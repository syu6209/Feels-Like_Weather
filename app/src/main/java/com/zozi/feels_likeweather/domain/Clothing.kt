package com.zozi.feels_likeweather.domain

import com.zozi.feels_likeweather.data.model.HourlyWeather
import com.zozi.feels_likeweather.domain.ClothingItem.*
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.model.isHeavyRain
import com.zozi.feels_likeweather.data.model.isSnow
import com.zozi.feels_likeweather.data.model.isWet
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 옷/소품 항목. [key] 는 이미지 리소스 이름 규칙에 쓰인다.
 * 이미지 탐색 순서: `cloth_<연령대>_<key>` → `cloth_<key>` → 기본 아이콘. (docs/CLOTHING_IMAGES.md 참고)
 */
enum class ClothingItem(val key: String, val label: String, val conditional: Boolean = false) {
    SLEEVELESS("sleeveless", "민소매"),
    SHORT_SLEEVE("short_sleeve", "반팔 티셔츠"),
    LINEN_SHIRT("linen_shirt", "얇은 셔츠"),
    LONG_SLEEVE("long_sleeve", "얇은 긴팔"),
    CARDIGAN("cardigan", "가디건"),
    SWEATSHIRT("sweatshirt", "맨투맨·후드티"),
    KNIT("knit", "니트"),
    THERMAL("thermal", "내복·발열내의"),
    VEST("vest", "경량 조끼"),
    LIGHT_JACKET("light_jacket", "얇은 재킷·바람막이"),
    TRENCH_COAT("trench_coat", "트렌치코트"),
    COAT("coat", "코트"),
    LIGHT_PADDING("light_padding", "경량 패딩"),
    PADDING("padding", "패딩"),
    LONG_PADDING("long_padding", "롱패딩"),
    SHORTS("shorts", "반바지"),
    COTTON_PANTS("cotton_pants", "면바지"),
    JEANS("jeans", "청바지"),
    FLEECE_PANTS("fleece_pants", "기모 바지"),
    ROMPER("romper", "반팔 바디수트"),
    LONG_ROMPER("long_romper", "긴팔 우주복"),
    SNOWSUIT("snowsuit", "방한 우주복"),
    SANDALS("sandals", "샌들"),
    SNEAKERS("sneakers", "운동화"),
    WINTER_BOOTS("winter_boots", "방한화"),
    SCARF("scarf", "목도리"),
    NECK_WARMER("neck_warmer", "넥워머"),
    GLOVES("gloves", "장갑"),
    BEANIE("beanie", "털모자"),

    // 특정 날씨 조건에서만 필요한 아이템
    UMBRELLA("umbrella", "우산", true),
    RAINCOAT("raincoat", "우비", true),
    RAIN_BOOTS("rain_boots", "장화", true),
    SUN_HAT("sun_hat", "모자", true),
    SUNGLASSES("sunglasses", "선글라스", true),
    SUNSCREEN("sunscreen", "선크림", true),
    WATER_BOTTLE("water_bottle", "물병", true),
    HAND_WARMER("hand_warmer", "핫팩", true),
}

data class ConditionalItem(val item: ClothingItem, val needed: Boolean, val reason: String)

/** 프로필의 활동 시간대 날씨 요약. */
data class ActivityWindow(
    val label: String,
    val hours: List<HourlyWeather>,
) {
    val minApparent: Double? get() = hours.minOfOrNull { it.apparentTemperature }
    val maxApparent: Double? get() = hours.maxOfOrNull { it.apparentTemperature }
    val maxPrecipitationProbability: Int get() = hours.maxOfOrNull { it.precipitationProbability } ?: 0
    val precipitationSum: Double get() = hours.sumOf { it.precipitation }
    val maxUv: Double get() = hours.maxOfOrNull { it.uvIndex } ?: 0.0
    val maxWind: Double get() = hours.maxOfOrNull { it.windSpeed } ?: 0.0
    val wet: Boolean get() = hours.any { isWet(it.weatherCode) }
    val heavyRain: Boolean get() = hours.any { isHeavyRain(it.weatherCode) } || precipitationSum >= 10.0
    val snow: Boolean get() = hours.any { isSnow(it.weatherCode) }

    /** 활동 시간대 평균 체감온도. 해당 날짜의 대표 체감으로 쓴다. */
    val meanApparent: Double? get() = hours.takeIf { it.isNotEmpty() }?.map { it.apparentTemperature }?.average()

    companion object {
        /**
         * 처음 보여줄 날짜. 오늘 활동 시간이 끝났으면(오늘 밤) 내일, 아니면(오늘 아침·낮) 오늘.
         * @return 오늘 기준 날짜 차이 (0 = 오늘, 1 = 내일)
         */
        fun defaultDayOffset(profile: Profile, now: LocalDateTime): Int =
            if (now.hour >= profile.activityEndHour) 1 else 0

        /** 다가오는(또는 진행 중인) 활동 시간대. */
        fun of(forecast: WeatherForecast, profile: Profile, now: LocalDateTime): ActivityWindow =
            forDay(forecast, profile, now, defaultDayOffset(profile, now))

        /**
         * [dayOffset] 일 뒤의 활동 시간대. 오늘이고 활동이 이미 시작됐다면 지금부터 끝까지만 본다.
         */
        fun forDay(forecast: WeatherForecast, profile: Profile, now: LocalDateTime, dayOffset: Int): ActivityWindow {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val start = date.atStartOfDay().plusHours(profile.activityStartHour.toLong())
            val end = date.atStartOfDay().plusHours(profile.activityEndHour.toLong())
            val nowHour = now.withMinute(0).withSecond(0).withNano(0)
            val from = if (dayOffset == 0 && nowHour.isAfter(start) && nowHour.isBefore(end)) nowHour else start
            val hours = forecast.hourly.filter { !it.time.isBefore(from) && it.time.isBefore(end) }
            val endLabel = if (profile.activityEndHour == 24) "24시" else "${profile.activityEndHour}시"
            return ActivityWindow(
                "${TimeSlots.dayLabel(dayOffset.toLong())} ${profile.activityStartHour}시~$endLabel",
                hours,
            )
        }
    }
}

data class ClothingRecommendation(
    val band: FeelsBand,
    val items: List<ClothingItem>,
    /** 필요한 아이템이 먼저, 불필요한 아이템이 뒤에 오도록 정렬됨. */
    val conditionalItems: List<ConditionalItem>,
    val tips: List<String>,
    val window: ActivityWindow,
)

/**
 * 연령대 × 체감 구간별 옷차림 추천.
 *
 * 근거 (자세한 내용은 docs/FEELS_BANDS.md):
 *  - 성인: 국내에서 널리 쓰이는 '기온별 옷차림' 지표(28℃↑ 민소매·반팔 … 4℃↓ 패딩)를 7개 체감 구간에 맞춰 재구성.
 *  - 유아: 미국소아과학회(AAP) "같은 환경의 어른보다 한 겹 더" 권고 → 추운 구간에서 한 단계 더 따뜻하게.
 *    질식·끼임 위험이 있는 목도리 대신 넥워머, 6개월 미만은 선크림 대신 그늘·모자·얇은 긴소매.
 *  - 아동: 활동량이 많아 땀이 나기 쉬우므로 벗고 입기 쉬운 겉옷 위주.
 *  - 중장년: 나이가 들수록 체온 조절 능력이 떨어진다는 CDC 안내 → 추운 구간은 내의·모자 강조, 더운 구간은 통풍.
 *  - 자외선: WHO UV Index 3 이상 차단 필요, 6 이상 높음.
 */
object ClothingRecommender {

    private val table: Map<AgeGroup, Map<FeelsBand, List<ClothingItem>>> =
        mapOf(
            AgeGroup.ADULT to mapOf(
                FeelsBand.SWELTERING to listOf(SLEEVELESS, SHORT_SLEEVE, SHORTS, SANDALS),
                FeelsBand.HOT to listOf(SHORT_SLEEVE, LINEN_SHIRT, SHORTS, COTTON_PANTS, SNEAKERS),
                FeelsBand.WARM to listOf(LONG_SLEEVE, LINEN_SHIRT, CARDIGAN, COTTON_PANTS, SNEAKERS),
                FeelsBand.COOL to listOf(SWEATSHIRT, KNIT, LIGHT_JACKET, JEANS, SNEAKERS),
                FeelsBand.CHILLY to listOf(KNIT, TRENCH_COAT, COAT, JEANS, SNEAKERS),
                FeelsBand.COLD to listOf(THERMAL, KNIT, PADDING, FLEECE_PANTS, SCARF, GLOVES, WINTER_BOOTS),
                FeelsBand.FREEZING to listOf(THERMAL, KNIT, LONG_PADDING, FLEECE_PANTS, BEANIE, SCARF, GLOVES, WINTER_BOOTS),
            ),
            AgeGroup.CHILD to mapOf(
                FeelsBand.SWELTERING to listOf(SHORT_SLEEVE, SHORTS, SANDALS),
                FeelsBand.HOT to listOf(SHORT_SLEEVE, SHORTS, SNEAKERS),
                FeelsBand.WARM to listOf(SHORT_SLEEVE, CARDIGAN, COTTON_PANTS, SNEAKERS),
                FeelsBand.COOL to listOf(LONG_SLEEVE, SWEATSHIRT, LIGHT_JACKET, COTTON_PANTS, SNEAKERS),
                FeelsBand.CHILLY to listOf(LONG_SLEEVE, SWEATSHIRT, LIGHT_PADDING, JEANS, SNEAKERS),
                FeelsBand.COLD to listOf(THERMAL, SWEATSHIRT, PADDING, FLEECE_PANTS, BEANIE, GLOVES, WINTER_BOOTS),
                FeelsBand.FREEZING to listOf(THERMAL, KNIT, LONG_PADDING, FLEECE_PANTS, BEANIE, NECK_WARMER, GLOVES, WINTER_BOOTS),
            ),
            AgeGroup.INFANT to mapOf(
                FeelsBand.SWELTERING to listOf(ROMPER, SANDALS),
                FeelsBand.HOT to listOf(ROMPER, SHORTS, SANDALS),
                FeelsBand.WARM to listOf(LONG_ROMPER, CARDIGAN, SNEAKERS),
                FeelsBand.COOL to listOf(LONG_ROMPER, VEST, LIGHT_JACKET, SNEAKERS),
                FeelsBand.CHILLY to listOf(LONG_ROMPER, SWEATSHIRT, LIGHT_PADDING, BEANIE, SNEAKERS),
                FeelsBand.COLD to listOf(THERMAL, LONG_ROMPER, SNOWSUIT, BEANIE, NECK_WARMER, GLOVES, WINTER_BOOTS),
                FeelsBand.FREEZING to listOf(THERMAL, LONG_ROMPER, SNOWSUIT, BEANIE, NECK_WARMER, GLOVES, WINTER_BOOTS),
            ),
            AgeGroup.SENIOR to mapOf(
                FeelsBand.SWELTERING to listOf(SHORT_SLEEVE, LINEN_SHIRT, COTTON_PANTS, SANDALS),
                FeelsBand.HOT to listOf(SHORT_SLEEVE, LINEN_SHIRT, COTTON_PANTS, SNEAKERS),
                FeelsBand.WARM to listOf(LONG_SLEEVE, CARDIGAN, COTTON_PANTS, SNEAKERS),
                FeelsBand.COOL to listOf(KNIT, VEST, LIGHT_JACKET, COTTON_PANTS, SNEAKERS),
                FeelsBand.CHILLY to listOf(THERMAL, KNIT, COAT, FLEECE_PANTS, SCARF, SNEAKERS),
                FeelsBand.COLD to listOf(THERMAL, KNIT, LONG_PADDING, FLEECE_PANTS, BEANIE, SCARF, GLOVES, WINTER_BOOTS),
                FeelsBand.FREEZING to listOf(THERMAL, KNIT, LONG_PADDING, FLEECE_PANTS, BEANIE, SCARF, GLOVES, WINTER_BOOTS),
            ),
        )

    fun itemsFor(ageGroup: AgeGroup, band: FeelsBand): List<ClothingItem> =
        table.getValue(ageGroup).getValue(band)

    fun recommend(
        ageGroup: AgeGroup,
        band: FeelsBand,
        window: ActivityWindow,
        thresholds: FeelsThresholds,
    ): ClothingRecommendation {
        val conditional = conditionalItems(ageGroup, band, window, thresholds)
            .sortedByDescending { it.needed }
        return ClothingRecommendation(
            band = band,
            items = itemsFor(ageGroup, band),
            conditionalItems = conditional,
            tips = tips(ageGroup, band, window, thresholds),
            window = window,
        )
    }

    private fun conditionalItems(
        ageGroup: AgeGroup,
        band: FeelsBand,
        w: ActivityWindow,
        thresholds: FeelsThresholds,
    ): List<ConditionalItem> {
        val rainy = w.maxPrecipitationProbability >= 40 || w.precipitationSum >= 0.5 || w.wet
        val rainReason = if (rainy) "강수확률 ${w.maxPrecipitationProbability}%" else "비 소식 없음"
        // WHO UV Index 는 정수로 반올림해 안내하므로, 판단과 표시 모두 반올림 값을 쓴다.
        val uv = w.maxUv.roundToInt().toDouble()
        val uvText = "자외선 ${uv.roundToInt()} (${uvLevel(uv)})"
        val maxBand = w.maxApparent?.let(thresholds::bandOf) ?: band
        val minBand = w.minApparent?.let(thresholds::bandOf) ?: band
        val hot = maxOf(maxBand.ordinal, band.ordinal) >= FeelsBand.HOT.ordinal
        val cold = minOf(minBand.ordinal, band.ordinal) <= FeelsBand.COLD.ordinal
        val kid = ageGroup == AgeGroup.INFANT || ageGroup == AgeGroup.CHILD

        return buildList {
            add(ConditionalItem(ClothingItem.UMBRELLA, rainy, rainReason))
            if (kid) add(ConditionalItem(ClothingItem.RAINCOAT, rainy, if (rainy) "아이는 우비가 안전해요" else rainReason))
            add(
                ConditionalItem(
                    ClothingItem.RAIN_BOOTS,
                    w.heavyRain || w.snow,
                    when {
                        w.snow -> "눈 소식"
                        w.heavyRain -> "많은 비 (%.1fmm)".format(w.precipitationSum)
                        else -> "많은 비·눈 없음"
                    },
                )
            )
            add(ConditionalItem(ClothingItem.SUN_HAT, uv >= 3 || hot, if (uv >= 3) uvText else if (hot) "더운 날씨" else uvText))
            if (ageGroup != AgeGroup.INFANT) add(ConditionalItem(ClothingItem.SUNGLASSES, uv >= 6, uvText))
            add(
                ConditionalItem(
                    ClothingItem.SUNSCREEN,
                    uv >= 3,
                    if (ageGroup == AgeGroup.INFANT && uv >= 3) "$uvText · 6개월 미만은 그늘 권장" else uvText,
                )
            )
            add(ConditionalItem(ClothingItem.WATER_BOTTLE, hot, if (hot) "더위 · 수분 보충" else "더위 없음"))
            add(ConditionalItem(ClothingItem.HAND_WARMER, cold, if (cold) "추위" else "추위 없음"))
        }
    }

    private fun tips(ageGroup: AgeGroup, band: FeelsBand, w: ActivityWindow, thresholds: FeelsThresholds): List<String> {
        val coldSide = band.ordinal <= FeelsBand.CHILLY.ordinal
        val hotSide = band.ordinal >= FeelsBand.HOT.ordinal
        val tips = mutableListOf<String>()
        when (ageGroup) {
            AgeGroup.INFANT -> {
                if (coldSide) tips += "아이는 같은 환경의 어른보다 한 겹 더 입혀주세요. 목 뒤에 땀이 나면 한 겹 벗겨 과열을 막아주세요."
                if (band.ordinal <= FeelsBand.COLD.ordinal) tips += "목도리는 끈이 걸릴 수 있어 넥워머를 권장해요."
                if (hotSide) tips += "6개월 미만 영아는 선크림보다 그늘·모자·얇은 긴소매로 햇빛을 가려주세요."
                if (!coldSide && !hotSide) tips += "얇은 옷을 겹쳐 입혀 체온에 맞게 조절해주세요."
            }
            AgeGroup.CHILD -> tips += "활동량이 많아 땀이 나기 쉬워요. 벗고 입기 쉬운 겉옷으로 겹쳐 입혀주세요."
            AgeGroup.ADULT -> Unit
            AgeGroup.SENIOR -> {
                if (coldSide || band == FeelsBand.COOL) tips += "체온 조절 능력이 떨어질 수 있어요. 얇은 옷을 여러 겹 입고 모자로 머리를 따뜻하게 해주세요."
                if (hotSide) tips += "더위를 늦게 느낄 수 있어요. 밝은 색의 통풍 잘 되는 옷을 입고 물을 자주 드세요."
            }
        }
        val min = w.minApparent
        val max = w.maxApparent
        if (min != null && max != null && max - min >= 8) {
            tips += "활동 시간 동안 체감 ${min.roundToInt()}°~${max.roundToInt()}°로 차이가 커요. 벗기 쉬운 겉옷을 챙기세요."
        } else if (min != null && thresholds.bandOf(min).ordinal < band.ordinal) {
            tips += "활동 시간 중 가장 추울 때는 체감 ${min.roundToInt()}°예요. 겉옷을 하나 더 챙기세요."
        }
        if (w.maxWind >= 9) tips += "바람이 강해요(최대 ${w.maxWind.roundToInt()}m/s). 바람막이가 도움이 돼요."
        return tips
    }

    fun uvLevel(uv: Double): String = when (uv.roundToInt()) {
        in Int.MIN_VALUE..2 -> "낮음"
        in 3..5 -> "보통"
        in 6..7 -> "높음"
        in 8..10 -> "매우 높음"
        else -> "위험"
    }
}
