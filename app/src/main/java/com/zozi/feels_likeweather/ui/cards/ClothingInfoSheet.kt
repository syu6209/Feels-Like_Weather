package com.zozi.feels_likeweather.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingRecommendation
import com.zozi.feels_likeweather.domain.Profile
import kotlin.math.roundToInt

/** 옷차림 추천 근거: 지금 보이는 추천이 어떻게 만들어졌는지와 연령대·날씨 아이템 기준. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingInfoSheet(
    profile: Profile,
    recommendation: ClothingRecommendation,
    usesMyBands: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("옷차림은 이렇게 추천했어요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            val plan = recommendation.plan
            Section("1. 활동 시간의 시간별 체감")
            Body(
                "${profile.name.ifBlank { profile.ageGroup.label }} 프로필의 활동 시간(${recommendation.window.label})을 " +
                    "한 시간씩 보고 체감 구간을 정했어요. " +
                    if (usesMyBands) "'내 기준'은 체감 설문으로 보정된 내 체감 구간을 쓰고, 직접 응답한 시간은 응답한 구간을 그대로 써요."
                    else "이 프로필은 기본 체감 구간을 써요."
            )
            if (plan != null) {
                Spacer(Modifier.height(6.dp))
                Fact("흐름", plan.timeline)
                Fact(
                    "체감",
                    "최저 ${plan.minApparent.roundToInt()}° · 평균 ${plan.meanApparent.roundToInt()}° · 최고 ${plan.maxApparent.roundToInt()}°",
                )
                Fact("하루 모양", plan.shape.label)
            }

            Spacer(Modifier.height(16.dp))
            Section("2. 평균 대신 구간 흐름으로 고르기")
            Body(
                "평균만 보면 아침엔 쌀쌀하고 낮엔 따뜻한 날이 어중간한 구간 하나로 뭉개져요. 그래서 옷을 층별로 나눠 골라요."
            )
            Spacer(Modifier.height(6.dp))
            Fact("상의·하의·신발", "가장 오래 머무는 구간 기준 (중간에 바꿔 입기 어려워요)")
            Fact("겉옷·장갑·목도리", "가장 추운 구간 기준. 구간이 바뀌면 '벗기 쉬운 겉옷'으로 안내해요")
            Fact("더울 땐 이것만", "가장 더운 구간이 더 덥다면 그 구간의 가벼운 상의를 함께 보여줘요")
            Fact("하루 모양", "갈수록 따뜻해짐 · 갈수록 서늘해짐 · 한낮에 가장 따뜻함 · 중간에 서늘해짐 · 여러 번 오르내림에 따라 안내 문구가 달라져요")
            Fact("일교차", "구간이 2단계 이상 바뀌거나 체감이 10° 이상 차이 나면 얇은 옷 여러 겹을 권해요")
            Fact("잠깐 튐", "앞뒤가 같은 구간 사이의 1시간짜리 변화는 일시적인 것으로 보고 무시해요")
            if (plan != null) {
                Spacer(Modifier.height(6.dp))
                Body(
                    "이번 추천: 기본 옷은 '${plan.coreBand.label}', 겉옷은 '${plan.coldBand.label}' 기준" +
                        if (plan.warmBand != plan.coreBand) ", 가장 더울 때는 '${plan.warmBand.label}'" else ""
                )
            }

            Spacer(Modifier.height(16.dp))
            Section("3. 연령대별 기준")
            AgeEvidence.forEach { (age, text) ->
                Fact(age.label + if (age == profile.ageGroup) " (선택됨)" else "", text)
            }

            Spacer(Modifier.height(16.dp))
            Section("4. 날씨 아이템")
            Body("필요한 아이템은 목록 맨 위에, 필요 없는 아이템은 아래에 흐리게 보여줘요.")
            Spacer(Modifier.height(6.dp))
            WeatherItemRules.forEach { (item, rule) -> Fact(item, rule) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "출처: 기상청 체감온도·폭염특보 기준, UTCI 열 스트레스 등급, 국내 기온별 옷차림 지표, " +
                    "미국소아과학회(AAP) HealthyChildren.org, 미국 질병통제예방센터(CDC) 고령층 체온 관리 안내, WHO 자외선 지수. " +
                    "공식 연령대별 옷차림 기준은 없어서 위 자료를 조합한 참고용 안내예요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AgeEvidence = listOf(
    AgeGroup.ADULT to "국내 기온별 옷차림 지표(28°↑ 민소매·반팔 … 4°↓ 패딩)를 7개 체감 구간에 맞춰 재구성했어요.",
    AgeGroup.INFANT to "미국소아과학회: 같은 환경의 어른보다 한 겹 더. 목도리 대신 넥워머, 6개월 미만은 선크림보다 그늘·모자·긴소매.",
    AgeGroup.CHILD to "활동량이 많아 땀이 나기 쉬워요. 벗고 입기 쉬운 겉옷 위주로, 비 오는 날엔 우비를 권해요.",
    AgeGroup.SENIOR to "CDC: 나이가 들수록 체온 조절과 추위·더위 감각이 둔해져요. 추울 땐 내의·모자, 더울 땐 통풍과 수분.",
)

private val WeatherItemRules = listOf(
    "우산" to "활동 시간 최대 강수확률 40% 이상, 강수량 0.5mm 이상, 또는 비 예보",
    "우비" to "유아·아동, 우산이 필요할 때",
    "장화" to "강한 비(누적 10mm 이상) 또는 눈",
    "모자·선크림" to "자외선 지수 3 이상(WHO: 차단 필요), 모자는 더운 날에도",
    "선글라스" to "자외선 지수 6 이상(WHO: 높음), 유아 제외",
    "물병" to "가장 더울 때 '더움' 이상",
    "핫팩" to "가장 추울 때 '추움' 이하",
)

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
