package com.zozi.feels_likeweather

import com.zozi.feels_likeweather.domain.BriefingPin
import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.TimeSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class FeelsDomainTest {

    private val default = FeelsThresholds.DEFAULT

    @Test
    fun bandsAreLinearAroundDefaultBoundaries() {
        assertEquals(FeelsBand.FREEZING, default.bandOf(-15.0))
        assertEquals(FeelsBand.COLD, default.bandOf(-10.0))
        assertEquals(FeelsBand.CHILLY, default.bandOf(8.0))
        assertEquals(FeelsBand.COOL, default.bandOf(15.0))
        assertEquals(FeelsBand.WARM, default.bandOf(22.0))
        assertEquals(FeelsBand.HOT, default.bandOf(28.0))
        assertEquals(FeelsBand.SWELTERING, default.bandOf(35.0))
    }

    @Test
    fun briefingIsPinnedForAnHour() {
        val (first, pin) = Briefings.pick(FeelsBand.CHILLY, null, 0L, Random(1))
        val (again, pin2) = Briefings.pick(FeelsBand.CHILLY, pin, 59 * 60 * 1000L, Random(2))
        assertEquals(first, again)
        assertEquals(pin, pin2)
        val (_, pin3) = Briefings.pick(FeelsBand.CHILLY, pin, 61 * 60 * 1000L, Random(3))
        assertNotEquals(pin.index, pin3.index)
    }

    @Test
    fun everyBandHasThreeToFivePhrases() {
        FeelsBand.entries.forEach { band ->
            val size = Briefings.phrases.getValue(band).size
            assertTrue("$band has $size", size in 3..5)
        }
    }

    @Test
    fun briefingPinOutOfRangeIsReplaced() {
        val (_, pin) = Briefings.pick(FeelsBand.HOT, BriefingPin(99, 0L), 10L)
        assertTrue(pin.index in Briefings.phrases.getValue(FeelsBand.HOT).indices)
    }

    @Test
    fun currentSlotIsInsertedBetweenPeriods() {
        val now = LocalDateTime.of(2026, 10, 30, 14, 20)
        val slots = TimeSlots.build(now)
        val i = slots.indexOfFirst { it.isCurrent }
        assertEquals("점심(12시)", slots[i - 1].title)
        assertEquals("오후(3시)", slots[i + 1].title)
        assertEquals("2:20", slots[i].title)
        // 12시간 전(02:20) 이후 첫 슬롯은 오늘 새벽 3시, 48시간 후(모레 14:20) 이전 마지막 슬롯은 모레 점심
        assertEquals("오늘 새벽(3시)", "${slots.first().dayLabel} ${slots.first().title}")
        assertEquals("모레 점심(12시)", "${slots.last().dayLabel} ${slots.last().title}")
    }

    @Test
    fun earlyMorningIncludesYesterday() {
        val slots = TimeSlots.build(LocalDateTime.of(2026, 10, 30, 8, 30))
        assertEquals("어제 늦은저녁(9시)", "${slots.first().dayLabel} ${slots.first().title}")
        // 8:30 은 아침(8시) 박스와 같은 시간대라 합쳐진다.
        assertEquals("아침(8시)", slots.first { it.isCurrent }.title)
    }

    @Test
    fun currentSlotMergesWithMatchingPeriod() {
        val now = LocalDateTime.of(2026, 10, 30, 12, 5)
        val slots = TimeSlots.build(now)
        assertEquals(1, slots.count { it.isCurrent })
        val current = slots.first { it.isCurrent }
        assertEquals("점심(12시)", current.title)
        assertFalse(slots.any { !it.isCurrent && it.hour == current.hour })
    }

    private fun assertBoundariesAscending(t: FeelsThresholds) {
        t.boundaries.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b in ${t.boundaries}", a < b) }
    }
}
