package com.zozi.feels_likeweather.domain

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 체감 예보의 시간대 박스 하나.
 *
 * @param key 설문 기록에 쓰는 고유 키 (정시 ISO 시각)
 * @param hour 예보 조회용 정시 시각
 * @param dayLabel 어제/오늘/내일/모레
 * @param periodName 새벽/아침/... (현재 박스는 null 일 수 있음)
 */
data class TimeSlot(
    val key: String,
    val hour: LocalDateTime,
    val dayLabel: String,
    val periodName: String?,
    val clockHour: Int,
    val isCurrent: Boolean,
    val currentTime: LocalDateTime? = null,
) {
    /**
     * 박스에 표시하는 문구. 예) "아침(8시)", 현재 박스는 "3:20" 또는 겹치는 시간대 이름 "점심(12시)".
     * 현재 박스는 박스 위쪽에 "지금" 이 따로 표시되므로 여기서는 "현재" 를 붙이지 않는다.
     */
    val title: String
        get() = when {
            periodName != null -> "$periodName(${clockHour}시)"
            else -> currentTime?.let { "%d:%02d".format(twelve(it.hour), it.minute) } ?: "${clockHour}시"
        }

    /** 설문 질문에 쓰는 문구. 예) "오늘 아침 실외", "지금 실외" */
    val questionSubject: String
        get() = if (isCurrent) "지금 실외" else "$dayLabel $periodName 실외"

    /** 아직 오지 않은 시간대인지 (설문 불가). */
    fun isFuture(now: LocalDateTime): Boolean = !isCurrent && hour.isAfter(now)

    companion object {
        fun twelve(hour24: Int): Int = (hour24 % 12).let { if (it == 0) 12 else it }
    }
}

object TimeSlots {

    /** 기 정의된 시간대: (시, 이름). */
    val periods: List<Pair<Int, String>> = listOf(
        3 to "새벽",
        6 to "이른아침",
        8 to "아침",
        12 to "점심",
        15 to "오후",
        18 to "저녁",
        21 to "늦은저녁",
        23 to "밤",
    )

    const val HOURS_BEFORE = 12L
    const val HOURS_AFTER = 48L

    /** 현재 기준 12시간 전 ~ 48시간 후의 시간대 박스와, 그 사이에 끼워 넣은 "현재" 박스. */
    fun build(now: LocalDateTime): List<TimeSlot> {
        val nowHour = now.truncatedTo(ChronoUnit.HOURS)
        val start = now.minusHours(HOURS_BEFORE)
        val end = now.plusHours(HOURS_AFTER)
        val today = now.toLocalDate()

        val slots = mutableListOf<TimeSlot>()
        var date = start.toLocalDate()
        while (!date.isAfter(end.toLocalDate())) {
            for ((h, name) in periods) {
                val t = date.atTime(h, 0)
                if (t.isBefore(start) || t.isAfter(end)) continue
                val isCurrent = t == nowHour
                slots += TimeSlot(
                    key = t.toString(),
                    hour = t,
                    dayLabel = dayLabel(ChronoUnit.DAYS.between(today, date)),
                    periodName = name,
                    clockHour = TimeSlot.twelve(h),
                    isCurrent = isCurrent,
                    currentTime = if (isCurrent) now else null,
                )
            }
            date = date.plusDays(1)
        }
        if (slots.none { it.isCurrent }) {
            val current = TimeSlot(
                key = nowHour.toString(),
                hour = nowHour,
                dayLabel = "오늘",
                periodName = null,
                clockHour = TimeSlot.twelve(now.hour),
                isCurrent = true,
                currentTime = now,
            )
            val index = slots.indexOfFirst { it.hour.isAfter(now) }.let { if (it < 0) slots.size else it }
            slots.add(index, current)
        }
        return slots
    }

    fun dayLabel(offset: Long): String = when (offset) {
        -1L -> "어제"
        0L -> "오늘"
        1L -> "내일"
        2L -> "모레"
        else -> if (offset < 0) "${-offset}일 전" else "${offset}일 후"
    }
}
