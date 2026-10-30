package com.zozi.feels_likeweather.domain

/**
 * 체감온도 구간. 선언 순서가 곧 추운 → 더운 순서(선형)이다.
 *
 * 기본 분기점(°C, 체감온도 기준)과 근거는 [FeelsThresholds.DEFAULT] 와 docs/FEELS_BANDS.md 참고.
 *
 * @param color 구간 대표 색상 (ARGB)
 * @param adverb "생각보다 더 '춥게' 느끼셨군요!" 처럼 반응 문구에 쓰는 부사형
 */
enum class FeelsBand(
    val label: String,
    val surveyLabel: String,
    val color: Long,
    val adverb: String,
) {
    FREEZING("강추위", "매우 추웠음", 0xFF3D5AFE, "꽁꽁 얼 듯 춥게"),
    COLD("추움", "추웠음", 0xFF2E86DE, "춥게"),
    CHILLY("쌀쌀함", "쌀쌀했음", 0xFF4FC3F7, "쌀쌀하게"),
    COOL("선선함", "선선했음", 0xFF26BFA6, "선선하게"),
    WARM("따뜻함", "따뜻했음", 0xFFFFB300, "따뜻하게"),
    HOT("더움", "더웠음", 0xFFFF7043, "덥게"),
    SWELTERING("무더움", "무더웠음", 0xFFE53935, "푹푹 찌듯 덥게");

    /** 현재 시제 질문용. 예) 지금은 "쌀쌀해요" */
    val presentLabel: String
        get() = when (this) {
            FREEZING -> "매우 추워요"
            COLD -> "추워요"
            CHILLY -> "쌀쌀해요"
            COOL -> "선선해요"
            WARM -> "따뜻해요"
            HOT -> "더워요"
            SWELTERING -> "무더워요"
        }

    companion object {
        val ordered: List<FeelsBand> = entries.toList()
    }
}

/**
 * 구간 분기점. [boundaries]\[i] 는 [FeelsBand] (i+1) 번째 구간이 시작되는 체감온도이다.
 * 크기는 항상 (구간 수 - 1) 이며 오름차순이다.
 */
data class FeelsThresholds(val boundaries: List<Double>) {

    init {
        require(boundaries.size == FeelsBand.entries.size - 1) { "boundaries size mismatch" }
    }

    fun bandOf(apparentTemperature: Double): FeelsBand {
        val index = boundaries.count { apparentTemperature >= it }
        return FeelsBand.entries[index]
    }

    /** [band] 가 시작되는 온도 (가장 추운 구간은 null). */
    fun lowerBoundOf(band: FeelsBand): Double? = boundaries.getOrNull(band.ordinal - 1)

    /** [band] 가 끝나는 온도 (가장 더운 구간은 null). */
    fun upperBoundOf(band: FeelsBand): Double? = boundaries.getOrNull(band.ordinal)

    companion object {
        /**
         * 기본 분기점 (°C, 체감온도).
         *
         * | 경계 | 값 | 근거 |
         * |---|---|---|
         * | 강추위/추움 | -10 | 기상청 겨울철 체감온도 '주의' 단계 시작(-10℃ 미만, 장시간 노출 시 저체온 위험) |
         * | 추움/쌀쌀함 | 4 | 기온별 옷차림 지표: 4℃ 이하 패딩·두꺼운 코트 / UTCI 0~9℃ '약한 추위 스트레스' |
         * | 쌀쌀함/선선함 | 12 | 기온별 옷차림 지표: 12℃ 부터 재킷·가디건, 9~11℃ 트렌치코트 |
         * | 선선함/따뜻함 | 20 | 기온별 옷차림 지표: 20℃ 이상 얇은 긴팔·가디건 / UTCI 9~26℃ 무스트레스 구간 중반 |
         * | 따뜻함/더움 | 26 | UTCI '중간 더위 스트레스' 시작(26℃) |
         * | 더움/무더움 | 32 | UTCI '강한 더위 스트레스' 시작(32℃) · 기상청 폭염주의보(체감 33℃) 직전 |
         */
        val DEFAULT = FeelsThresholds(listOf(-10.0, 4.0, 12.0, 20.0, 26.0, 32.0))

        /** 그래프 표시 범위. */
        const val GRAPH_MIN = -20.0
        const val GRAPH_MAX = 40.0
    }
}
