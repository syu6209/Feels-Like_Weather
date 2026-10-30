package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.domain.ActivityPlanner
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingLayer
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.DayShape
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.HourFeel
import com.zozi.feels_likeweather.domain.layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ActivityPlanTest {

    private val day = LocalDateTime.of(2026, 10, 31, 0, 0)
    private val t = FeelsThresholds.DEFAULT

    /** (시작 시, 체감) 목록을 시간별 체감으로. */
    private fun hours(vararg spans: Pair<IntRange, Double>): List<HourFeel> = spans.flatMap { (range, apparent) ->
        range.map { h -> HourFeel(day.plusHours(h.toLong()), apparent, t.bandOf(apparent)) }
    }

    @Test
    fun stableDayHasNoHeadlineAndPlainOutfit() {
        val plan = ActivityPlanner.plan(hours(8..19 to 15.0))!!
        assertEquals(DayShape.STABLE, plan.shape)
        assertNull(plan.headline)
        assertEquals(
            ClothingRecommender.itemsFor(AgeGroup.ADULT, FeelsBand.COOL),
            ClothingRecommender.outfit(AgeGroup.ADULT, plan).map { it.item },
        )
    }

    @Test
    fun chillyMorningWarmNoonCoolEveningIsPeak() {
        // 8~10시 쌀쌀함(9°), 11~17시 따뜻함(22°), 18~19시 선선함(15°)
        val plan = ActivityPlanner.plan(hours(8..10 to 9.0, 11..17 to 22.0, 18..19 to 15.0))!!
        assertEquals(DayShape.PEAK, plan.shape)
        assertEquals(FeelsBand.WARM, plan.coreBand) // 가장 오래 머무는 구간 (평균 17.6° 의 '선선함' 이 아님)
        assertEquals(FeelsBand.CHILLY, plan.coldBand)
        assertTrue(plan.bigSwing)
        assertTrue(plan.advice.first().startsWith("체감 차이가 커요"))
        assertEquals("8~11시 쌀쌀함 → 11~18시 따뜻함 → 18~20시 선선함", plan.timeline)

        val outfit = ClothingRecommender.outfit(AgeGroup.ADULT, plan)
        // 기본 옷은 따뜻함 기준, 겉옷은 쌀쌀함 기준 + '벗기 쉬운 겉옷' 설명
        assertTrue(outfit.any { it.item == ClothingItem.LONG_SLEEVE && it.note == null })
        assertTrue(outfit.any { it.item == ClothingItem.COTTON_PANTS })
        val outers = outfit.filter { it.item.layer == ClothingLayer.OUTER }
        assertEquals(listOf(ClothingItem.TRENCH_COAT, ClothingItem.COAT), outers.map { it.item })
        assertTrue(outers.all { it.note == plan.outerNote })
        // 쌀쌀함 기준 상의(니트)·하의(청바지)는 섞이지 않는다
        assertFalse(outfit.any { it.item == ClothingItem.KNIT || it.item == ClothingItem.JEANS })
    }

    @Test
    fun warmingDayTellsToTakeOffLater() {
        val plan = ActivityPlanner.plan(hours(7..9 to 9.0, 10..18 to 22.0))!!
        assertEquals(DayShape.WARMING, plan.shape)
        assertTrue(plan.headline!!.contains("출발할 땐 겉옷"))
        assertEquals("출발할 때 · 벗기 쉬운 겉옷", plan.outerNote)
    }

    @Test
    fun coolingDayTellsToBringOuterForTheWayBack() {
        val plan = ActivityPlanner.plan(hours(9..16 to 21.0, 17..21 to 14.0))!!
        assertEquals(DayShape.COOLING, plan.shape)
        assertTrue(plan.headline!!.contains("17시부터 선선함"))
    }

    @Test
    fun hotNoonAddsLightTop() {
        // 아침 선선함, 낮 대부분 따뜻함, 13~14시만 더움
        val plan = ActivityPlanner.plan(hours(8..9 to 18.0, 10..12 to 23.0, 13..14 to 28.0, 15..17 to 23.0))!!
        assertEquals(FeelsBand.WARM, plan.coreBand)
        assertEquals(FeelsBand.HOT, plan.warmBand)
        val outfit = ClothingRecommender.outfit(AgeGroup.ADULT, plan)
        assertTrue(outfit.any { it.item == ClothingItem.SHORT_SLEEVE && it.note == "더울 땐 이것만" })
    }

    @Test
    fun oneHourBlipIsSmoothedAway() {
        val plan = ActivityPlanner.plan(hours(8..11 to 15.0, 12..12 to 20.5, 13..17 to 15.0))!!
        assertEquals(DayShape.STABLE, plan.shape)
        assertEquals(1, plan.segments.size)
    }

    @Test
    fun edgeHourIsNotSmoothed() {
        // 시작 1시간만 쌀쌀한 경우는 실제로 추운 시간이므로 유지한다
        val plan = ActivityPlanner.plan(hours(8..8 to 10.0, 9..17 to 15.0))!!
        assertEquals(DayShape.WARMING, plan.shape)
        assertEquals(FeelsBand.CHILLY, plan.coldBand)
    }

    @Test
    fun surveyAnswersOverrideHourBands() {
        val hourly = (8..12).map { h ->
            HourlyWeather(day.plusHours(h.toLong()), 15.0, 15.0, 50, 0, 0.0, 0, 1.0, 0.0, true)
        }
        val window = ActivityWindow("내일 8시~13시", hourly)
        val answered = mapOf(day.plusHours(10).toString() to FeelsBand.COLD)
        val feels = ActivityPlanner.hourFeels(window, t, day.minusDays(1), null, answered)
        assertEquals(FeelsBand.COLD, feels.first { it.time.hour == 10 }.band)
        assertTrue(feels.first { it.time.hour == 10 }.answered)
        assertEquals(FeelsBand.COOL, feels.first { it.time.hour == 9 }.band)
    }

    @Test
    fun shapeClassification() {
        assertEquals(DayShape.STABLE, ActivityPlanner.shapeOf(listOf(3)))
        assertEquals(DayShape.WARMING, ActivityPlanner.shapeOf(listOf(2, 3, 4)))
        assertEquals(DayShape.COOLING, ActivityPlanner.shapeOf(listOf(4, 3)))
        assertEquals(DayShape.PEAK, ActivityPlanner.shapeOf(listOf(2, 4, 3)))
        assertEquals(DayShape.DIP, ActivityPlanner.shapeOf(listOf(4, 3, 4)))
        assertEquals(DayShape.MIXED, ActivityPlanner.shapeOf(listOf(2, 4, 2, 4)))
    }

    @Test
    fun coldestHourInColdBandSuggestsAccessoriesInBag() {
        val plan = ActivityPlanner.plan(hours(7..8 to 2.0, 9..17 to 10.0))!!
        assertEquals(FeelsBand.COLD, plan.coldBand)
        assertTrue(plan.advice.any { it.contains("장갑·목도리는 가방에") })
        val outfit = ClothingRecommender.outfit(AgeGroup.ADULT, plan)
        assertTrue(outfit.any { it.item == ClothingItem.GLOVES && it.note == "추울 때 · 가방에" })
    }
}
