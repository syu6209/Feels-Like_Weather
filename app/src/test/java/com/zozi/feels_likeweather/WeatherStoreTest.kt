package com.zozi.feels_likeweather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zozi.feels_likeweather.data.local.WeatherStore
import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.remote.OpenMeteoClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = WeatherStore(context)
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val t0 = 1_800_000_000_000L

    private fun forecast(fetchedAt: Long): WeatherForecast = OpenMeteoClient.parseForecast(
        JSONObject(javaClass.classLoader!!.getResource("forecast_seoul.json").readText()),
        fetchedAt,
    )

    /** SQLite 는 -0.0 을 0.0 으로 저장하므로 비교 전에 맞춘다. */
    private fun List<HourlyWeather>.normalized() =
        map { it.copy(temperature = it.temperature + 0.0, apparentTemperature = it.apparentTemperature + 0.0) }

    @Test
    fun savesAndLoadsWholeForecast() = runBlocking {
        val original = forecast(t0)
        store.save("37.567,126.978", "서울특별시", 37.5665, 126.978, original)

        val loaded = store.load("37.567,126.978", nowMillis = t0 + hour)
        assertNotNull(loaded)
        loaded!!
        assertEquals("서울특별시", loaded.placeName)
        assertEquals(37.5665, loaded.latitude, 1e-9)
        assertEquals(original.current, loaded.forecast.current)
        assertEquals(original.hourly.normalized(), loaded.forecast.hourly.normalized())
        assertEquals(original.daily, loaded.forecast.daily)
        assertEquals(t0, loaded.fetchedAtMillis)
    }

    @Test
    fun refreshesEvery30MinutesAndExpiresAfter5Days() = runBlocking {
        store.save("a", "A", 37.0, 127.0, forecast(t0))

        val loaded = store.load("a", nowMillis = t0 + 29 * 60_000)!!
        assertTrue(!loaded.isStale(t0 + 29 * 60_000))
        assertTrue(loaded.isStale(t0 + 30 * 60_000))

        assertNotNull(store.load("a", nowMillis = t0 + 5 * day - 1))
        assertNull(store.load("a", nowMillis = t0 + 5 * day + 1))
    }

    @Test
    fun savingAgainReplacesRowsAndKeepsViewedOrder() = runBlocking {
        store.save("a", "A", 37.0, 127.0, forecast(t0))
        store.save("b", "B", 35.0, 129.0, forecast(t0))
        store.markViewed("a", t0 + 1)
        store.markViewed("b", t0 + 2)
        assertEquals("b", store.lastViewed(t0 + 3)!!.key)

        // 다시 받아도 마지막으로 본 순서는 그대로다.
        store.save("a", "A2", 37.0, 127.0, forecast(t0 + hour))
        assertEquals("b", store.lastViewed(t0 + hour)!!.key)
        assertEquals("A2", store.load("a", t0 + hour)!!.placeName)
        assertEquals(forecast(t0).hourly.size, store.load("a", t0 + hour)!!.forecast.hourly.size)

        store.delete("b")
        assertEquals("a", store.lastViewed(t0 + hour)!!.key)
    }

    @Test
    fun currentWeatherFollowsHourlyForecastWhenOld() {
        val f = forecast(t0)
        // 받은 시각(15:30)과 같은 시간대면 그대로
        assertSame(f, f.currentAt(LocalDateTime.parse("2026-10-30T15:59")))
        // 이틀 뒤에는 그 시각의 시간별 예보가 현재 날씨가 된다.
        val later = f.currentAt(LocalDateTime.parse("2026-11-01T09:20"))
        val h = f.hourlyAt(LocalDateTime.parse("2026-11-01T09:00"))!!
        assertEquals(h.time, later.current.time)
        assertEquals(h.apparentTemperature, later.current.apparentTemperature, 0.0)
        assertEquals(h.weatherCode, later.current.weatherCode)
    }
}
