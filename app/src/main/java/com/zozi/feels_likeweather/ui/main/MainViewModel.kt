package com.zozi.feels_likeweather.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zozi.feels_likeweather.data.location.LocationClient
import com.zozi.feels_likeweather.data.location.PlaceNameResolver
import com.zozi.feels_likeweather.data.model.Place
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.prefs.AppPreferences
import com.zozi.feels_likeweather.data.prefs.ThemeMode
import com.zozi.feels_likeweather.data.prefs.UserData
import com.zozi.feels_likeweather.data.remote.OpenMeteoClient
import com.zozi.feels_likeweather.domain.BriefingPhrase
import com.zozi.feels_likeweather.domain.BriefingPin
import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.domain.SurveyRecord
import com.zozi.feels_likeweather.domain.TimeSlot
import com.zozi.feels_likeweather.domain.TimeSlots
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class WeatherState(
    val loading: Boolean = true,
    val forecast: WeatherForecast? = null,
    val placeName: String? = null,
    val usingCurrentLocation: Boolean = true,
    val notice: String? = null,
    val error: String? = null,
)

/** 체감 예보 카드의 시간대 박스 하나에 해당하는 데이터. */
data class SlotUi(
    val slot: TimeSlot,
    val apparent: Double,
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val myBand: FeelsBand,
    val defaultBand: FeelsBand,
)

data class FeelsUi(
    val now: LocalDateTime,
    val slots: List<SlotUi>,
    val selected: SlotUi,
    val briefing: BriefingPhrase,
    val myThresholds: FeelsThresholds,
    val answers: Map<String, FeelsBand>,
)

/** 현재 기준 12시간 전 ~ 48시간 후 시간대 박스와 각 시간대의 체감 데이터. */
fun buildSlots(forecast: WeatherForecast, now: LocalDateTime, mine: FeelsThresholds): List<SlotUi> {
    fun slotUi(slot: TimeSlot, apparent: Double, temperature: Double, code: Int, isDay: Boolean) = SlotUi(
        slot = slot,
        apparent = apparent,
        temperature = temperature,
        weatherCode = code,
        isDay = isDay,
        myBand = mine.bandOf(apparent),
        defaultBand = FeelsThresholds.DEFAULT.bandOf(apparent),
    )

    return TimeSlots.build(now).mapNotNull { slot ->
        if (slot.isCurrent) {
            val c = forecast.current
            slotUi(slot, c.apparentTemperature, c.temperature, c.weatherCode, c.isDay)
        } else {
            forecast.hourlyAt(slot.hour)?.let { h ->
                slotUi(slot, h.apparentTemperature, h.temperature, h.weatherCode, h.isDay)
            }
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = OpenMeteoClient()
    private val locationClient = LocationClient(app)
    private val placeResolver = PlaceNameResolver(app)
    private val prefs = AppPreferences(app)

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    val userData: StateFlow<UserData> = prefs.userData
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserData())

    val myThresholds: StateFlow<FeelsThresholds> = userData
        .map { FeelsLearner.replay(it.surveys) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeelsThresholds.DEFAULT)

    private val selectedSlotKey = MutableStateFlow<String?>(null)

    /** 노출된 브리핑을 저장 완료 전에도 고정해 두기 위한 메모리 캐시. */
    private val pinCache = mutableMapOf<FeelsBand, BriefingPin>()

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val feels: StateFlow<FeelsUi?> = combine(
        _weather, userData, myThresholds, selectedSlotKey, minuteTicker,
    ) { weather, data, mine, selectedKey, _ ->
        val forecast = weather.forecast ?: return@combine null
        buildFeelsUi(forecast, data, mine, selectedKey)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    private fun buildFeelsUi(
        forecast: WeatherForecast,
        data: UserData,
        mine: FeelsThresholds,
        selectedKey: String?,
    ): FeelsUi? {
        val now = forecast.localNow()
        val slots = buildSlots(forecast, now, mine)
        if (slots.isEmpty()) return null
        val selected = slots.firstOrNull { it.slot.key == selectedKey } ?: slots.first { it.slot.isCurrent }

        val band = selected.myBand
        val pin = pinCache[band] ?: data.briefingPins[band]
        val (phrase, newPin) = Briefings.pick(band, pin, System.currentTimeMillis())
        if (newPin != pin) {
            pinCache[band] = newPin
            viewModelScope.launch { prefs.saveBriefingPins(userData.value.briefingPins + pinCache) }
        }

        return FeelsUi(
            now = now,
            slots = slots,
            selected = selected,
            briefing = phrase,
            myThresholds = mine,
            answers = data.surveys.associate { it.slotKey to it.reported },
        )
    }

    // region 날씨 / 위치

    private var started = false

    /** 첫 실행 시 위치 권한을 요청해야 하는지 (선택된 지역이 있으면 필요 없음). */
    suspend fun needsLocationPermission(): Boolean =
        !started && prefs.userData.first().selectedPlace == null && !locationClient.hasPermission()

    fun hasLocationPermission(): Boolean = locationClient.hasPermission()

    /** 화면 진입 시 한 번만 날씨를 불러온다. */
    fun startIfNeeded() {
        if (started) return
        started = true
        loadWeather()
    }

    /** 현재 선택된 지역(없으면 현재 위치)의 날씨를 불러온다. */
    fun loadWeather() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _weather.update { it.copy(loading = true, error = null) }
            val place = prefs.userData.first().selectedPlace
            try {
                var notice: String? = null
                val (lat, lon, name) = if (place != null) {
                    Triple(place.latitude, place.longitude, place.name)
                } else {
                    val location = locationClient.currentLocation()
                    if (location == null) {
                        notice = if (locationClient.hasPermission()) {
                            "현재 위치를 찾지 못해 서울 날씨를 보여드려요."
                        } else {
                            "위치 권한이 없어 서울 날씨를 보여드려요. 지역 선택에서 다른 지역을 고를 수 있어요."
                        }
                        Triple(SEOUL.latitude, SEOUL.longitude, SEOUL.name)
                    } else {
                        val resolved = placeResolver.displayName(location.latitude, location.longitude)
                        Triple(location.latitude, location.longitude, resolved ?: "현재 위치")
                    }
                }
                val forecast = api.fetchForecast(lat, lon)
                _weather.value = WeatherState(
                    loading = false,
                    forecast = forecast,
                    placeName = name,
                    usingCurrentLocation = place == null,
                    notice = notice,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _weather.update {
                    it.copy(loading = false, error = "날씨 정보를 불러오지 못했어요. 네트워크 상태를 확인해주세요.")
                }
            }
        }
    }

    fun selectPlace(place: Place?) {
        viewModelScope.launch {
            if (place != null) {
                val saved = userData.value.savedPlaces
                if (saved.none { it.id == place.id }) prefs.saveSavedPlaces(saved + place)
            }
            prefs.saveSelectedPlace(place)
            selectedSlotKey.value = null
            loadWeather()
        }
    }

    fun removeSavedPlace(place: Place) {
        viewModelScope.launch {
            prefs.saveSavedPlaces(userData.value.savedPlaces.filterNot { it.id == place.id })
            if (userData.value.selectedPlace?.id == place.id) selectPlace(null)
        }
    }

    suspend fun searchPlaces(query: String): List<Place> {
        val local = runCatching { placeResolver.search(query) }.getOrDefault(emptyList())
        if (local.isNotEmpty()) return local
        return runCatching { api.searchPlaces(query) }.getOrDefault(emptyList())
    }

    // endregion

    // region 체감 예보 / 설문

    fun selectSlot(key: String) {
        selectedSlotKey.value = key
    }

    fun answerSurvey(slot: SlotUi, reported: FeelsBand) {
        viewModelScope.launch {
            val before = myThresholds.value
            val records = userData.value.surveys.filterNot { it.slotKey == slot.slot.key } +
                SurveyRecord(slot.slot.key, slot.apparent, reported, System.currentTimeMillis())
            prefs.saveSurveys(records)
            val after = FeelsLearner.replay(records)
            _messages.send(describeChange(before, after, reported))
        }
    }

    private fun describeChange(before: FeelsThresholds, after: FeelsThresholds, reported: FeelsBand): String {
        val changes = FeelsBand.entries.drop(1).mapNotNull { band ->
            val b = before.lowerBoundOf(band)!!
            val a = after.lowerBoundOf(band)!!
            if (b == a) null else "${band.label} ${"%.1f".format(b)}°→${"%.1f".format(a)}°"
        }
        return if (changes.isEmpty()) {
            "'${reported.surveyLabel}' 응답을 기록했어요."
        } else {
            "내 체감 기준을 조정했어요 · " + changes.take(2).joinToString(", ")
        }
    }

    fun resetSurveys() {
        viewModelScope.launch {
            prefs.saveSurveys(emptyList())
            _messages.send("내 체감 구간을 기본값으로 되돌렸어요.")
        }
    }

    // endregion

    // region 프로필 / 설정

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            val list = userData.value.profiles
            val updated = if (list.any { it.id == profile.id }) {
                list.map { if (it.id == profile.id) profile else it }
            } else {
                list + profile
            }
            prefs.saveProfiles(updated)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            prefs.saveProfiles(userData.value.profiles.filterNot { it.id == id && !it.isMine })
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.saveThemeMode(mode) }
    }

    // endregion

    companion object {
        val SEOUL = Place("서울특별시", "대한민국", 37.5665, 126.9780)
    }
}
