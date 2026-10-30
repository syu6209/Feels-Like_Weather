package com.zozi.feels_likeweather.domain

import kotlin.math.exp
import kotlin.math.pow

/**
 * 체감 설문 1건. 같은 [slotKey] 에 다시 응답하면 기존 응답을 덮어쓴다.
 *
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
}

/**
 * 설문 응답으로 "내 체감 구간" 분기점을 추정한다.
 *
 * ## 방식: 분기점별 정규화 로지스틱 회귀 (MAP 추정)
 * 응답 하나(체감온도 t, 응답 구간 r)는 각 분기점 k 에 대해 "t 에서 구간 k 이상으로 느꼈는가(r ≥ k)"라는
 * 예/아니오 관측이 된다. 분기점 θ_k 근처에서 그렇게 느낄 확률을 σ((t − θ_k) / [SLOPE]) 로 보고,
 *  - 기본 분기점을 중심으로 한 정규분포 사전분포(표준편차 [PRIOR_SD])와
 *  - 응답들의 가능도(최근 응답일수록 큰 가중치, 반감기 [HALF_LIFE_DAYS])
 * 를 함께 최대화하는 θ_k 를 뉴턴법으로 구한다.
 *
 * 응답이 적을 때는 기본값 근처에 머물고, 쌓일수록 데이터 쪽으로 수렴한다. 예보와 같은 구간을 고른 응답도
 * "지금 기준이 맞다"는 근거로 반영된다. 순서와 무관한 일괄 계산이라 삭제는 해당 응답을 빼고 다시 계산하는 것,
 * 되돌리기는 원래 응답 시각 그대로 다시 넣고 계산하는 것으로 정확히 원래 상태로 돌아간다.
 *
 * 마지막으로 분기점이 서로 [MIN_GAP] 이상 떨어진 오름차순이 되도록 가중 등위회귀(PAV)로 보정한다.
 */
object FeelsLearner {
    /** 사람이 같은 온도를 다르게 느끼는 흔들림 정도(°C). 작을수록 응답 하나에 민감하게 반응한다. */
    const val SLOPE = 1.5

    /** 기본 분기점을 얼마나 믿을지(°C). 클수록 응답에 빨리 따라간다. */
    const val PRIOR_SD = 3.0

    /** 계절에 따라 체감이 적응하므로 오래된 응답일수록 가중치를 줄인다. */
    const val HALF_LIFE_DAYS = 60.0

    const val MIN_GAP = 1.0
    const val PURGE_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000

    private const val LOWEST = -40.0
    private const val HIGHEST = 50.0
    private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

    fun estimate(
        records: List<SurveyRecord>,
        nowMillis: Long,
        base: FeelsThresholds = FeelsThresholds.DEFAULT,
    ): FeelsThresholds {
        val active = records.filterNot { it.isDeleted }
        if (active.isEmpty()) return base
        val weights = active.map { weight(nowMillis - it.answeredAtMillis) }

        val fits = base.boundaries.mapIndexed { i, prior ->
            val k = i + 1 // 분기점 i 는 구간 k 가 시작되는 온도
            fitBoundary(
                prior = prior,
                temps = active.map { it.apparentTemperature },
                above = active.map { it.reported.ordinal >= k },
                weights = weights,
            )
        }
        val ordered = orderWithGap(fits.map { it.first }, fits.map { it.second })
        return FeelsThresholds(ordered.map { (Math.round(it.coerceIn(LOWEST, HIGHEST) * 10) / 10.0) })
    }

    /** 응답 시점으로부터 지난 시간에 따른 가중치 (0, 1]. */
    fun weight(ageMillis: Long): Double =
        0.5.pow(ageMillis.coerceAtLeast(0) / DAY_MILLIS / HALF_LIFE_DAYS)

    /** 7일이 지난 삭제 기록을 걸러낸다. */
    fun purge(records: List<SurveyRecord>, nowMillis: Long): List<SurveyRecord> =
        records.filterNot { r -> r.deletedAtMillis?.let { nowMillis - it >= PURGE_AFTER_MILLIS } == true }

    /** 한 분기점의 MAP 추정값과 정밀도(곡률). */
    private fun fitBoundary(
        prior: Double,
        temps: List<Double>,
        above: List<Boolean>,
        weights: List<Double>,
    ): Pair<Double, Double> {
        val priorPrecision = 1.0 / (PRIOR_SD * PRIOR_SD)
        var theta = prior
        var precision = priorPrecision
        repeat(50) {
            var gradient = -(theta - prior) * priorPrecision
            var curvature = priorPrecision
            for (i in temps.indices) {
                val p = sigmoid((temps[i] - theta) / SLOPE)
                val y = if (above[i]) 1.0 else 0.0
                gradient += weights[i] * (p - y) / SLOPE
                curvature += weights[i] * p * (1 - p) / (SLOPE * SLOPE)
            }
            // 목적함수가 오목하므로 뉴턴 스텝이 수렴한다. 초반 큰 스텝만 제한한다.
            val step = (gradient / curvature).coerceIn(-5.0, 5.0)
            theta += step
            precision = curvature
            if (kotlin.math.abs(step) < 1e-6) return theta to precision
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
