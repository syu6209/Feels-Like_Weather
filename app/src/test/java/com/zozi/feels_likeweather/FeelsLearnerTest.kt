package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Season
import com.zozi.feels_likeweather.domain.SurveyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeelsLearnerTest {

    private val default = FeelsThresholds.DEFAULT
    private val today = LocalDate.of(2026, 10, 15)
    private var seq = 0L

    /** [date] 15시에 느낀 체감. 응답 시각은 만든 순서대로 늘어난다(나중에 만든 것이 최신). */
    private fun answer(t: Double, band: FeelsBand, date: LocalDate = today, deleted: Boolean = false): SurveyRecord {
        seq++
        return SurveyRecord(
            slotKey = date.atTime(15, 0).toString(),
            apparentTemperature = t,
            reported = band,
            answeredAtMillis = seq,
            deletedAtMillis = if (deleted) 1L else null,
        )
    }

    private fun warm(records: List<SurveyRecord>, on: LocalDate = today) =
        FeelsLearner.estimate(records, on).lowerBoundOf(FeelsBand.WARM)!!

    @Test
    fun noAnswersKeepsDefault() {
        assertEquals(default, FeelsLearner.estimate(emptyList(), today))
    }

    @Test
    fun feelingWarmerLowersWarmBoundaryBelowThatTemperature() {
        // 18° 는 기본값으로 '선선함'. '따뜻했음' 이라고 답하면 '따뜻함' 기준 온도가 18° 아래로 내려간다.
        val t = FeelsLearner.estimate(listOf(answer(18.0, FeelsBand.WARM)), today)
        assertEquals(FeelsBand.WARM, t.bandOf(18.0))
        // 멀리 떨어진 분기점은 그대로
        assertEquals(default.lowerBoundOf(FeelsBand.COLD), t.lowerBoundOf(FeelsBand.COLD))
        assertEquals(default.lowerBoundOf(FeelsBand.SWELTERING), t.lowerBoundOf(FeelsBand.SWELTERING))
    }

    @Test
    fun documentedExamples() {
        // docs/FEELS_BANDS.md 4-2 예시 표와 앱 안내 문구의 숫자
        assertEquals(17.0, warm(listOf(answer(18.0, FeelsBand.WARM))), 1e-9)
        assertEquals(15.6, warm(List(3) { answer(18.0, FeelsBand.WARM) }), 1e-9)
        assertEquals(21.2, warm(listOf(answer(19.0, FeelsBand.WARM)) + List(5) { answer(19.0, FeelsBand.COOL) }), 1e-9)
        // 다른 계절(여름) 응답 하나 → 가을에는 '평소 기준'으로만 반영
        assertEquals(17.5, warm(listOf(answer(18.0, FeelsBand.WARM, LocalDate.of(2026, 7, 20)))), 1e-9)
        // 여름엔 18° 가 따뜻했지만(3번) 가을엔 선선했다(1번) → 가을 기준에서 18° 는 선선함
        val adapt = List(3) { answer(18.0, FeelsBand.WARM, LocalDate.of(2026, 7, 20)) } + answer(18.0, FeelsBand.COOL)
        assertEquals(18.2, warm(adapt), 1e-9)
        assertEquals(FeelsBand.COOL, FeelsLearner.estimate(adapt, today).bandOf(18.0))
    }

    @Test
    fun feelingColderRaisesBoundaries() {
        val t = FeelsLearner.estimate(listOf(answer(22.0, FeelsBand.CHILLY)), today)
        assertTrue(t.lowerBoundOf(FeelsBand.COOL)!! > default.lowerBoundOf(FeelsBand.COOL)!!)
        assertTrue(t.lowerBoundOf(FeelsBand.WARM)!! > default.lowerBoundOf(FeelsBand.WARM)!!)
        assertAscendingWithGap(t)
    }

    @Test
    fun agreeingAnswersReinforceTheDefault() {
        val contrary = answer(19.0, FeelsBand.WARM)
        val withAgreement = listOf(contrary) + List(5) { answer(19.0, FeelsBand.COOL) }
        assertTrue(warm(withAgreement) > warm(listOf(contrary)))
        assertEquals(FeelsBand.COOL, FeelsLearner.estimate(withAgreement, today).bandOf(19.0))
    }

    @Test
    fun resultDoesNotDependOnListOrder() {
        val records = listOf(
            answer(18.0, FeelsBand.WARM),
            answer(9.0, FeelsBand.COLD, LocalDate.of(2026, 1, 10)),
            answer(27.0, FeelsBand.WARM, LocalDate.of(2026, 8, 1)),
        )
        assertEquals(FeelsLearner.estimate(records, today), FeelsLearner.estimate(records.reversed(), today))
    }

    @Test
    fun deleteAndRestoreAreExact() {
        val a = answer(18.0, FeelsBand.WARM)
        val b = answer(9.0, FeelsBand.COLD)
        val c = answer(15.0, FeelsBand.CHILLY)
        val deletedC = listOf(a, b, c.copy(deletedAtMillis = 1L))
        assertEquals(FeelsLearner.estimate(listOf(a, b), today), FeelsLearner.estimate(deletedC, today))
        // 되돌리면(원래 응답 시각 유지) 정확히 이전 상태로 돌아간다
        val restored = deletedC.map { it.copy(deletedAtMillis = null) }
        assertEquals(FeelsLearner.estimate(listOf(a, b, c), today), FeelsLearner.estimate(restored, today))
    }

    @Test
    fun manyConsistentAnswersConvergeBetweenTheData() {
        // 16° 는 늘 따뜻했고, 14° 는 늘 선선했던 사람 → '따뜻함' 기준은 14~16° 사이
        val records = (1..10).flatMap { listOf(answer(16.0, FeelsBand.WARM), answer(14.0, FeelsBand.COOL)) }
        assertTrue(warm(records) in 14.0..16.0)
    }

    @Test
    fun springAndAutumnKeepSeparateBands() {
        // 봄엔 16° 가 따뜻하고, 가을엔 16° 가 선선한 사람 (계절 적응)
        val records = List(10) { answer(16.0, FeelsBand.WARM, LocalDate.of(2026, 4, 15)) } +
            List(10) { answer(16.0, FeelsBand.COOL, LocalDate.of(2026, 10, 15)) }
        assertEquals(FeelsBand.WARM, FeelsLearner.estimate(records, LocalDate.of(2027, 4, 20)).bandOf(16.0))
        assertEquals(FeelsBand.COOL, FeelsLearner.estimate(records, LocalDate.of(2026, 10, 20)).bandOf(16.0))
        val seasons = FeelsLearner.seasonalEstimates(records, 2026)
        assertNotEquals(seasons.getValue(Season.SPRING), seasons.getValue(Season.AUTUMN))
    }

    @Test
    fun lastYearsSameSeasonCountsFully() {
        val lastYear = listOf(answer(18.0, FeelsBand.WARM, LocalDate.of(2025, 10, 15)))
        val thisYear = listOf(answer(18.0, FeelsBand.WARM, LocalDate.of(2026, 10, 15)))
        assertEquals(warm(thisYear), warm(lastYear), 1e-9)
    }

    @Test
    fun noJumpAtCalendarSeasonBoundary() {
        val records = List(5) { answer(3.0, FeelsBand.CHILLY, LocalDate.of(2026, 2, 20)) } +
            List(5) { answer(3.0, FeelsBand.CHILLY, LocalDate.of(2026, 3, 8)) }
        val feb28 = FeelsLearner.estimate(records, LocalDate.of(2027, 2, 28)).lowerBoundOf(FeelsBand.CHILLY)!!
        val mar1 = FeelsLearner.estimate(records, LocalDate.of(2027, 3, 1)).lowerBoundOf(FeelsBand.CHILLY)!!
        assertTrue("$feb28 vs $mar1", kotlin.math.abs(feb28 - mar1) <= 0.2)
    }

    @Test
    fun countWeightTapersFromThirtyToNinety() {
        (0 until FeelsLearner.FULL_WEIGHT_COUNT).forEach { assertEquals(1.0, FeelsLearner.countWeight(it), 0.0) }
        assertEquals(0.0, FeelsLearner.countWeight(FeelsLearner.ZERO_WEIGHT_COUNT - 1), 0.0) // 90번째
        val weights = (0..100).map(FeelsLearner::countWeight)
        weights.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
        assertTrue(FeelsLearner.countWeight(60) in 0.4..0.6)
    }

    @Test
    fun answersBeyondTheCountWindowHaveNoEffect() {
        val old = List(10) { answer(18.0, FeelsBand.WARM) }
        val recent = List(FeelsLearner.ZERO_WEIGHT_COUNT - 1) { answer(16.0 + it % 5, FeelsBand.COOL) }
        // 최신 89개 뒤로 밀려난 오래된 10개는 결과에 영향이 없다
        assertEquals(FeelsLearner.estimate(recent, today), FeelsLearner.estimate(old + recent, today))
    }

    @Test
    fun winterAnswersDoNotPushOutSummerAnswers() {
        // 여름에 22° 가 선선했던 사람이, 그 뒤 겨울에 응답을 100개 했다
        val summer = List(10) { answer(22.0, FeelsBand.COOL, LocalDate.of(2026, 7, 20)) }
        val winter = List(100) { answer(0.0, FeelsBand.COLD, LocalDate.of(2027, 1, 15)) }
        // 다음 여름에도 여름 응답이 그대로 반영된다 (계절 안에서 순번을 셈)
        assertEquals(FeelsBand.COOL, FeelsLearner.estimate(summer + winter, LocalDate.of(2027, 7, 20)).bandOf(22.0))
    }

    @Test
    fun contradictoryAnswersKeepOrderAndGap() {
        val records = listOf(
            answer(-15.0, FeelsBand.SWELTERING),
            answer(38.0, FeelsBand.FREEZING),
            answer(10.0, FeelsBand.HOT),
            answer(25.0, FeelsBand.COLD),
        )
        assertAscendingWithGap(FeelsLearner.estimate(records, today))
    }

    @Test
    fun purgeRemovesOnlyRecordsDeletedOverAWeekAgo() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val fresh = answer(10.0, FeelsBand.COLD).copy(deletedAtMillis = now - 6 * day)
        val expired = answer(10.0, FeelsBand.COLD).copy(deletedAtMillis = now - 7 * day)
        val active = answer(10.0, FeelsBand.COLD)
        assertEquals(listOf(fresh, active), FeelsLearner.purge(listOf(fresh, expired, active), now))
    }

    @Test
    fun reactionPhraseUsesReportedBand() {
        assertEquals("생각보다 더 춥게 느끼셨군요!", Briefings.reaction(FeelsBand.COLD, isNow = false).text)
        assertEquals("춥게", Briefings.reaction(FeelsBand.COLD, isNow = true).keyword)
    }

    private fun assertAscendingWithGap(t: FeelsThresholds) {
        t.boundaries.zipWithNext().forEach { (a, b) ->
            assertTrue("$a .. $b in ${t.boundaries}", b - a >= FeelsLearner.MIN_GAP - 1e-6)
        }
    }
}
