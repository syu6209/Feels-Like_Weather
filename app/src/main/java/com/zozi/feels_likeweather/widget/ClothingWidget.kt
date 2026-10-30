package com.zozi.feels_likeweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingLayer
import com.zozi.feels_likeweather.domain.layer
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * 옷차림 추천 위젯. 위젯 설정에서 고른 프로필의 다가오는 활동 시간 기준으로 추천한다.
 * 크기에 따라 구간 → 아이템 이미지 → 겹쳐 입기 안내 → 체감 통계 순으로 더 많이 보여준다.
 */
class ClothingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialSettings = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, id))
        val initial = WidgetData.clothing(context, initialSettings.profileId)
        provideContent {
            val prefs = currentState<Preferences>()
            val settings = WidgetSettings.from(prefs)
            val version = prefs[WidgetSettings.VERSION] ?: 0
            val ctx = LocalContext.current
            val model by produceState(initial, version, settings.profileId) {
                value = WidgetData.clothing(ctx, settings.profileId)
            }
            ClothingContent(model, settings)
        }
    }
}

class ClothingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClothingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        runBlocking { WidgetUpdater.cancelIfNoWidgets(context) }
    }
}

private data class WidgetItem(val item: ClothingItem, val caption: String?, val needed: Boolean)

/**
 * 위젯은 공간이 좁아서, 필요한 날씨 아이템은 "챙길 것" 한 줄로 따로 보여주고
 * 이미지 격자에는 옷을 겉옷 → 상의 → 하의 → 신발 → 소품 순으로 보여준다.
 */
private val LayerOrder = listOf(
    ClothingLayer.OUTER, ClothingLayer.TOP, ClothingLayer.INNER, ClothingLayer.BOTTOM, ClothingLayer.SHOES, ClothingLayer.ACCESSORY,
)

@Composable
private fun ClothingContent(model: ClothingWidgetModel?, settings: WidgetSettings) {
    val colors = WidgetColors(settings)
    val size = LocalSize.current
    WidgetFrame(colors, compact = size.isShort()) {
        if (model == null) {
            EmptyState(colors)
            return@WidgetFrame
        }
        val rec = model.recommendation
        val clothes = rec.items
            .sortedBy { LayerOrder.indexOf(it.item.layer) }
            .map { WidgetItem(it.item, it.note, false) }
        val carry = rec.conditionalItems.filter { it.needed }.map { it.item.label }
        val age = model.profile.ageGroup

        when {
            size.isShort() -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BandChip(rec.band, fontSize = 11.sp)
                Spacer(GlanceModifier.width(6.dp))
                if (size.isWide()) {
                    clothes.take(5).forEach { ItemImage(it.item, age, 34.dp, colors); Spacer(GlanceModifier.width(4.dp)) }
                    if (carry.isNotEmpty()) {
                        Text(
                            "+ " + carry.joinToString(" · "),
                            style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                } else {
                    Text(
                        clothes.take(2).joinToString(", ") { it.item.label },
                        style = TextStyle(color = colors.text, fontSize = 12.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
            !size.isWide() -> Column(GlanceModifier.fillMaxSize()) {
                NarrowHeader(model, colors, showWindow = size.isTall())
                Spacer(GlanceModifier.height(4.dp))
                if (size.isTall()) {
                    Carry(carry, colors)
                    ItemGrid(clothes, age, columns = 2, rows = 2, imageSize = 38.dp, colors, showLabels = true)
                } else {
                    ItemGrid(clothes, age, columns = 2, rows = 1, imageSize = 36.dp, colors, showLabels = false)
                }
            }
            else -> Column(GlanceModifier.fillMaxSize()) {
                Header(model, colors, carryInline = if (size.isTall()) emptyList() else carry)
                if (size.isXLarge()) {
                    rec.plan?.let { plan ->
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            "체감 ${plan.minApparent.roundToInt()}°~${plan.maxApparent.roundToInt()}° (평균 ${plan.meanApparent.roundToInt()}°) · ${plan.timeline}",
                            style = TextStyle(color = colors.subText, fontSize = 11.sp),
                            maxLines = 2,
                        )
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                if (size.isTall()) {
                    Carry(carry, colors)
                    rec.plan?.headline?.let {
                        Text(
                            it,
                            style = TextStyle(color = colors.text, fontSize = 12.sp),
                            maxLines = if (size.isXLarge()) 3 else 2,
                        )
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
                val rows = when {
                    size.isXLarge() -> 2
                    else -> 1
                }
                ItemGrid(
                    clothes, age, columns = 5, rows = rows,
                    imageSize = if (size.isXLarge()) 46.dp else 40.dp, colors, showLabels = size.isTall(),
                )
            }
        }
    }
}

/** 필요한 날씨 아이템 한 줄. */
@Composable
private fun Carry(carry: List<String>, colors: WidgetColors) {
    if (carry.isEmpty()) return
    Text(
        "챙길 것: " + carry.joinToString(" · "),
        style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    Spacer(GlanceModifier.height(4.dp))
}

/** 좁은 위젯 머리: 구간(설정 버튼 옆) → 프로필 → (세로로 길면) 활동 시간. */
@Composable
private fun NarrowHeader(model: ClothingWidgetModel, colors: WidgetColors, showWindow: Boolean) {
    BandChip(model.recommendation.band, fontSize = 11.sp)
    Text(
        model.profile.name.ifBlank { model.profile.ageGroup.label },
        style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    if (showWindow) {
        Text(model.recommendation.window.label, style = TextStyle(color = colors.subText, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun Header(model: ClothingWidgetModel, colors: WidgetColors, carryInline: List<String> = emptyList()) {
    val rec = model.recommendation
    val plan = rec.plan
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (plan != null && plan.bandSpan > 0) {
            BandChip(plan.coldBand)
            Text(" ~ ", style = TextStyle(color = colors.subText, fontSize = 12.sp))
            BandChip(plan.warmBand)
        } else {
            BandChip(rec.band)
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            model.profile.name.ifBlank { model.profile.ageGroup.label },
            style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
    if (carryInline.isEmpty()) {
        Text(
            "${rec.window.label} · ${model.placeName}",
            style = TextStyle(color = colors.subText, fontSize = 11.sp),
            maxLines = 1,
        )
    } else {
        // 4×2 처럼 높이가 낮을 땐 지역 대신 챙길 것을 같은 줄에
        Row {
            Text("${rec.window.label} · ", style = TextStyle(color = colors.subText, fontSize = 11.sp), maxLines = 1)
            Text(
                "챙길 것: " + carryInline.joinToString(" · "),
                style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/** 아이템 격자. 칸 수를 넘는 아이템은 앱에서 볼 수 있다. */
@Composable
private fun ItemGrid(
    items: List<WidgetItem>,
    ageGroup: AgeGroup,
    columns: Int,
    rows: Int,
    imageSize: Dp,
    colors: WidgetColors,
    showLabels: Boolean,
) {
    val shown = items.take(columns * rows)
    Column(GlanceModifier.fillMaxWidth()) {
        shown.chunked(columns).forEach { row ->
            Row(GlanceModifier.fillMaxWidth()) {
                row.forEach { w ->
                    Column(
                        GlanceModifier.defaultWeight().padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ItemImage(w.item, ageGroup, imageSize, colors, highlighted = w.needed)
                        if (showLabels) {
                            Text(
                                w.item.label,
                                style = TextStyle(color = colors.text, fontSize = 10.sp, textAlign = TextAlign.Center),
                                maxLines = 1,
                            )
                        }
                    }
                }
                // 마지막 줄 빈칸을 채워 열 너비를 맞춘다.
                repeat(columns - row.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun ItemImage(item: ClothingItem, ageGroup: AgeGroup, size: Dp, colors: WidgetColors, highlighted: Boolean = false) {
    val context = LocalContext.current
    val res = clothingImageRes(context, item, ageGroup)
    Box(
        GlanceModifier
            .size(size)
            .let { if (highlighted) it.containerBackground(WidgetShape.TILE) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) {
            Image(ImageProvider(res), contentDescription = item.label, modifier = GlanceModifier.size(size - 4.dp).cornerRadius(8.dp))
        } else {
            Text(item.label.take(2), style = TextStyle(color = colors.text, fontSize = 10.sp))
        }
    }
}
