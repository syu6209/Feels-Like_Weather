package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClothingTest {

    private fun hour(h: Int, apparent: Double, pop: Int = 0, code: Int = 0, uv: Double = 0.0) = HourlyWeather(
        time = LocalDateTime.of(2026, 10, 30, h, 0),
        temperature = apparent,
        apparentTemperature = apparent,
        humidity = 50,
        precipitationProbability = pop,
        precipitation = 0.0,
        weatherCode = code,
        windSpeed = 2.0,
        uvIndex = uv,
        isDay = true,
    )

    @Test
    fun everyAgeAndBandHasItems() {
        AgeGroup.entries.forEach { age ->
            FeelsBand.entries.forEach { band ->
                assertTrue("$age/$band", ClothingRecommender.itemsFor(age, band).isNotEmpty())
            }
        }
    }

    @Test
    fun neededWeatherItemsComeFirst() {
        val window = ActivityWindow("오늘", listOf(hour(9, 15.0, pop = 80, code = 63), hour(10, 16.0, pop = 60)))
        val rec = ClothingRecommender.recommend(AgeGroup.ADULT, FeelsBand.COOL, window, FeelsThresholds.DEFAULT)
        assertEquals(ClothingItem.UMBRELLA, rec.conditionalItems.first().item)
        assertTrue(rec.conditionalItems.first().needed)
        val firstUnneeded = rec.conditionalItems.indexOfFirst { !it.needed }
        assertTrue(rec.conditionalItems.drop(firstUnneeded).none { it.needed })
    }

    @Test
    fun noRainMeansUmbrellaAtBottom() {
        val window = ActivityWindow("오늘", listOf(hour(12, 30.0, uv = 8.0)))
        val rec = ClothingRecommender.recommend(AgeGroup.ADULT, FeelsBand.HOT, window, FeelsThresholds.DEFAULT)
        val umbrella = rec.conditionalItems.first { it.item == ClothingItem.UMBRELLA }
        assertFalse(umbrella.needed)
        assertTrue(rec.conditionalItems.first().needed)
        assertTrue(rec.conditionalItems.first { it.item == ClothingItem.SUNGLASSES }.needed)
    }

    @Test
    fun infantsGetNoScarfOrSunglasses() {
        FeelsBand.entries.forEach { band ->
            assertFalse(ClothingRecommender.itemsFor(AgeGroup.INFANT, band).contains(ClothingItem.SCARF))
        }
        val window = ActivityWindow("오늘", listOf(hour(12, 30.0, uv = 9.0)))
        val rec = ClothingRecommender.recommend(AgeGroup.INFANT, FeelsBand.HOT, window, FeelsThresholds.DEFAULT)
        assertTrue(rec.conditionalItems.none { it.item == ClothingItem.SUNGLASSES })
    }
}
