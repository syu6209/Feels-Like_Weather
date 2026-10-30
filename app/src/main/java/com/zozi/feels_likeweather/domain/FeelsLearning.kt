package com.zozi.feels_likeweather.domain

/** 체감 설문 1건. 같은 [slotKey] 에 다시 응답하면 기존 응답을 덮어쓴다. */
data class SurveyRecord(
    val slotKey: String,
    val apparentTemperature: Double,
    val reported: FeelsBand,
    val answeredAtMillis: Long,
)

/**
 * 설문 응답으로 "내 체감 구간" 분기점을 보정한다.
 *
 * 응답 하나가 들어올 때마다, 현재 분기점으로 예측한 구간 p 와 사용자가 응답한 구간 r 이 다르면
 * 두 구간 사이의 분기점을 응답 온도 t 쪽으로 [LEARNING_RATE] 만큼 끌어당긴다.
 *  - r > p (예상보다 따뜻하게 느낌): 분기점 p+1..r 을 t 아래로 → 해당 구간 기준 온도가 낮아진다.
 *  - r < p (예상보다 춥게 느낌): 분기점 r+1..p 를 t 위로 → 해당 구간 기준 온도가 높아진다.
 * 한 번에 완전히 옮기지 않고 절반씩 이동하므로 응답이 쌓일수록 점점 내 체감에 수렴한다.
 * 분기점 사이에는 최소 [MIN_GAP] 의 간격을 유지해 구간 순서가 뒤집히지 않게 한다.
 */
object FeelsLearner {
    const val LEARNING_RATE = 0.5
    const val MARGIN = 0.5
    const val MIN_GAP = 1.0
    private const val LOWEST = -40.0
    private const val HIGHEST = 50.0

    /** 기본값에서 시작해 응답을 시간순으로 재생한 결과. */
    fun replay(records: List<SurveyRecord>, base: FeelsThresholds = FeelsThresholds.DEFAULT): FeelsThresholds =
        records.sortedBy { it.answeredAtMillis }.fold(base) { acc, r -> apply(acc, r.apparentTemperature, r.reported) }

    fun apply(thresholds: FeelsThresholds, apparent: Double, reported: FeelsBand): FeelsThresholds {
        val predicted = thresholds.bandOf(apparent)
        if (predicted == reported) return thresholds
        val b = thresholds.boundaries.toMutableList()
        val p = predicted.ordinal
        val r = reported.ordinal
        if (r > p) {
            // 분기점 k (k = 구간 k 의 시작) 를 t 이하로 내린다. boundaries 인덱스는 k-1.
            val target = apparent - MARGIN
            for (k in p + 1..r) {
                val i = k - 1
                b[i] = minOf(b[i], b[i] + LEARNING_RATE * (target - b[i]))
            }
            for (i in r - 1 downTo 1) b[i - 1] = minOf(b[i - 1], b[i] - MIN_GAP)
            for (i in r until b.size) b[i] = maxOf(b[i], b[i - 1] + MIN_GAP)
        } else {
            val target = apparent + MARGIN
            for (k in r + 1..p) {
                val i = k - 1
                b[i] = maxOf(b[i], b[i] + LEARNING_RATE * (target - b[i]))
            }
            for (i in r + 1 until b.size) b[i] = maxOf(b[i], b[i - 1] + MIN_GAP)
            for (i in r - 1 downTo 0) b[i] = minOf(b[i], b[i + 1] - MIN_GAP)
        }
        return FeelsThresholds(b.map { it.coerceIn(LOWEST, HIGHEST).roundTo(1) })
    }

    private fun Double.roundTo(decimals: Int): Double {
        var m = 1.0
        repeat(decimals) { m *= 10 }
        return Math.round(this * m) / m
    }
}
