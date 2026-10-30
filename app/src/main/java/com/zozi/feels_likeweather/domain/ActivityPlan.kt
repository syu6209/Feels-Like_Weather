package com.zozi.feels_likeweather.domain

import java.time.LocalDateTime
import kotlin.math.roundToInt

/** 활동 시간 중 한 시간의 체감. */
data class HourFeel(
    val time: LocalDateTime,
    val apparent: Double,
    val band: FeelsBand,
    /** 설문으로 직접 응답한 시간인지. */
    val answered: Boolean = false,
)

/** 같은 체감 구간이 이어지는 시간 묶음. [endExclusive] 는 다음 구간이 시작되는 시각. */
data class FeelsSegment(
    val band: FeelsBand,
    val start: LocalDateTime,
    val endExclusive: LocalDateTime,
) {
    val hours: Int get() = java.time.Duration.between(start, endExclusive).toHours().toInt()

    /** 예) "8~11시" */
    val rangeLabel: String
        get() {
            val end = endExclusive.hour.let { if (it == 0 && endExclusive.toLocalDate() > start.toLocalDate()) 24 else it }
            return "${start.hour}~${end}시"
        }
}

/** 활동 시간 동안 체감이 어떻게 흘러가는지. */
enum class DayShape(val label: String) {
    STABLE("일정"),
    WARMING("갈수록 따뜻해짐"),
    COOLING("갈수록 서늘해짐"),
    PEAK("한낮에 가장 따뜻함"),
    DIP("중간에 서늘해짐"),
    MIXED("여러 번 오르내림"),
}

/**
 * 활동 시간대 체감 분석 결과.
 *
 * @param coreBand 가장 오래 머무는 구간 (상의·하의·신발 기준)
 * @param coldBand 가장 추운 구간 (겉옷·소품 기준)
 * @param warmBand 가장 따뜻한 구간
 * @param headline 구간이 바뀔 때의 안내 (구간이 하나면 null)
 * @param outerNote 겉옷에 붙일 짧은 설명 (예: "출발할 때")
 */
data class ActivityPlan(
    val hours: List<HourFeel>,
    val segments: List<FeelsSegment>,
    val shape: DayShape,
    val coreBand: FeelsBand,
    val coldBand: FeelsBand,
    val warmBand: FeelsBand,
    val minApparent: Double,
    val maxApparent: Double,
    val meanApparent: Double,
    val headline: String?,
    val advice: List<String>,
    val outerNote: String?,
) {
    /** 가장 추운 구간과 가장 따뜻한 구간 사이 단계 수. */
    val bandSpan: Int get() = warmBand.ordinal - coldBand.ordinal

    /** 일교차가 큰지: 구간이 2단계 이상 바뀌거나 체감이 10° 이상 차이 남. */
    val bigSwing: Boolean get() = bandSpan >= 2 || maxApparent - minApparent >= BIG_SWING_DEGREES

    val timeline: String get() = segments.joinToString(" → ") { "${it.rangeLabel} ${it.band.label}" }

    companion object {
        const val BIG_SWING_DEGREES = 10.0
    }
}

/**
 * 활동 시간의 시간별 체감 구간을 보고, 구간이 바뀌는 날의 옷차림 안내를 만든다 (규칙 기반).
 *
 * 단순 평균은 "아침엔 쌀쌀하고 낮엔 따뜻한" 날을 어중간한 구간 하나로 뭉개버리므로,
 *  1. 시간별 구간을 이어 붙여 구간 묶음(segment)을 만들고, 앞뒤가 같은 구간인 1시간짜리 튐은 흡수한다.
 *  2. 묶음의 흐름으로 하루 모양(상승·하강·한낮 정점·중간 하강·오르내림)을 정한다.
 *  3. 가장 오래 머무는 구간으로 기본 옷(상의·하의·신발)을, 가장 추운 구간으로 겉옷·소품을 고르고,
 *     구간이 바뀌면 "벗기 쉬운 겉옷"으로 안내한다.
 */
object ActivityPlanner {

    /**
     * 활동 시간의 시간별 체감.
     * - 지금 시각은 실시간 체감([currentApparent])을 쓴다.
     * - [answers] 에 응답한 시간은 응답 구간을 쓴다 ('내 기준' 프로필).
     */
    fun hourFeels(
        window: ActivityWindow,
        thresholds: FeelsThresholds,
        now: LocalDateTime,
        currentApparent: Double?,
        answers: Map<String, FeelsBand> = emptyMap(),
    ): List<HourFeel> {
        val nowHour = now.withMinute(0).withSecond(0).withNano(0)
        return window.hours.map { h ->
            val apparent = if (h.time == nowHour && currentApparent != null) currentApparent else h.apparentTemperature
            val answer = answers[h.time.toString()]
            HourFeel(h.time, apparent, answer ?: thresholds.bandOf(apparent), answered = answer != null)
        }
    }

    fun plan(hours: List<HourFeel>): ActivityPlan? {
        if (hours.isEmpty()) return null
        val segments = smooth(segmentsOf(hours))
        val bands = segments.map { it.band }
        val shape = shapeOf(bands.map { it.ordinal })

        val core = segments.groupBy { it.band }
            .mapValues { (_, list) -> list.sumOf { it.hours } }
            // 같은 시간이면 더 추운 구간을 기준으로 (덜 입는 것보다 벗는 편이 쉬움)
            .entries.sortedWith(compareByDescending<Map.Entry<FeelsBand, Int>> { it.value }.thenBy { it.key.ordinal })
            .first().key
        val cold = bands.minBy { it.ordinal }
        val warm = bands.maxBy { it.ordinal }
        val min = hours.minOf { it.apparent }
        val max = hours.maxOf { it.apparent }

        val draft = ActivityPlan(
            hours = hours,
            segments = segments,
            shape = shape,
            coreBand = core,
            coldBand = cold,
            warmBand = warm,
            minApparent = min,
            maxApparent = max,
            meanApparent = hours.map { it.apparent }.average(),
            headline = null,
            advice = emptyList(),
            outerNote = null,
        )
        return draft.copy(
            headline = headline(draft),
            advice = advice(draft),
            outerNote = outerNote(shape),
        )
    }

    internal fun segmentsOf(hours: List<HourFeel>): List<FeelsSegment> {
        val result = mutableListOf<FeelsSegment>()
        for (h in hours.sortedBy { it.time }) {
            val last = result.lastOrNull()
            if (last != null && last.band == h.band && last.endExclusive == h.time) {
                result[result.lastIndex] = last.copy(endExclusive = h.time.plusHours(1))
            } else {
                result += FeelsSegment(h.band, h.time, h.time.plusHours(1))
            }
        }
        return result
    }

    /** 앞뒤가 같은 구간인 1시간짜리 묶음은 일시적인 튐으로 보고 앞뒤와 합친다. */
    internal fun smooth(segments: List<FeelsSegment>): List<FeelsSegment> {
        val list = segments.toMutableList()
        var i = 1
        while (i < list.size - 1) {
            val prev = list[i - 1]
            val next = list[i + 1]
            if (list[i].hours == 1 && prev.band == next.band) {
                list[i - 1] = prev.copy(endExclusive = next.endExclusive)
                list.removeAt(i)
                list.removeAt(i)
            } else {
                i++
            }
        }
        return list
    }

    internal fun shapeOf(levels: List<Int>): DayShape {
        if (levels.size <= 1) return DayShape.STABLE
        val ups = levels.zipWithNext().map { (a, b) -> b > a }
        val turns = ups.zipWithNext().count { (a, b) -> a != b }
        return when {
            turns == 0 && ups.first() -> DayShape.WARMING
            turns == 0 -> DayShape.COOLING
            turns == 1 && ups.first() -> DayShape.PEAK
            turns == 1 -> DayShape.DIP
            else -> DayShape.MIXED
        }
    }

    private fun headline(p: ActivityPlan): String? {
        val segs = p.segments
        if (segs.size <= 1) return null
        val coldest = segs.filter { it.band == p.coldBand }
        val warmest = segs.filter { it.band == p.warmBand }
        return when (p.shape) {
            DayShape.STABLE -> null
            DayShape.WARMING ->
                "갈수록 체감이 올라가요. 출발할 땐 겉옷을 입고, ${segs.last().start.hour}시부터는 벗어서 들고 다닐 수 있는 가벼운 겉옷을 고르세요."
            DayShape.COOLING ->
                "갈수록 체감이 내려가요(${segs.last().start.hour}시부터 ${segs.last().band.label}). 나갈 땐 괜찮아도 돌아올 때를 위해 겉옷을 챙기세요."
            DayShape.PEAK ->
                "처음과 끝은 서늘하고 ${warmest.joinToString(", ") { it.rangeLabel }}가 가장 따뜻해요. " +
                    "얇은 옷을 겹쳐 입고, 겉옷은 벗어서 들고 다니기 쉬운 것으로 챙기세요."
            DayShape.DIP ->
                "중간(${coldest.joinToString(", ") { it.rangeLabel }})에 잠시 서늘해져요. 가벼운 겉옷을 가방에 챙겨두세요."
            DayShape.MIXED ->
                "체감이 여러 번 오르내려요(${p.coldBand.label}~${p.warmBand.label}). 입고 벗기 쉽게 겹쳐 입으세요."
        }
    }

    private fun advice(p: ActivityPlan): List<String> = buildList {
        if (p.bigSwing) {
            add(
                "체감 차이가 커요(${p.minApparent.roundToInt()}°~${p.maxApparent.roundToInt()}°). " +
                    "두꺼운 외투 한 벌보다 얇은 옷 여러 겹이 체온 조절에 좋아요."
            )
        }
        if (p.bandSpan > 0 && p.coldBand.ordinal <= FeelsBand.COLD.ordinal) {
            val coldTimes = p.segments.filter { it.band == p.coldBand }.joinToString(", ") { it.rangeLabel }
            add("가장 추울 때($coldTimes)는 ${p.coldBand.label} 수준이에요. 장갑·목도리는 가방에 넣어 다니세요.")
        }
        if (p.bandSpan > 0 && p.warmBand.ordinal >= FeelsBand.HOT.ordinal) {
            add("가장 더울 땐 ${p.warmBand.label} 수준이에요. 안에는 통풍이 잘 되는 얇은 옷을 입으세요.")
        }
    }

    private fun outerNote(shape: DayShape): String? = when (shape) {
        DayShape.STABLE -> null
        DayShape.WARMING -> "출발할 때 · 벗기 쉬운 겉옷"
        DayShape.COOLING -> "돌아올 때 · 챙겨 가세요"
        DayShape.PEAK -> "처음·끝 시간 · 벗기 쉬운 겉옷"
        DayShape.DIP -> "잠깐 서늘할 때"
        DayShape.MIXED -> "추울 때 · 벗기 쉬운 겉옷"
    }
}
