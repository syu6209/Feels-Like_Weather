package com.zozi.feels_likeweather.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Season
import com.zozi.feels_likeweather.domain.SurveyRecord
import com.zozi.feels_likeweather.ui.components.BandGraph
import com.zozi.feels_likeweather.ui.theme.composeColor
import java.time.LocalDate

/** 체감 구간 산정 방법: 기본 체감 그래프와 내 체감 그래프. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeelsInfoSheet(
    myThresholds: FeelsThresholds,
    surveys: List<SurveyRecord>,
    currentApparent: Double?,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val surveyCount = surveys.count { !it.isDeleted }
    val season = Season.of(today)
    val inSeason = FeelsLearner.inSeasonCount(surveys, today)
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
            Text("체감 구간은 이렇게 정했어요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "체감온도(Open-Meteo 'apparent temperature': 기온·습도·바람·일사를 반영)를 기준으로 " +
                    "7개 구간을 추운 순서대로 나눴어요. 분기점은 아래 공신력 있는 지표를 함께 참고했어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text("기본 체감 그래프", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "설문 데이터가 없는 기본값",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            BandGraph(FeelsThresholds.DEFAULT, marker = currentApparent)

            Spacer(Modifier.height(16.dp))
            Text("내 체감 그래프 · 지금(${season.label}) 기준", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (surveyCount == 0) "아직 설문 응답이 없어 기본값과 같아요. 체감 설문에 답하면 내 체감에 맞게 바뀌어요."
                else "응답 ${surveyCount}개 중 지금과 비슷한 시기의 응답 ${inSeason}개를 중심으로 반영했어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            BandGraph(myThresholds, marker = currentApparent)

            Spacer(Modifier.height(16.dp))
            BandTable(myThresholds)

            if (surveyCount > 0) {
                Spacer(Modifier.height(20.dp))
                Text("계절별 내 체감", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "같은 온도도 계절에 따라 다르게 느껴져서, 계절마다 내 체감 구간을 따로 계산해요. " +
                        "그 계절 응답이 없으면 다른 계절 응답으로 만든 평소 기준을 써요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val seasonal = remember(surveys, today.year) { FeelsLearner.seasonalEstimates(surveys, today.year) }
                Season.entries.forEach { s ->
                    val count = FeelsLearner.inSeasonCount(surveys, s.representative.atYear(today.year))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${s.label}${if (s == season) " (지금)" else ""} · 비슷한 시기 응답 ${count}개",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (s == season) FontWeight.Bold else FontWeight.Normal,
                        color = if (s == season) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    BandGraph(seasonal.getValue(s))
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("분기점 근거", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Evidence.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("내 체감 보정 방식", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "모든 설문 응답을 함께 보고, 각 분기점에서 '그 온도 이상으로 느꼈는지'를 가장 잘 설명하는 온도를 찾아요. " +
                    "응답이 적을 땐 기본값 근처에 머물고, 쌓일수록 내 응답 쪽으로 옮겨가요. " +
                    "예) 18°가 '선선함'인데 '따뜻했음'을 고르면 '따뜻함' 시작 온도가 20° → 17°로 낮아져요. " +
                    "예보와 같은 구간을 고른 응답도 지금 기준이 맞다는 근거가 돼요.\n\n" +
                    "계절 적응: 지금과 날짜가 가까운 응답일수록 크게 반영해요(같은 시기 100%, 한 달 차이 약 65%, 75일 이상 0%). " +
                    "다른 계절 응답은 '평소 기준'으로 쓰여, 새 계절이 시작돼도 기본값이 아니라 내 평소 기준에서 출발해요.\n\n" +
                    "오래된 응답: 시간이 지났다고 잊지 않아요. 대신 응답이 쌓이면 최신 30개는 그대로, 그보다 오래된 응답은 점점 줄어 " +
                    "90번째부터는 반영하지 않아요. 계절별로 따로 세서 겨울 응답이 많아도 지난여름 응답이 밀려나지 않아요.\n\n" +
                    "기록을 지우거나 되돌리면 그 응답을 빼거나 넣고 다시 계산해서, 되돌리면 정확히 원래대로 돌아와요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BandTable(thresholds: FeelsThresholds) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FeelsBand.entries.reversed().forEach { band ->
            val low = thresholds.lowerBoundOf(band)
            val high = thresholds.upperBoundOf(band)
            val range = when {
                low == null -> "${fmt(high!!)} 미만"
                high == null -> "${fmt(low)} 이상"
                else -> "${fmt(low)} ~ ${fmt(high)}"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(band.composeColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(band.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(range, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun fmt(t: Double): String =
    if (t == Math.rint(t)) "${t.toInt()}°" else "%.1f°".format(t)

private val Evidence = listOf(
    "-10° · 강추위 / 추움" to "기상청 겨울철 체감온도 단계에서 체감 -10℃ 미만부터 '주의' 단계예요. 노출된 피부가 차가워지고, 보호 없이 오래 있으면 저체온 위험이 있어요.",
    "4° · 추움 / 쌀쌀함" to "국내에서 널리 쓰이는 '기온별 옷차림' 지표에서 4℃ 이하는 패딩·두꺼운 코트 구간이에요. " +
        "UTCI(국제 열쾌적 지수) 기준으로도 0~9℃는 '약한 추위 스트레스'예요.",
    "12° · 쌀쌀함 / 선선함" to "기온별 옷차림 지표에서 9~11℃는 트렌치코트, 12~16℃는 재킷·가디건으로 겉옷이 가벼워지는 지점이에요.",
    "20° · 선선함 / 따뜻함" to "기온별 옷차림 지표에서 20℃부터 얇은 긴팔·가디건 차림이 돼요. UTCI 9~26℃ '열 스트레스 없음' 구간의 중간이에요.",
    "26° · 따뜻함 / 더움" to "UTCI에서 26℃부터 '중간 더위 스트레스'로 분류돼요.",
    "32° · 더움 / 무더움" to "UTCI에서 32℃부터 '강한 더위 스트레스'예요. 기상청 폭염주의보 기준(일 최고체감온도 33℃ 이상)과도 가까워요.",
)
