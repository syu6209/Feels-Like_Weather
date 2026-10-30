package com.zozi.feels_likeweather.data.cache

import android.content.Context
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.remote.OpenMeteoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** 마지막으로 불러온 예보. 위젯이 앱 없이도 그릴 수 있도록 앱과 위젯이 함께 쓴다. */
data class CachedForecast(
    val forecast: WeatherForecast,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * 예보 원본 JSON 과 지역 정보를 앱 내부 파일에 보관한다.
 * 위젯 갱신 작업은 백그라운드 위치 권한 없이 마지막 좌표로 예보만 다시 받는다.
 */
class ForecastCache(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    suspend fun save(json: String, placeName: String, latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        val wrapper = JSONObject()
            .put("place", placeName)
            .put("lat", latitude)
            .put("lon", longitude)
            .put("fetchedAt", System.currentTimeMillis())
            .put("forecast", JSONObject(json))
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(wrapper.toString())
        tmp.renameTo(file)
    }

    suspend fun load(): CachedForecast? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            val o = JSONObject(file.readText())
            CachedForecast(
                forecast = OpenMeteoClient.parseForecast(o.getJSONObject("forecast"), o.optLong("fetchedAt")),
                placeName = o.optString("place"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
            )
        }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "forecast_cache.json"
    }
}
