package com.zozi.feels_likeweather.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.zozi.feels_likeweather.data.model.CurrentWeather
import com.zozi.feels_likeweather.data.model.DailyWeather
import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.data.model.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** DB 에 저장된 한 지역의 날씨. */
data class StoredForecast(
    val key: String,
    val placeName: String,
    /** 요청한 좌표 (예보 격자 좌표가 아니라 위치·지역 좌표). */
    val latitude: Double,
    val longitude: Double,
    val forecast: WeatherForecast,
) {
    val fetchedAtMillis: Long get() = forecast.fetchedAtMillis

    /** 최신 정보로 다시 받을 때가 됐는지 (30분). */
    fun isStale(nowMillis: Long, interval: Long = WeatherStore.REFRESH_INTERVAL_MILLIS): Boolean =
        nowMillis - fetchedAtMillis >= interval
}

/**
 * 지역별 날씨 캐시. 화면을 바로 그리고, 오프라인에서도 저장된 예보를 보여주기 위해 쓴다.
 * - 받은 지 5일이 지난 데이터는 만료되어 보여주지 않고 지운다.
 * - 같은 지역을 다시 받으면 통째로 바꾼다.
 */
class WeatherStore(context: Context) {

    private val db = WeatherDatabase.get(context)

    suspend fun save(
        key: String,
        placeName: String,
        latitude: Double,
        longitude: Double,
        forecast: WeatherForecast,
    ) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val c = forecast.current
            val viewedAt = w.rawQuery("SELECT viewed_at FROM location WHERE key = ?", arrayOf(key)).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            // 지역 행을 지우면 시간별·일별 행도 함께 지워진다(ON DELETE CASCADE).
            w.delete("location", "key = ?", arrayOf(key))
            w.insertOrThrow("location", null, ContentValues().apply {
                put("key", key)
                put("name", placeName)
                put("latitude", latitude)
                put("longitude", longitude)
                put("utc_offset", forecast.utcOffsetSeconds)
                put("fetched_at", forecast.fetchedAtMillis)
                put("viewed_at", viewedAt)
                put("now_time", c.time.toString())
                put("now_temperature", c.temperature)
                put("now_apparent", c.apparentTemperature)
                put("now_humidity", c.humidity)
                put("now_code", c.weatherCode)
                put("now_wind", c.windSpeed)
                put("now_precipitation", c.precipitation)
                put("now_is_day", if (c.isDay) 1 else 0)
            })
            forecast.hourly.forEach { h ->
                w.insertOrThrow("hourly", null, ContentValues().apply {
                    put("location_key", key)
                    put("time", h.time.toString())
                    put("temperature", h.temperature)
                    put("apparent", h.apparentTemperature)
                    put("humidity", h.humidity)
                    put("precipitation_probability", h.precipitationProbability)
                    put("precipitation", h.precipitation)
                    put("code", h.weatherCode)
                    put("wind", h.windSpeed)
                    put("uv", h.uvIndex)
                    put("is_day", if (h.isDay) 1 else 0)
                })
            }
            forecast.daily.forEach { d ->
                w.insertOrThrow("daily", null, ContentValues().apply {
                    put("location_key", key)
                    put("date", d.date.toString())
                    put("code", d.weatherCode)
                    put("temperature_max", d.temperatureMax)
                    put("temperature_min", d.temperatureMin)
                    put("apparent_max", d.apparentMax)
                    put("apparent_min", d.apparentMin)
                    put("precipitation_probability_max", d.precipitationProbabilityMax)
                    put("precipitation_sum", d.precipitationSum)
                    put("uv_max", d.uvIndexMax)
                    put("sunrise", d.sunrise?.toString())
                    put("sunset", d.sunset?.toString())
                })
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** 저장된 날씨. 없거나 만료(5일)됐으면 null. */
    suspend fun load(key: String, nowMillis: Long = System.currentTimeMillis()): StoredForecast? =
        withContext(Dispatchers.IO) {
            purgeExpired(nowMillis)
            val r = db.readableDatabase
            r.rawQuery("SELECT * FROM location WHERE key = ?", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) readForecast(cursor) else null
            }
        }

    /** 마지막으로 화면에 보여준 지역의 날씨 (위젯·백그라운드 갱신용). */
    suspend fun lastViewed(nowMillis: Long = System.currentTimeMillis()): StoredForecast? =
        withContext(Dispatchers.IO) {
            purgeExpired(nowMillis)
            db.readableDatabase.rawQuery(
                "SELECT * FROM location ORDER BY viewed_at DESC, fetched_at DESC LIMIT 1", null,
            ).use { cursor -> if (cursor.moveToFirst()) readForecast(cursor) else null }
        }

    suspend fun markViewed(key: String, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("location", ContentValues().apply { put("viewed_at", nowMillis) }, "key = ?", arrayOf(key))
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("location", "key = ?", arrayOf(key))
    }

    /** 받은 지 5일이 지난 날씨를 지운다. */
    fun purgeExpired(nowMillis: Long) {
        db.writableDatabase.delete("location", "fetched_at < ?", arrayOf((nowMillis - EXPIRE_MILLIS).toString()))
    }

    private fun readForecast(location: Cursor): StoredForecast {
        val key = location.string("key")
        val r = db.readableDatabase
        val hourly = r.rawQuery("SELECT * FROM hourly WHERE location_key = ? ORDER BY time", arrayOf(key)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        HourlyWeather(
                            time = LocalDateTime.parse(c.string("time")),
                            temperature = c.double("temperature"),
                            apparentTemperature = c.double("apparent"),
                            humidity = c.int("humidity"),
                            precipitationProbability = c.int("precipitation_probability"),
                            precipitation = c.double("precipitation"),
                            weatherCode = c.int("code"),
                            windSpeed = c.double("wind"),
                            uvIndex = c.double("uv"),
                            isDay = c.int("is_day") == 1,
                        )
                    )
                }
            }
        }
        val daily = r.rawQuery("SELECT * FROM daily WHERE location_key = ? ORDER BY date", arrayOf(key)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DailyWeather(
                            date = LocalDate.parse(c.string("date")),
                            weatherCode = c.int("code"),
                            temperatureMax = c.double("temperature_max"),
                            temperatureMin = c.double("temperature_min"),
                            apparentMax = c.double("apparent_max"),
                            apparentMin = c.double("apparent_min"),
                            precipitationProbabilityMax = c.int("precipitation_probability_max"),
                            precipitationSum = c.double("precipitation_sum"),
                            uvIndexMax = c.double("uv_max"),
                            sunrise = c.stringOrNull("sunrise")?.let(LocalDateTime::parse),
                            sunset = c.stringOrNull("sunset")?.let(LocalDateTime::parse),
                        )
                    )
                }
            }
        }
        val forecast = WeatherForecast(
            latitude = location.double("latitude"),
            longitude = location.double("longitude"),
            utcOffsetSeconds = location.int("utc_offset"),
            current = CurrentWeather(
                time = LocalDateTime.parse(location.string("now_time")),
                temperature = location.double("now_temperature"),
                apparentTemperature = location.double("now_apparent"),
                humidity = location.int("now_humidity"),
                weatherCode = location.int("now_code"),
                windSpeed = location.double("now_wind"),
                precipitation = location.double("now_precipitation"),
                isDay = location.int("now_is_day") == 1,
            ),
            hourly = hourly,
            daily = daily,
            fetchedAtMillis = location.long("fetched_at"),
        )
        return StoredForecast(
            key = key,
            placeName = location.string("name"),
            latitude = forecast.latitude,
            longitude = forecast.longitude,
            forecast = forecast,
        )
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.stringOrNull(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    companion object {
        /** 현재 위치 날씨의 키. 저장한 지역은 [com.zozi.feels_likeweather.data.model.Place.id] 를 쓴다. */
        const val CURRENT_LOCATION_KEY = "current"

        /** 최신 정보로 다시 받는 간격. */
        const val REFRESH_INTERVAL_MILLIS = 30 * 60 * 1000L

        /** 캐시 만료. */
        const val EXPIRE_MILLIS = 5 * 24 * 60 * 60 * 1000L

        /** 이 시간 이상 지난 날씨에는 기준 시각을 표시한다. */
        const val SHOW_BASIS_AFTER_MILLIS = 6 * 60 * 60 * 1000L
    }
}
