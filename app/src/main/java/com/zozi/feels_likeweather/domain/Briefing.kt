package com.zozi.feels_likeweather.domain

import kotlin.random.Random

/** 브리핑 한 문장. [keyword] 부분에 흐르는 브러쉬 그라데이션 효과가 들어간다. */
data class BriefingPhrase(val prefix: String, val keyword: String, val suffix: String) {
    val text: String get() = prefix + keyword + suffix
}

/** 구간별로 한번 노출된 문구를 1시간 동안 고정하기 위한 기록. */
data class BriefingPin(val index: Int, val pinnedAtMillis: Long)

object Briefings {

    /**
     * 설문 응답이 예보 구간과 다를 때 잠깐 보여주는 반응 문구.
     * 예) 예보 쌀쌀함, 응답 추움 → "생각보다 더 '춥게' 느끼셨군요!"
     */
    fun reaction(reported: FeelsBand, isNow: Boolean): BriefingPhrase =
        BriefingPhrase("생각보다 더 ", reported.adverb, if (isNow) " 느끼시는군요!" else " 느끼셨군요!")

    const val PIN_DURATION_MILLIS = 60 * 60 * 1000L

    val phrases: Map<FeelsBand, List<BriefingPhrase>> = mapOf(
        FeelsBand.FREEZING to listOf(
            BriefingPhrase("살을 에는 듯 ", "매섭게 추운", " 날이에요. 롱패딩과 목도리, 장갑까지 꼭 챙기세요."),
            BriefingPhrase("숨 쉬기도 ", "매섭게 추울", " 거예요. 외출은 짧게, 따뜻한 실내에서 자주 쉬어가세요."),
            BriefingPhrase("손끝이 시릴 만큼 ", "매섭게 추워요", ". 내복부터 겹겹이 껴입는 걸 추천해요."),
            BriefingPhrase("한파급으로 ", "매섭게 추운", " 날씨예요. 동상에 주의하고 노출된 피부를 가려주세요."),
        ),
        FeelsBand.COLD to listOf(
            BriefingPhrase("", "춥게", " 느끼실 거예요. 두툼한 패딩을 꺼낼 때예요."),
            BriefingPhrase("바깥은 꽤 ", "추워요", ". 목도리 하나 두르면 한결 따뜻할 거예요."),
            BriefingPhrase("코끝이 찡하게 ", "추운", " 날이에요. 기모 바지와 장갑을 챙겨보세요."),
            BriefingPhrase("오늘은 ", "춥게", " 느껴질 거예요. 따뜻한 음료 한 잔 어때요?"),
            BriefingPhrase("찬 바람에 ", "춥게", " 느끼실 수 있어요. 두꺼운 외투가 필요해요."),
        ),
        FeelsBand.CHILLY to listOf(
            BriefingPhrase("", "쌀쌀하게", " 느끼실거에요. 얇은 겉옷을 준비하는건 어때요?"),
            BriefingPhrase("바람이 ", "쌀쌀하게", " 느껴질 거예요. 가벼운 코트를 챙겨보세요."),
            BriefingPhrase("공기가 제법 ", "쌀쌀해요", ". 니트 위에 겉옷 하나 걸치면 딱 좋아요."),
            BriefingPhrase("조금 ", "쌀쌀할", " 수 있어요. 겹쳐 입기 좋은 날이에요."),
        ),
        FeelsBand.COOL to listOf(
            BriefingPhrase("", "선선하게", " 느끼실 거예요. 가벼운 재킷이면 충분해요."),
            BriefingPhrase("바람이 ", "선선해서", " 걷기 좋은 날이에요. 맨투맨 하나 어때요?"),
            BriefingPhrase("", "선선한", " 공기가 기분 좋을 거예요. 가디건을 챙기면 든든해요."),
            BriefingPhrase("활동하기 좋게 ", "선선해요", ". 긴팔에 얇은 겉옷이면 딱이에요."),
        ),
        FeelsBand.WARM to listOf(
            BriefingPhrase("", "따뜻하게", " 느끼실 거예요. 얇은 긴팔이면 충분해요."),
            BriefingPhrase("햇살이 ", "따뜻한", " 날이에요. 산책하기 정말 좋겠어요."),
            BriefingPhrase("", "따뜻해서", " 겉옷은 가볍게, 셔츠 한 장이면 좋아요."),
            BriefingPhrase("포근하고 ", "따뜻하게", " 느껴질 거예요. 가벼운 차림으로 나가보세요."),
        ),
        FeelsBand.HOT to listOf(
            BriefingPhrase("", "덥게", " 느끼실 거예요. 반팔과 통풍 잘 되는 옷을 추천해요."),
            BriefingPhrase("햇볕 아래는 꽤 ", "더워요", ". 물을 자주 마셔주세요."),
            BriefingPhrase("조금만 걸어도 ", "덥게", " 느껴질 거예요. 시원한 옷차림이 좋아요."),
            BriefingPhrase("한낮엔 ", "더운", " 날씨예요. 모자와 선크림을 챙겨보세요."),
        ),
        FeelsBand.SWELTERING to listOf(
            BriefingPhrase("숨이 턱 막히게 ", "무더워요", ". 한낮 야외활동은 피하는 게 좋아요."),
            BriefingPhrase("푹푹 찌듯 ", "무덥게", " 느끼실 거예요. 그늘에서 자주 쉬어가세요."),
            BriefingPhrase("온열질환에 주의해야 할 만큼 ", "무더운", " 날이에요. 물을 충분히 드세요."),
            BriefingPhrase("가만히 있어도 ", "무더울", " 거예요. 가장 얇고 밝은 옷을 입으세요."),
        ),
    )

    /**
     * 구간의 브리핑 문구를 고른다. 이미 고정된 문구가 1시간 이내라면 그대로 쓰고,
     * 아니면 직전 문구와 다른 문구를 무작위로 골라 새로 고정한다.
     *
     * @return 선택된 문구와 (변경되었다면) 새 고정 정보
     */
    fun pick(
        band: FeelsBand,
        pin: BriefingPin?,
        nowMillis: Long,
        random: Random = Random.Default,
    ): Pair<BriefingPhrase, BriefingPin> {
        val list = phrases.getValue(band)
        if (pin != null && pin.index in list.indices && nowMillis - pin.pinnedAtMillis in 0 until PIN_DURATION_MILLIS) {
            return list[pin.index] to pin
        }
        val candidates = list.indices.filter { it != pin?.index }.ifEmpty { list.indices.toList() }
        val index = candidates[random.nextInt(candidates.size)]
        return list[index] to BriefingPin(index, nowMillis)
    }
}
