package com.zozi.feels_likeweather.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zozi.feels_likeweather.data.model.Place
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.BriefingPin
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.domain.SurveyRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val label: String) { SYSTEM("시스템 설정"), LIGHT("라이트"), DARK("다크") }

/** 앱 로컬 저장소에 보관하는 사용자 데이터 전체. */
data class UserData(
    val profiles: List<Profile> = Profile.defaults,
    val surveys: List<SurveyRecord> = emptyList(),
    val briefingPins: Map<FeelsBand, BriefingPin> = emptyMap(),
    val savedPlaces: List<Place> = emptyList(),
    /** null 이면 현재 위치 사용. */
    val selectedPlace: Place? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feels_like")

class AppPreferences(private val context: Context) {

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles")
        val SURVEYS = stringPreferencesKey("surveys")
        val BRIEFING_PINS = stringPreferencesKey("briefing_pins")
        val SAVED_PLACES = stringPreferencesKey("saved_places")
        val SELECTED_PLACE = stringPreferencesKey("selected_place")
        val THEME = stringPreferencesKey("theme")
    }

    val userData: Flow<UserData> = context.dataStore.data.map { p ->
        UserData(
            profiles = p[Keys.PROFILES]?.let(Codec::decodeProfiles)?.let(::ensureMine) ?: Profile.defaults,
            surveys = p[Keys.SURVEYS]?.let(Codec::decodeSurveys).orEmpty(),
            briefingPins = p[Keys.BRIEFING_PINS]?.let(Codec::decodePins).orEmpty(),
            savedPlaces = p[Keys.SAVED_PLACES]?.let(Codec::decodePlaces).orEmpty(),
            selectedPlace = p[Keys.SELECTED_PLACE]?.let { runCatching { Codec.decodePlace(JSONObject(it)) }.getOrNull() },
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun saveProfiles(profiles: List<Profile>) = context.dataStore.edit {
        it[Keys.PROFILES] = Codec.encodeProfiles(ensureMine(profiles))
    }

    suspend fun saveSurveys(records: List<SurveyRecord>) = context.dataStore.edit {
        it[Keys.SURVEYS] = Codec.encodeSurveys(records.takeLast(MAX_SURVEYS))
    }

    suspend fun saveBriefingPins(pins: Map<FeelsBand, BriefingPin>) = context.dataStore.edit {
        it[Keys.BRIEFING_PINS] = Codec.encodePins(pins)
    }

    suspend fun saveSavedPlaces(places: List<Place>) = context.dataStore.edit {
        it[Keys.SAVED_PLACES] = Codec.encodePlaces(places)
    }

    suspend fun saveSelectedPlace(place: Place?) = context.dataStore.edit {
        if (place == null) it.remove(Keys.SELECTED_PLACE) else it[Keys.SELECTED_PLACE] = Codec.encodePlace(place).toString()
    }

    suspend fun saveThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    private fun ensureMine(profiles: List<Profile>): List<Profile> =
        if (profiles.any { it.isMine }) profiles else listOf(Profile.defaults.first()) + profiles

    companion object {
        const val MAX_SURVEYS = 500
    }
}

/** org.json 기반 직렬화. */
internal object Codec {

    fun encodeProfiles(list: List<Profile>): String = JSONArray().apply {
        list.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("age", p.ageGroup.name)
                    .put("start", p.activityStartHour)
                    .put("end", p.activityEndHour)
                    .put("mine", p.isMine)
            )
        }
    }.toString()

    fun decodeProfiles(s: String): List<Profile> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Profile(
                id = o.getString("id"),
                name = o.getString("name"),
                ageGroup = runCatching { AgeGroup.valueOf(o.getString("age")) }.getOrDefault(AgeGroup.ADULT),
                activityStartHour = o.optInt("start", 9),
                activityEndHour = o.optInt("end", 18),
                isMine = o.optBoolean("mine", false),
            )
        }
    }.getOrDefault(Profile.defaults)

    fun encodeSurveys(list: List<SurveyRecord>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject()
                    .put("slot", r.slotKey)
                    .put("t", r.apparentTemperature)
                    .put("band", r.reported.name)
                    .put("at", r.answeredAtMillis)
            )
        }
    }.toString()

    fun decodeSurveys(s: String): List<SurveyRecord> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val band = runCatching { FeelsBand.valueOf(o.getString("band")) }.getOrNull() ?: return@mapNotNull null
            SurveyRecord(o.getString("slot"), o.getDouble("t"), band, o.getLong("at"))
        }
    }.getOrDefault(emptyList())

    fun encodePins(map: Map<FeelsBand, BriefingPin>): String = JSONObject().apply {
        map.forEach { (band, pin) -> put(band.name, JSONObject().put("i", pin.index).put("at", pin.pinnedAtMillis)) }
    }.toString()

    fun decodePins(s: String): Map<FeelsBand, BriefingPin> = runCatching {
        val o = JSONObject(s)
        o.keys().asSequence().mapNotNull { key ->
            val band = runCatching { FeelsBand.valueOf(key) }.getOrNull() ?: return@mapNotNull null
            val v = o.getJSONObject(key)
            band to BriefingPin(v.getInt("i"), v.getLong("at"))
        }.toMap()
    }.getOrDefault(emptyMap())

    fun encodePlace(p: Place): JSONObject = JSONObject()
        .put("name", p.name)
        .put("region", p.region)
        .put("lat", p.latitude)
        .put("lon", p.longitude)

    fun decodePlace(o: JSONObject): Place =
        Place(o.getString("name"), o.optString("region"), o.getDouble("lat"), o.getDouble("lon"))

    fun encodePlaces(list: List<Place>): String = JSONArray().apply { list.forEach { put(encodePlace(it)) } }.toString()

    fun decodePlaces(s: String): List<Place> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { decodePlace(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
