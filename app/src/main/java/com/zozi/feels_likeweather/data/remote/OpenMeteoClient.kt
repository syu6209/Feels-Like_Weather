package com.zozi.feels_likeweather.data.remote

import com.zozi.feels_likeweather.data.model.CurrentWeather
import com.zozi.feels_likeweather.data.model.DailyWeather
import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.data.model.Place
import com.zozi.feels_likeweather.data.model.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Open-Meteo 무료 API 클라이언트 (API 키 불필요, 비상업적 용도 무료, CC BY 4.0).
 * - Forecast: https://open-meteo.com/en/docs
 * - Geocoding: https://open-meteo.com/en/docs/geocoding-api
 */
class OpenMeteoClient {

    suspend fun fetchForecast(latitude: Double, longitude: Double): WeatherForecast =
        parseForecast(JSONObject(fetchForecastJson(latitude, longitude)), System.currentTimeMillis())

    /** 예보 원본 JSON. 위젯용 캐시에 그대로 저장한다. */
    suspend fun fetchForecastJson(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast")
                append("?latitude=").append(latitude)
                append("&longitude=").append(longitude)
                append("&current=").append(CURRENT_FIELDS)
                append("&hourly=").append(HOURLY_FIELDS)
                append("&daily=").append(DAILY_FIELDS)
                append("&timezone=auto&past_days=1&forecast_days=10&wind_speed_unit=ms")
            }
            get(url)
        }

    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=15&language=ko&format=json")
        )
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            Place(
                name = o.getString("name"),
                region = listOfNotNull(
                    o.optStringOrNull("admin1"),
                    o.optStringOrNull("country"),
                ).distinct().joinToString(", "),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
            )
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("Open-Meteo HTTP $code $body")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,precipitation,is_day"
        private const val HOURLY_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,precipitation," +
                "weather_code,wind_speed_10m,uv_index,is_day"
        private const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
                "precipitation_probability_max,precipitation_sum,uv_index_max,sunrise,sunset"

        fun parseForecast(json: JSONObject, fetchedAtMillis: Long): WeatherForecast {
            val c = json.getJSONObject("current")
            val current = CurrentWeather(
                time = LocalDateTime.parse(c.getString("time")),
                temperature = c.getDouble("temperature_2m"),
                apparentTemperature = c.getDouble("apparent_temperature"),
                humidity = c.optDouble("relative_humidity_2m", 0.0).roundToInt(),
                weatherCode = c.optInt("weather_code", 0),
                windSpeed = c.optDouble("wind_speed_10m", 0.0),
                precipitation = c.optDouble("precipitation", 0.0),
                isDay = c.optInt("is_day", 1) == 1,
            )

            val h = json.getJSONObject("hourly")
            val hTime = h.getJSONArray("time")
            val hourly = (0 until hTime.length()).mapNotNull { i ->
                // 아직 계산되지 않은 시간대는 null 로 내려올 수 있다.
                val temp = h.getJSONArray("temperature_2m").doubleOrNull(i) ?: return@mapNotNull null
                val apparent = h.getJSONArray("apparent_temperature").doubleOrNull(i) ?: return@mapNotNull null
                HourlyWeather(
                    time = LocalDateTime.parse(hTime.getString(i)),
                    temperature = temp,
                    apparentTemperature = apparent,
                    humidity = h.optJSONArray("relative_humidity_2m")?.doubleOrNull(i)?.roundToInt() ?: 0,
                    precipitationProbability = h.optJSONArray("precipitation_probability")?.doubleOrNull(i)
                        ?.roundToInt() ?: 0,
                    precipitation = h.optJSONArray("precipitation")?.doubleOrNull(i) ?: 0.0,
                    weatherCode = h.optJSONArray("weather_code")?.doubleOrNull(i)?.toInt() ?: 0,
                    windSpeed = h.optJSONArray("wind_speed_10m")?.doubleOrNull(i) ?: 0.0,
                    uvIndex = h.optJSONArray("uv_index")?.doubleOrNull(i) ?: 0.0,
                    isDay = (h.optJSONArray("is_day")?.doubleOrNull(i) ?: 1.0) >= 1.0,
                )
            }

            val d = json.getJSONObject("daily")
            val dTime = d.getJSONArray("time")
            val daily = (0 until dTime.length()).mapNotNull { i ->
                val max = d.getJSONArray("temperature_2m_max").doubleOrNull(i) ?: return@mapNotNull null
                val min = d.getJSONArray("temperature_2m_min").doubleOrNull(i) ?: return@mapNotNull null
                DailyWeather(
                    date = LocalDate.parse(dTime.getString(i)),
                    weatherCode = d.optJSONArray("weather_code")?.doubleOrNull(i)?.toInt() ?: 0,
                    temperatureMax = max,
                    temperatureMin = min,
                    apparentMax = d.optJSONArray("apparent_temperature_max")?.doubleOrNull(i) ?: max,
                    apparentMin = d.optJSONArray("apparent_temperature_min")?.doubleOrNull(i) ?: min,
                    precipitationProbabilityMax = d.optJSONArray("precipitation_probability_max")
                        ?.doubleOrNull(i)?.roundToInt() ?: 0,
                    precipitationSum = d.optJSONArray("precipitation_sum")?.doubleOrNull(i) ?: 0.0,
                    uvIndexMax = d.optJSONArray("uv_index_max")?.doubleOrNull(i) ?: 0.0,
                    sunrise = d.optJSONArray("sunrise")?.optStringOrNull(i)?.let(LocalDateTime::parse),
                    sunset = d.optJSONArray("sunset")?.optStringOrNull(i)?.let(LocalDateTime::parse),
                )
            }

            return WeatherForecast(
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                utcOffsetSeconds = json.optInt("utc_offset_seconds", 0),
                current = current,
                hourly = hourly,
                daily = daily,
                fetchedAtMillis = fetchedAtMillis,
            )
        }

        private fun JSONArray.doubleOrNull(i: Int): Double? =
            if (i >= length() || isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }

        private fun JSONArray.optStringOrNull(i: Int): String? =
            if (i >= length() || isNull(i)) null else optString(i)

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }
    }
}
