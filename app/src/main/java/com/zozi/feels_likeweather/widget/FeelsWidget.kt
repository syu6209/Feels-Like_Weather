package com.zozi.feels_likeweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.ui.main.SlotUi
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * 체감 보정 예보 위젯.
 * 크기에 따라 체감온도·구간 → 브리핑 → 설문(설정에서 켜고 끔) → 다음 시간대 순으로 더 많이 보여준다.
 */
class FeelsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.feels(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val settings = WidgetSettings.from(prefs)
            val version = prefs[WidgetSettings.VERSION] ?: 0
            val ctx = LocalContext.current
            val model by produceState(initial, version) { value = WidgetData.feels(ctx) }
            FeelsContent(model, settings)
        }
    }
}

class FeelsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FeelsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        runBlocking { WidgetUpdater.cancelIfNoWidgets(context) }
    }
}

@Composable
private fun FeelsContent(model: FeelsWidgetModel?, settings: WidgetSettings) {
    val colors = WidgetColors(settings)
    val size = LocalSize.current
    WidgetFrame(colors, compact = size.isShort()) {
        if (model == null) {
            EmptyState(colors)
            return@WidgetFrame
        }
        val now = model.current
        when {
            size.isShort() -> CompactRow(model, colors, showBriefing = size.isWide())
            // 2×2: 세로로 체감온도 → 구간 → 실제 기온
            !size.isWide() && !size.isTall() -> NarrowHeader(model, colors)
            // 2×3 이상 좁은 위젯: 설문을 켜면 한 줄 머리 + 세로 버튼, 끄면 브리핑
            !size.isWide() -> Column(GlanceModifier.fillMaxSize()) {
                if (settings.showSurvey) {
                    // 오른쪽 위 설정 버튼 옆에는 체감온도만, 구간은 다음 줄에
                    Text(
                        "${now.apparent.roundToInt()}°",
                        style = TextStyle(color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    )
                    BandChip(now.displayBand, fontSize = 11.sp)
                    Spacer(GlanceModifier.height(6.dp))
                    Survey(now, model.answer, colors, vertical = true)
                } else {
                    NarrowHeader(model, colors)
                    Spacer(GlanceModifier.height(6.dp))
                    Briefing(model, colors, maxLines = 5)
                }
            }
            // 4×2: 왼쪽 체감온도, 오른쪽 브리핑
            !size.isTall() -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                NarrowHeader(model, colors, modifier = GlanceModifier.width(96.dp))
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(model.placeName, style = TextStyle(color = colors.subText, fontSize = 11.sp), maxLines = 1)
                    Briefing(model, colors, maxLines = 4)
                }
            }
            else -> Column(GlanceModifier.fillMaxSize()) {
                Header(model, colors, tempSize = 44)
                Spacer(GlanceModifier.height(4.dp))
                Briefing(model, colors, maxLines = 3)
                if (size.isXLarge()) {
                    Spacer(GlanceModifier.height(10.dp))
                    UpcomingSlots(model.upcoming.take(6), colors)
                }
                if (size.isTall()) {
                    Spacer(GlanceModifier.height(10.dp))
                    if (settings.showSurvey) {
                        Survey(now, model.answer, colors, vertical = false)
                    } else if (!size.isXLarge()) {
                        UpcomingSlots(model.upcoming.take(4), colors)
                    }
                }
            }
        }
    }
}

/** 한 줄 크기(2×1, 4×1): 체감온도 + 구간 (+ 넓으면 브리핑). */
@Composable
private fun CompactRow(model: FeelsWidgetModel, colors: WidgetColors, showBriefing: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${model.current.apparent.roundToInt()}°",
            style = TextStyle(color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(6.dp))
        BandChip(model.current.displayBand, fontSize = 11.sp)
        if (showBriefing) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                model.briefing.text,
                style = TextStyle(color = colors.subText, fontSize = 12.sp),
                maxLines = 2,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** 좁은 위젯 머리: 체감온도 아래로 구간과 실제 기온. */
@Composable
private fun NarrowHeader(model: FeelsWidgetModel, colors: WidgetColors, modifier: GlanceModifier = GlanceModifier) {
    val now = model.current
    Column(modifier) {
        Text(
            "${now.apparent.roundToInt()}°",
            style = TextStyle(color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Medium),
        )
        BandChip(now.displayBand)
        Spacer(GlanceModifier.height(2.dp))
        Text(
            "체감 · 실제 ${now.temperature.roundToInt()}°",
            style = TextStyle(color = colors.subText, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Header(model: FeelsWidgetModel, colors: WidgetColors, tempSize: Int) {
    val now = model.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${now.apparent.roundToInt()}°",
            style = TextStyle(color = colors.text, fontSize = tempSize.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column {
            BandChip(now.displayBand)
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "체감 · 실제 ${now.temperature.roundToInt()}°",
                style = TextStyle(color = colors.subText, fontSize = 11.sp),
                maxLines = 1,
            )
            Text(
                model.placeName,
                style = TextStyle(color = colors.subText, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Briefing(model: FeelsWidgetModel, colors: WidgetColors, maxLines: Int) {
    Text(
        model.briefing.text,
        style = TextStyle(color = colors.text, fontSize = 13.sp),
        maxLines = maxLines,
    )
}

/**
 * 지금 시간대 체감 설문. 예보(또는 내 응답) 구간을 가운데에 두고 한 단계 춥게/덥게를 함께 보여준다.
 * 누르면 바로 저장되고 앱의 내 체감 구간과 모든 위젯에 반영된다.
 */
@Composable
private fun Survey(slot: SlotUi, answer: FeelsBand?, colors: WidgetColors, vertical: Boolean) {
    val center = (answer ?: slot.myBand).ordinal.coerceIn(1, FeelsBand.entries.size - 2)
    val options = FeelsBand.entries.subList(center - 1, center + 2)
    Column(GlanceModifier.fillMaxWidth()) {
        Text(
            when {
                answer != null && vertical -> "응답했어요"
                answer != null -> "응답했어요 · 다시 고를 수 있어요"
                vertical -> "지금 어때요?"
                else -> "지금 실외는 어떻게 느끼시나요?"
            },
            style = TextStyle(color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        if (vertical) {
            options.forEach { band ->
                SurveyButton(band, slot, chosen = band == answer, colors, GlanceModifier.fillMaxWidth().height(28.dp))
                Spacer(GlanceModifier.height(4.dp))
            }
        } else {
            Row(GlanceModifier.fillMaxWidth()) {
                options.forEachIndexed { i, band ->
                    SurveyButton(band, slot, chosen = band == answer, colors, GlanceModifier.defaultWeight().height(36.dp))
                    if (i < options.lastIndex) Spacer(GlanceModifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SurveyButton(band: FeelsBand, slot: SlotUi, chosen: Boolean, colors: WidgetColors, modifier: GlanceModifier) {
    val predicted = slot.predictedAtAnswer ?: slot.myBand
    Box(
        modifier
            .let { if (chosen) it.roundedBackground(band.widgetColor(), WidgetShape.BUTTON) else it.containerBackground(WidgetShape.BUTTON) }
            .clickable(
                actionRunCallback<SurveyAnswerAction>(
                    actionParametersOf(
                        SurveyAnswerAction.SLOT_KEY to slot.slot.key,
                        SurveyAnswerAction.APPARENT to slot.apparent,
                        SurveyAnswerAction.REPORTED to band.ordinal,
                        SurveyAnswerAction.PREDICTED to predicted.ordinal,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (chosen) "${band.surveyLabel} ✓" else band.surveyLabel,
            style = TextStyle(
                color = if (chosen) band.widgetOnColor() else colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/** 다음 시간대들의 체감 (큰 위젯). */
@Composable
private fun UpcomingSlots(slots: List<SlotUi>, colors: WidgetColors) {
    if (slots.isEmpty()) return
    Row(GlanceModifier.fillMaxWidth()) {
        slots.forEach { s ->
            Column(
                GlanceModifier
                    .defaultWeight()
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.slot.dayLabel,
                    style = TextStyle(color = colors.subText, fontSize = 10.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                Text(
                    s.slot.periodName ?: "${s.slot.clockHour}시",
                    style = TextStyle(color = colors.text, fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                Text(
                    "${s.apparent.roundToInt()}°",
                    style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Box(
                    GlanceModifier
                        .size(width = 28.dp, height = 4.dp)
                        .roundedBackground(s.displayBand.widgetColor(), WidgetShape.PILL),
                ) {}
            }
        }
    }
}
