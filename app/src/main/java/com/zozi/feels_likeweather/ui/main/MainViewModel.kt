package com.zozi.feels_likeweather.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zozi.feels_likeweather.data.local.StoredForecast
import com.zozi.feels_likeweather.data.local.WeatherStore
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
import com.zozi.feels_likeweather.domain.withAnswer
import com.zozi.feels_likeweather.widget.WidgetUpdater
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class WeatherState(
    /** 보여줄 날씨 없이 불러오는 중. */
    val loading: Boolean = true,
    /** 당겨서 새로고침 중. */
    val refreshing: Boolean = false,
    val forecast: WeatherForecast? = null,
    val placeName: String? = null,
    val usingCurrentLocation: Boolean = true,
    /** 마지막 갱신에 실패해 저장된 날씨를 보여주는 중. */
    val offline: Boolean = false,
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
    /** 이 시간대에 직접 응답한 구간 (없으면 null). */
    val answer: FeelsBand? = null,
    /** 응답 당시 예보였던 구간. */
    val predictedAtAnswer: FeelsBand? = null,
) {
    /** 화면에 보여줄 구간. 직접 응답한 시간대는 응답이 곧 정답이므로 응답 구간을 쓴다. */
    val displayBand: FeelsBand get() = answer ?: myBand

    /** 응답이 예보와 달라 "쌀쌀함 → 추움" 처럼 바뀐 구간을 보여줘야 하는지. */
    val correctedFrom: FeelsBand? get() = predictedAtAnswer?.takeIf { answer != null && it != answer }
}

/** 설문 응답이 예보와 달랐을 때 체감 보정 예보 카드에 잠깐(2초) 보여주는 반응. */
data class SurveyReaction(val slotKey: String, val from: FeelsBand, val to: FeelsBand, val phrase: BriefingPhrase)

data class FeelsUi(
    val now: LocalDateTime,
    val slots: List<SlotUi>,
    val selected: SlotUi,
    val briefing: BriefingPhrase,
    val myThresholds: FeelsThresholds,
    val answers: Map<String, FeelsBand>,
    /** 선택된 시간대에 대한 반응이 진행 중이면 브리핑 대신 보여준다. */
    val reaction: SurveyReaction? = null,
)

/** 현재 기준 12시간 전 ~ 48시간 후 시간대 박스와 각 시간대의 체감 데이터. */
fun buildSlots(
    forecast: WeatherForecast,
    now: LocalDateTime,
    mine: FeelsThresholds,
    records: List<SurveyRecord> = emptyList(),
): List<SlotUi> {
    val answered = records.filterNot { it.isDeleted }.associateBy { it.slotKey }
    fun slotUi(slot: TimeSlot, apparent: Double, temperature: Double, code: Int, isDay: Boolean) = SlotUi(
        slot = slot,
        apparent = apparent,
        temperature = temperature,
        weatherCode = code,
        isDay = isDay,
        myBand = mine.bandOf(apparent),
        defaultBand = FeelsThresholds.DEFAULT.bandOf(apparent),
        answer = answered[slot.key]?.reported,
        predictedAtAnswer = answered[slot.key]?.predicted,
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

/** 두 좌표 사이 거리(km, 하버사인). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = OpenMeteoClient()
    private val locationClient = LocationClient(app)
    private val placeResolver = PlaceNameResolver(app)
    private val prefs = AppPreferences(app)
    private val store = WeatherStore(app)

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    val userData: StateFlow<UserData> = prefs.userData
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserData())

    val myThresholds: StateFlow<FeelsThresholds> = userData
        .map { FeelsLearner.estimate(it.surveys, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeelsThresholds.DEFAULT)

    private val selectedSlotKey = MutableStateFlow<String?>(null)
    private val reaction = MutableStateFlow<SurveyReaction?>(null)
    private var reactionJob: Job? = null

    /** 노출된 브리핑을 저장 완료 전에도 고정해 두기 위한 메모리 캐시. */
    private val pinCache = mutableMapOf<FeelsBand, BriefingPin>()

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val feels: StateFlow<FeelsUi?> = combine(
        _weather, userData, myThresholds, combine(selectedSlotKey, reaction, ::Pair), minuteTicker,
    ) { weather, data, mine, (selectedKey, currentReaction), _ ->
        val forecast = weather.forecast ?: return@combine null
        buildFeelsUi(forecast, data, mine, selectedKey, currentReaction)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    private fun buildFeelsUi(
        forecast: WeatherForecast,
        data: UserData,
        mine: FeelsThresholds,
        selectedKey: String?,
        currentReaction: SurveyReaction?,
    ): FeelsUi? {
        val now = forecast.localNow()
        val slots = buildSlots(forecast, now, mine, data.surveys)
        if (slots.isEmpty()) return null
        val selected = slots.firstOrNull { it.slot.key == selectedKey } ?: slots.first { it.slot.isCurrent }

        val band = selected.displayBand
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
            answers = data.surveys.filterNot { it.isDeleted }.associate { it.slotKey to it.reported },
            reaction = currentReaction?.takeIf { it.slotKey == selected.slot.key },
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

    /** 당겨서 새로고침 · 다시 시도: 30분이 지나지 않았어도 새로 받는다. */
    fun refresh() = loadWeather(force = true, userInitiated = true)

    /**
     * 화면이 보이는 동안 1분마다 부른다. 받은 지 30분이 지났으면 새로 받고,
     * 저장된 예보를 보는 중이면 시각이 바뀐 만큼 현재 날씨를 시간별 예보로 바꾼다.
     */
    fun onTick() {
        if (!started || loadJob?.isActive == true) return
        val forecast = _weather.value.forecast ?: return
        if (System.currentTimeMillis() - forecast.fetchedAtMillis >= WeatherStore.REFRESH_INTERVAL_MILLIS) {
            loadWeather(quiet = true)
        } else {
            _weather.update { it.copy(forecast = forecast.currentAt(forecast.localNow())) }
        }
    }

    /**
     * 선택된 지역(없으면 현재 위치)의 날씨를 보여준다.
     * 1. 로컬 DB 에 저장된 날씨(5일 이내)를 바로 보여주고
     * 2. 받은 지 30분이 지났거나([force]) 현재 위치가 바뀌었으면 새로 받아 저장한다.
     * 새로 받지 못하면(오프라인) 저장된 날씨를 계속 보여준다.
     */
    private fun loadWeather(force: Boolean = false, userInitiated: Boolean = false, quiet: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val place = prefs.userData.first().selectedPlace
            val key = place?.id ?: WeatherStore.CURRENT_LOCATION_KEY
            val cached = store.load(key)
            if (cached != null) {
                show(cached, usingCurrentLocation = place == null)
                store.markViewed(key)
                WidgetUpdater.updateAll(getApplication())
            } else {
                _weather.update {
                    // 다른 지역으로 바꿨는데 저장된 날씨가 없으면 이전 지역 날씨를 지우고 불러온다.
                    val samePlace = it.usingCurrentLocation == (place == null) && (place == null || it.placeName == place.name)
                    if (samePlace) it.copy(loading = it.forecast == null, error = null)
                    else WeatherState(loading = true, placeName = place?.name, usingCurrentLocation = place == null)
                }
            }
            _weather.update { it.copy(refreshing = userInitiated) }

            try {
                var notice: String? = null
                val target: Target = when {
                    place != null -> Target(place.latitude, place.longitude, place.name)
                    else -> {
                        val location = locationClient.currentLocation()
                        when {
                            location != null -> Target(location.latitude, location.longitude, name = null)
                            // 위치를 못 찾으면 마지막으로 저장한 현재 위치를 쓴다.
                            cached != null -> Target(cached.latitude, cached.longitude, cached.placeName)
                            else -> {
                                notice = if (locationClient.hasPermission()) {
                                    "현재 위치를 찾지 못해 서울 날씨를 보여드려요."
                                } else {
                                    "위치 권한이 없어 서울 날씨를 보여드려요. 지역 선택에서 다른 지역을 고를 수 있어요."
                                }
                                Target(SEOUL.latitude, SEOUL.longitude, SEOUL.name)
                            }
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val moved = cached != null &&
                    distanceKm(cached.latitude, cached.longitude, target.latitude, target.longitude) > MOVED_KM
                if (cached != null && !force && !moved && !cached.isStale(now)) {
                    _weather.update { it.copy(refreshing = false, notice = notice) }
                    return@launch
                }

                val name = target.name
                    ?: placeResolver.displayName(target.latitude, target.longitude)
                    ?: cached?.placeName?.takeIf { !moved }
                    ?: "현재 위치"
                val forecast = api.fetchForecast(target.latitude, target.longitude)
                store.save(key, name, target.latitude, target.longitude, forecast)
                store.markViewed(key)
                WidgetUpdater.updateAll(getApplication())
                show(
                    StoredForecast(key, name, target.latitude, target.longitude, forecast),
                    usingCurrentLocation = place == null,
                    notice = notice,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val showing = _weather.value.forecast != null
                _weather.update {
                    if (showing) {
                        it.copy(loading = false, refreshing = false, offline = true)
                    } else {
                        it.copy(loading = false, refreshing = false, error = "날씨 정보를 불러오지 못했어요. 네트워크 상태를 확인해주세요.")
                    }
                }
                if (showing && !quiet) _messages.send("인터넷에 연결할 수 없어 저장된 날씨를 보여드려요.")
            }
        }
    }

    private fun show(stored: StoredForecast, usingCurrentLocation: Boolean, notice: String? = null) {
        val forecast = stored.forecast
        _weather.update {
            it.copy(
                loading = false,
                forecast = forecast.currentAt(forecast.localNow()),
                placeName = stored.placeName,
                usingCurrentLocation = usingCurrentLocation,
                offline = false,
                notice = notice,
                error = null,
            )
        }
    }

    private data class Target(val latitude: Double, val longitude: Double, val name: String?)

    fun selectPlace(place: Place?) {
        viewModelScope.launch {
            if (place != null) {
                val saved = userData.value.savedPlaces
                if (saved.none { it.id == place.id }) prefs.saveSavedPlaces(saved + place)
            }
            prefs.saveSelectedPlace(place)
            selectedSlotKey.value = null
            loadWeather(quiet = true)
        }
    }

    fun removeSavedPlace(place: Place) {
        viewModelScope.launch {
            prefs.saveSavedPlaces(userData.value.savedPlaces.filterNot { it.id == place.id })
            store.delete(place.id)
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
            val now = System.currentTimeMillis()
            val before = myThresholds.value
            val records = userData.value.surveys.withAnswer(slot.slot.key, slot.apparent, reported, slot.myBand, now)

            // 카드에 보이던 구간과 응답이 다르면 반응 문구 → 새 구간 브리핑 순서로 전환한다.
            val shown = slot.displayBand
            if (reported != shown) {
                reactionJob?.cancel()
                reaction.value = SurveyReaction(
                    slotKey = slot.slot.key,
                    from = shown,
                    to = reported,
                    phrase = Briefings.reaction(reported, isNow = slot.slot.isCurrent),
                )
                reactionJob = viewModelScope.launch {
                    delay(REACTION_MILLIS)
                    reaction.value = null
                }
            }

            prefs.saveSurveys(records)
            val after = FeelsLearner.estimate(records, LocalDate.now())
            _messages.send(describeChange(before, after) ?: "'${reported.surveyLabel}' 응답을 기록했어요.")
        }
    }

    /** 바뀐 분기점 설명. 바뀐 게 없으면 null. */
    private fun describeChange(before: FeelsThresholds, after: FeelsThresholds): String? {
        val changes = FeelsBand.entries.drop(1).mapNotNull { band ->
            val b = before.lowerBoundOf(band)!!
            val a = after.lowerBoundOf(band)!!
            if (b == a) null else "${band.label} ${"%.1f".format(b)}°→${"%.1f".format(a)}°"
        }
        if (changes.isEmpty()) return null
        return "내 체감 기준을 조정했어요 · " + changes.take(2).joinToString(", ") +
            if (changes.size > 2) " 외 ${changes.size - 2}개" else ""
    }

    /** 설문 기록을 삭제한다(7일 동안 되돌릴 수 있음). 바뀐 기준 설명을 돌려준다. */
    suspend fun deleteSurvey(slotKey: String): String {
        val now = System.currentTimeMillis()
        val records = userData.value.surveys.map {
            if (it.slotKey == slotKey && !it.isDeleted) it.copy(deletedAtMillis = now) else it
        }
        return saveAndDescribe(records, "기록을 삭제했어요.")
    }

    /** 삭제한 기록을 원래 응답 시각 그대로 되살린다. 같은 시간대에 새 응답이 있으면 새 응답을 유지한다. */
    suspend fun restoreSurvey(slotKey: String): String {
        val current = userData.value.surveys
        if (current.any { it.slotKey == slotKey && !it.isDeleted }) return "같은 시간대에 새 응답이 있어 되돌리지 않았어요."
        val records = current.map { if (it.slotKey == slotKey) it.copy(deletedAtMillis = null) else it }
        return saveAndDescribe(records, "기록을 되돌렸어요.")
    }

    private suspend fun saveAndDescribe(records: List<SurveyRecord>, done: String): String {
        val today = LocalDate.now()
        val before = FeelsLearner.estimate(userData.value.surveys, today)
        prefs.saveSurveys(records)
        val after = FeelsLearner.estimate(records, today)
        return describeChange(before, after)?.let { "$done $it" } ?: done
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
            val isNew = list.none { it.id == profile.id }
            val updated = if (isNew) list + profile else list.map { if (it.id == profile.id) profile else it }
            prefs.saveProfiles(updated)
            // 새로 만든 프로필은 바로 옷차림 추천에서 보이도록 선택한다.
            if (isNew) prefs.saveSelectedProfile(profile.id)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            prefs.saveProfiles(userData.value.profiles.filterNot { it.id == id && !it.isMine })
            if (userData.value.selectedProfileId == id) prefs.saveSelectedProfile(Profile.MINE_ID)
        }
    }

    /** 옷차림 추천에서 고른 프로필을 기억한다. */
    fun selectProfile(id: String) {
        viewModelScope.launch { prefs.saveSelectedProfile(id) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.saveThemeMode(mode) }
    }

    // endregion

    init {
        // 삭제 후 7일이 지난 기록은 앱을 열 때 정리한다.
        viewModelScope.launch {
            val surveys = prefs.userData.first().surveys
            if (FeelsLearner.purge(surveys, System.currentTimeMillis()).size != surveys.size) {
                prefs.saveSurveys(surveys)
            }
        }
        // 앱에서 설문·프로필이 바뀌면 위젯에도 반영한다.
        viewModelScope.launch {
            prefs.userData
                .map { it.surveys to it.profiles }
                .distinctUntilChanged()
                .drop(1)
                .debounce(500)
                .collect { WidgetUpdater.updateAll(getApplication()) }
        }
    }

    companion object {
        const val REACTION_MILLIS = 2_000L

        /** 현재 위치가 이만큼 바뀌면 30분이 지나지 않았어도 새로 받는다. */
        const val MOVED_KM = 3.0
        val SEOUL = Place("서울특별시", "대한민국", 37.5665, 126.9780)
    }
}
