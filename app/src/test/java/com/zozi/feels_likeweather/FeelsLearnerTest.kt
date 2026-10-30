package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.SurveyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeelsLearnerTest {

    private val default = FeelsThresholds.DEFAULT
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day

    private fun answer(key: String, t: Double, band: FeelsBand, daysAgo: Long = 0, deleted: Boolean = false) =
        SurveyRecord(key, t, band, now - daysAgo * day, deletedAtMillis = if (deleted) now else null)

    @Test
    fun noAnswersKeepsDefault() {
        assertEquals(default, FeelsLearner.estimate(emptyList(), now))
    }

    @Test
    fun feelingWarmerLowersWarmBoundaryBelowThatTemperature() {
        // 18° 는 기본값으로 '선선함'. '따뜻했음' 이라고 답하면 '따뜻함' 기준 온도가 18° 아래로 내려간다.
        val t = FeelsLearner.estimate(listOf(answer("a", 18.0, FeelsBand.WARM)), now)
        assertTrue(t.lowerBoundOf(FeelsBand.WARM)!! < 18.0)
        assertEquals(FeelsBand.WARM, t.bandOf(18.0))
        // 멀리 떨어진 분기점은 거의 그대로
        assertEquals(default.lowerBoundOf(FeelsBand.COLD), t.lowerBoundOf(FeelsBand.COLD))
        assertEquals(default.lowerBoundOf(FeelsBand.SWELTERING), t.lowerBoundOf(FeelsBand.SWELTERING))
    }

    @Test
    fun documentedExamples() {
        // docs/FEELS_BANDS.md 4-2 예시 표와 앱 안내 문구의 숫자
        fun warm(vararg r: SurveyRecord) = FeelsLearner.estimate(r.toList(), now).lowerBoundOf(FeelsBand.WARM)!!
        assertEquals(17.5, warm(answer("a", 18.0, FeelsBand.WARM)), 1e-9)
        assertEquals(16.1, warm(answer("a", 18.0, FeelsBand.WARM), answer("b", 18.0, FeelsBand.WARM), answer("c", 18.0, FeelsBand.WARM)), 1e-9)
        assertEquals(18.3, warm(answer("a", 18.0, FeelsBand.WARM, daysAgo = 60)), 1e-9)
        val agree = (1..5).map { answer("c$it", 19.0, FeelsBand.COOL) }.toTypedArray()
        assertEquals(21.1, warm(answer("w", 19.0, FeelsBand.WARM), *agree), 1e-9)
    }

    @Test
    fun feelingColderRaisesBoundaries() {
        val t = FeelsLearner.estimate(listOf(answer("a", 22.0, FeelsBand.CHILLY)), now)
        assertTrue(t.lowerBoundOf(FeelsBand.COOL)!! > default.lowerBoundOf(FeelsBand.COOL)!!)
        assertTrue(t.lowerBoundOf(FeelsBand.WARM)!! > default.lowerBoundOf(FeelsBand.WARM)!!)
        assertAscendingWithGap(t)
    }

    @Test
    fun agreeingAnswersReinforceTheDefault() {
        // 예보와 같은 응답도 근거가 되어, 반대 응답 하나에 덜 흔들리게 한다.
        val contrary = answer("x", 19.0, FeelsBand.WARM)
        val alone = FeelsLearner.estimate(listOf(contrary), now)
        val withAgreement = FeelsLearner.estimate(
            listOf(contrary) + (1..5).map { answer("c$it", 19.0, FeelsBand.COOL) },
            now,
        )
        assertTrue(withAgreement.lowerBoundOf(FeelsBand.WARM)!! > alone.lowerBoundOf(FeelsBand.WARM)!!)
        assertEquals(FeelsBand.COOL, withAgreement.bandOf(19.0))
    }

    @Test
    fun resultDoesNotDependOnOrder() {
        val records = listOf(
            answer("a", 18.0, FeelsBand.WARM, daysAgo = 3),
            answer("b", 9.0, FeelsBand.COLD, daysAgo = 2),
            answer("c", 27.0, FeelsBand.WARM, daysAgo = 1),
        )
        assertEquals(FeelsLearner.estimate(records, now), FeelsLearner.estimate(records.reversed(), now))
    }

    @Test
    fun deleteAndRestoreAreExact() {
        val base = listOf(answer("a", 18.0, FeelsBand.WARM, 5), answer("b", 9.0, FeelsBand.COLD, 2))
        val withC = base + answer("c", 15.0, FeelsBand.CHILLY, 1)
        val deletedC = base + answer("c", 15.0, FeelsBand.CHILLY, 1, deleted = true)

        // 삭제한 기록은 없는 것과 같고
        assertEquals(FeelsLearner.estimate(base, now), FeelsLearner.estimate(deletedC, now))
        // 되돌리면(원래 응답 시각 유지) 정확히 이전 상태로 돌아간다
        val restored = deletedC.map { it.copy(deletedAtMillis = null) }
        assertEquals(FeelsLearner.estimate(withC, now), FeelsLearner.estimate(restored, now))
    }

    @Test
    fun manyConsistentAnswersConvergeBetweenTheData() {
        // 16° 는 늘 따뜻했고, 14° 는 늘 선선했던 사람 → '따뜻함' 기준은 14~16° 사이
        val records = (1..10).flatMap {
            listOf(answer("w$it", 16.0, FeelsBand.WARM, it.toLong()), answer("c$it", 14.0, FeelsBand.COOL, it.toLong()))
        }
        val warm = FeelsLearner.estimate(records, now).lowerBoundOf(FeelsBand.WARM)!!
        assertTrue("$warm", warm in 14.0..16.0)
    }

    @Test
    fun recentAnswersOutweighOldOnes() {
        // 반년 전엔 18° 가 따뜻했지만, 최근엔 18° 가 선선하다 → 최근 응답 쪽을 따른다(계절 적응)
        val records = listOf(
            answer("old1", 18.0, FeelsBand.WARM, 180),
            answer("old2", 18.5, FeelsBand.WARM, 175),
            answer("new", 18.0, FeelsBand.COOL, 1),
        )
        assertEquals(FeelsBand.COOL, FeelsLearner.estimate(records, now).bandOf(18.0))
    }

    @Test
    fun contradictoryAnswersKeepOrderAndGap() {
        val records = listOf(
            answer("a", -15.0, FeelsBand.SWELTERING),
            answer("b", 38.0, FeelsBand.FREEZING),
            answer("c", 10.0, FeelsBand.HOT),
            answer("d", 25.0, FeelsBand.COLD),
        )
        assertAscendingWithGap(FeelsLearner.estimate(records, now))
    }

    @Test
    fun purgeRemovesOnlyRecordsDeletedOverAWeekAgo() {
        val fresh = answer("a", 10.0, FeelsBand.COLD).copy(deletedAtMillis = now - 6 * day)
        val expired = answer("b", 10.0, FeelsBand.COLD).copy(deletedAtMillis = now - 7 * day)
        val active = answer("c", 10.0, FeelsBand.COLD)
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
