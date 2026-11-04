package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.imageNames
import com.zozi.feels_likeweather.domain.isOnePiece
import com.zozi.feels_likeweather.domain.layer
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
        val rec = ClothingRecommender.recommend(AgeGroup.ADULT, null, FeelsBand.COOL, window)
        assertEquals(ClothingItem.UMBRELLA, rec.conditionalItems.first().item)
        assertTrue(rec.conditionalItems.first().needed)
        val firstUnneeded = rec.conditionalItems.indexOfFirst { !it.needed }
        assertTrue(rec.conditionalItems.drop(firstUnneeded).none { it.needed })
    }

    @Test
    fun noRainMeansUmbrellaAtBottom() {
        val window = ActivityWindow("오늘", listOf(hour(12, 30.0, uv = 8.0)))
        val rec = ClothingRecommender.recommend(AgeGroup.ADULT, null, FeelsBand.HOT, window)
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
        val rec = ClothingRecommender.recommend(AgeGroup.INFANT, null, FeelsBand.HOT, window)
        assertTrue(rec.conditionalItems.none { it.item == ClothingItem.SUNGLASSES })
    }

    @Test
    fun alternativesReplaceAnItemInTheSameLayer() {
        AgeGroup.entries.forEach { age ->
            FeelsBand.entries.forEach { band ->
                val picks = ClothingRecommender.picksFor(age, band)
                picks.filter { it.insteadOf != null }.forEach { alt ->
                    val primary = alt.insteadOf!!
                    assertTrue("$age/$band: ${alt.item} 대신할 $primary 가 없음", picks.any { it.item == primary && it.insteadOf == null })
                    // 같은 층에서 하나를 고르거나, 원피스형이 상·하의를 대신한다.
                    assertTrue("$age/$band: ${alt.item}", alt.item.layer == primary.layer || alt.item.isOnePiece)
                }
            }
        }
    }

    @Test
    fun infantGetsTopAndBottomWithOptionalRomper() {
        val rec = ClothingRecommender.recommend(AgeGroup.INFANT, null, FeelsBand.HOT, ActivityWindow("오늘", listOf(hour(12, 28.0))))
        val items = rec.items.associateBy { it.item }
        assertFalse(items.getValue(ClothingItem.SHORT_SLEEVE).isAlternative)
        assertFalse(items.getValue(ClothingItem.SHORTS).isAlternative)
        assertEquals("상·하의 대신", items.getValue(ClothingItem.ROMPER).alternativeNote)
    }

    @Test
    fun coatIsAnAlternativeToTrenchCoat() {
        val rec = ClothingRecommender.recommend(AgeGroup.ADULT, null, FeelsBand.CHILLY, ActivityWindow("오늘", listOf(hour(9, 8.0))))
        val coat = rec.items.first { it.item == ClothingItem.COAT }
        assertEquals(ClothingItem.TRENCH_COAT, coat.insteadOf)
        assertEquals("트렌치코트 대신", coat.alternativeNote)
    }

    @Test
    fun infantImagesFallBackToChildThenCommon() {
        assertEquals(
            listOf("cloth_infant_long_sleeve", "cloth_child_long_sleeve", "cloth_long_sleeve"),
            ClothingItem.LONG_SLEEVE.imageNames(AgeGroup.INFANT),
        )
        assertEquals(listOf("cloth_adult_coat", "cloth_coat"), ClothingItem.COAT.imageNames(AgeGroup.ADULT))
    }
}
