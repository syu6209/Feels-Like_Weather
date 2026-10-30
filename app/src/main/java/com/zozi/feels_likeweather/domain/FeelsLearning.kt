package com.zozi.feels_likeweather.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 체감 설문 1건. 같은 [slotKey] 에 다시 응답하면 기존 응답을 덮어쓴다.
 *
 * @param slotKey 응답한 시간대 (ISO 정시, 예: "2026-10-30T15:00"). 계절 판단에 이 날짜를 쓴다.
 * @param predicted 응답 당시 예보(내 체감 구간)였던 구간. 기록 화면과 "쌀쌀함 → 추움" 표시에 쓴다.
 * @param deletedAtMillis 삭제한 시각. null 이 아니면 학습에서 빠지고, [FeelsLearner.PURGE_AFTER_MILLIS] 뒤 완전 삭제된다.
 */
data class SurveyRecord(
    val slotKey: String,
    val apparentTemperature: Double,
    val reported: FeelsBand,
    val answeredAtMillis: Long,
    val predicted: FeelsBand? = null,
    val deletedAtMillis: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null

    /** 체감을 느낀 날짜. 시간대 키를 읽지 못하면 응답한 날짜. */
    val observedDate: LocalDate
        get() = runCatching { LocalDateTime.parse(slotKey).toLocalDate() }.getOrElse {
            Instant.ofEpochMilli(answeredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
}

/**
 * [slotKey] 시간대 응답을 기록한다. 같은 시간대의 이전 응답(삭제된 것 포함)은 대체하고,
 * 다시 응답해도 "원래 예보" 는 처음 응답할 때의 예보로 유지한다.
 */
fun List<SurveyRecord>.withAnswer(
    slotKey: String,
    apparentTemperature: Double,
    reported: FeelsBand,
    predicted: FeelsBand,
    nowMillis: Long,
): List<SurveyRecord> {
    val existing = firstOrNull { it.slotKey == slotKey && !it.isDeleted }
    return filterNot { it.slotKey == slotKey } + SurveyRecord(
        slotKey = slotKey,
        apparentTemperature = apparentTemperature,
        reported = reported,
        answeredAtMillis = nowMillis,
        predicted = existing?.predicted ?: predicted,
    )
}

/** 계절 이름 표시용 (한국 기준 3개월씩). 계산은 날짜 거리로 부드럽게 한다. */
enum class Season(val label: String, val representative: MonthDay) {
    SPRING("봄", MonthDay.of(4, 15)),
    SUMMER("여름", MonthDay.of(7, 20)),
    AUTUMN("가을", MonthDay.of(10, 15)),
    WINTER("겨울", MonthDay.of(1, 15));

    companion object {
        fun of(date: LocalDate): Season = when (date.monthValue) {
            3, 4, 5 -> SPRING
            6, 7, 8 -> SUMMER
            9, 10, 11 -> AUTUMN
            else -> WINTER
        }
    }
}

/**
 * 설문 응답으로 "내 체감 구간" 분기점을 추정한다.
 *
 * ## 1. 분기점별 정규화 로지스틱 회귀 (MAP)
 * 응답 하나(체감 t, 응답 구간 r)는 각 분기점 k 마다 "t 에서 구간 k 이상으로 느꼈다(r ≥ k)" 라는 관측이다.
 * P(r ≥ k | t) = σ((t − θ_k) / [SLOPE]) 로 두고, 사전분포와 함께 가중 가능도를 최대화하는 θ_k 를 구한다.
 *
 * ## 2. 계절 적응: 비슷한 시기의 응답을 더 믿는다
 * 사람은 계절에 적응해서 같은 15°도 봄과 가을에 다르게 느낀다. 응답마다 "오늘과 날짜(월·일)가 얼마나 가까운지"로
 * 계절 가중치 s 를 준다 (같은 시기 1, 한 달 차이 약 0.65, [SEASON_HALF_WIDTH_DAYS]일 이상 0, 해는 무시).
 * 달력으로 딱 자르지 않아 2월 말 → 3월 초에 기준이 갑자기 바뀌지 않고, 작년 같은 계절 응답도 그대로 쓰인다.
 *
 * ## 3. 두 단계 추정 (응답을 두 번 세지 않음)
 *  1) 평소 기준: 기본 분기점에서 시작해, 각 응답을 (1 − s) 만큼 반영 → 계절과 무관한 나의 평소 경향
 *  2) 이번 계절 기준: 평소 기준에서 시작해(불확실도 + 계절 차이 [SEASON_SD]°), 각 응답을 s 만큼 반영
 * 응답 하나의 가중치가 두 단계에 나뉘어 들어가므로 중복 반영되지 않는다. 새 계절이 시작돼 그 계절 응답이 없으면
 * 기본값이 아니라 평소 기준을 쓴다.
 *
 * ## 4. 개수 기반 감쇠 (시간이 아니라 응답 수로)
 * 시간이 지났다고 잊지 않는다. 대신 새 응답이 쌓이면 오래된 응답의 영향을 줄인다: 최신 [FULL_WEIGHT_COUNT]개는 100%,
 * 그보다 오래된 응답은 선형으로 줄어 [ZERO_WEIGHT_COUNT]번째부터 0. 이번 계절 단계는 "그 계절에 해당하는 응답끼리"
 * 순번을 세므로, 겨울에 응답을 많이 해도 지난여름 응답이 밀려나지 않는다.
 *
 * ## 5. 순서 보정
 * 분기점이 [MIN_GAP] 이상 떨어진 오름차순이 되도록 정밀도 가중 등위회귀(PAV)로 맞춘다.
 *
 * 모든 계산이 응답 순서와 무관한 일괄 계산이라, 삭제 = 빼고 다시 계산, 되돌리기 = 원래 시각 그대로 넣고 다시 계산이며
 * 되돌리면 정확히 원래 값으로 돌아온다.
 */
object FeelsLearner {
    /** 사람이 같은 온도를 다르게 느끼는 흔들림 정도(°C). */
    const val SLOPE = 1.5

    /** 기본 분기점을 얼마나 믿을지(°C). */
    const val PRIOR_SD = 3.0

    /** 계절에 따라 평소 기준에서 벗어날 수 있는 정도(°C). */
    const val SEASON_SD = 2.0

    /** 날짜(월·일) 차이가 이 일수 이상이면 이번 계절 단계에 반영하지 않는다. */
    const val SEASON_HALF_WIDTH_DAYS = 75.0

    /** 최신 응답부터 이 개수까지는 영향력 100%. */
    const val FULL_WEIGHT_COUNT = 30

    /** 최신 응답부터 셌을 때 이 순번(1부터 셈)부터 영향력 0. */
    const val ZERO_WEIGHT_COUNT = 90

    const val MIN_GAP = 1.0
    const val PURGE_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000

    private const val LOWEST = -40.0
    private const val HIGHEST = 50.0

    /** [today] 계절에 맞는 내 체감 구간. */
    fun estimate(
        records: List<SurveyRecord>,
        today: LocalDate,
        base: FeelsThresholds = FeelsThresholds.DEFAULT,
    ): FeelsThresholds {
        val active = records.filterNot { it.isDeleted }.sortedByDescending { it.answeredAtMillis }
        if (active.isEmpty()) return base

        val season = active.map { seasonWeight(it.observedDate, today) }

        // 1) 평소 기준: 전체 응답 순번으로 감쇠, 계절 가중치의 나머지(1 − s)만큼 반영
        val overall = fitAll(
            priorMeans = base.boundaries,
            priorSds = base.boundaries.map { PRIOR_SD },
            records = active,
            weights = active.indices.map { i -> countWeight(i) * (1 - season[i]) },
        )

        // 2) 이번 계절 기준: 이번 계절 응답끼리의 순번으로 감쇠, 계절 가중치 s 만큼 반영
        var seasonalRank = 0
        val seasonalWeights = active.indices.map { i ->
            if (season[i] > 0) season[i] * countWeight(seasonalRank++) else 0.0
        }
        val seasonal = fitAll(
            priorMeans = overall.map { it.first },
            priorSds = overall.map { (_, precision) -> sqrt(1 / precision + SEASON_SD * SEASON_SD) },
            records = active,
            weights = seasonalWeights,
        )

        val ordered = orderWithGap(seasonal.map { it.first }, seasonal.map { it.second })
        return FeelsThresholds(ordered.map { Math.round(it.coerceIn(LOWEST, HIGHEST) * 10) / 10.0 })
    }

    /** 계절별 내 체감 구간 (각 계절 대표일 기준). */
    fun seasonalEstimates(records: List<SurveyRecord>, year: Int): Map<Season, FeelsThresholds> =
        Season.entries.associateWith { estimate(records, it.representative.atYear(year)) }

    /** [today] 계절에 반영되는(계절 가중치 > 0) 응답 수. */
    fun inSeasonCount(records: List<SurveyRecord>, today: LocalDate): Int =
        records.count { !it.isDeleted && seasonWeight(it.observedDate, today) > 0 }

    /** 최신순 [rank] 번째(0부터) 응답의 영향력. */
    fun countWeight(rank: Int): Double = when {
        rank < FULL_WEIGHT_COUNT -> 1.0
        rank >= ZERO_WEIGHT_COUNT - 1 -> 0.0
        else -> 1.0 - (rank - FULL_WEIGHT_COUNT + 1).toDouble() / (ZERO_WEIGHT_COUNT - FULL_WEIGHT_COUNT)
    }

    /** 날짜(월·일) 거리에 따른 계절 가중치 [0, 1]. 해는 무시한다. */
    fun seasonWeight(observed: LocalDate, today: LocalDate): Double {
        val d = abs(observed.dayOfYear - today.dayOfYear).toDouble()
        val distance = min(d, 365.0 - d)
        if (distance >= SEASON_HALF_WIDTH_DAYS) return 0.0
        val c = cos(PI / 2 * distance / SEASON_HALF_WIDTH_DAYS)
        return c * c
    }

    /** 7일이 지난 삭제 기록을 걸러낸다. */
    fun purge(records: List<SurveyRecord>, nowMillis: Long): List<SurveyRecord> =
        records.filterNot { r -> r.deletedAtMillis?.let { nowMillis - it >= PURGE_AFTER_MILLIS } == true }

    /** 분기점마다 (MAP 추정값, 정밀도). */
    private fun fitAll(
        priorMeans: List<Double>,
        priorSds: List<Double>,
        records: List<SurveyRecord>,
        weights: List<Double>,
    ): List<Pair<Double, Double>> = priorMeans.indices.map { i ->
        val k = i + 1 // 분기점 i 는 구간 k 가 시작되는 온도
        fitBoundary(
            prior = priorMeans[i],
            priorSd = priorSds[i],
            temps = records.map { it.apparentTemperature },
            above = records.map { it.reported.ordinal >= k },
            weights = weights,
        )
    }

    private fun fitBoundary(
        prior: Double,
        priorSd: Double,
        temps: List<Double>,
        above: List<Boolean>,
        weights: List<Double>,
    ): Pair<Double, Double> {
        val priorPrecision = 1.0 / (priorSd * priorSd)
        var theta = prior
        var precision = priorPrecision
        repeat(50) {
            var gradient = -(theta - prior) * priorPrecision
            var curvature = priorPrecision
            for (i in temps.indices) {
                if (weights[i] <= 0) continue
                val p = sigmoid((temps[i] - theta) / SLOPE)
                val y = if (above[i]) 1.0 else 0.0
                gradient += weights[i] * (p - y) / SLOPE
                curvature += weights[i] * p * (1 - p) / (SLOPE * SLOPE)
            }
            // 목적함수가 오목하므로 뉴턴 스텝이 수렴한다. 초반 큰 스텝만 제한한다.
            val step = (gradient / curvature).coerceIn(-5.0, 5.0)
            theta += step
            precision = curvature
            if (abs(step) < 1e-6) return theta to precision
        }
        return theta to precision
    }

    /**
     * 분기점이 오름차순이면서 서로 [MIN_GAP] 이상 떨어지도록 정밀도 가중 등위회귀로 보정한다.
     * b_i − i·gap 이 단조 증가하도록 맞추면 원래 값 사이 간격이 gap 이상이 된다.
     */
    private fun orderWithGap(values: List<Double>, weights: List<Double>): List<Double> {
        val shifted = values.mapIndexed { i, v -> v - i * MIN_GAP }
        val blocks = ArrayList<DoubleArray>() // [평균, 가중치합, 개수]
        for (i in shifted.indices) {
            blocks += doubleArrayOf(shifted[i], weights[i], 1.0)
            while (blocks.size >= 2 && blocks[blocks.size - 2][0] > blocks.last()[0]) {
                val b = blocks.removeAt(blocks.size - 1)
                val a = blocks.removeAt(blocks.size - 1)
                val w = a[1] + b[1]
                blocks += doubleArrayOf((a[0] * a[1] + b[0] * b[1]) / w, w, a[2] + b[2])
            }
        }
        val fitted = blocks.flatMap { b -> List(b[2].toInt()) { b[0] } }
        return fitted.mapIndexed { i, v -> v + i * MIN_GAP }
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
