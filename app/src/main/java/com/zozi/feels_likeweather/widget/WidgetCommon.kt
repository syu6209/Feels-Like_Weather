package com.zozi.feels_likeweather.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zozi.feels_likeweather.MainActivity
import com.zozi.feels_likeweather.R
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.FeelsBand

/** 지원하는 위젯 크기(반응형 기준점). 런처 칸 수는 대략적인 값이다. */
object WidgetSizes {
    val SMALL = DpSize(110.dp, 48.dp)   // 2×1
    val SQUARE = DpSize(110.dp, 110.dp) // 2×2
    val TALL = DpSize(110.dp, 200.dp)   // 2×3 ~ 2×4
    val WIDE = DpSize(250.dp, 48.dp)    // 4×1
    val MEDIUM = DpSize(250.dp, 110.dp) // 4×2
    val LARGE = DpSize(250.dp, 200.dp)  // 4×3
    val XLARGE = DpSize(320.dp, 280.dp) // 5×4 이상, 태블릿

    val all = setOf(SMALL, SQUARE, TALL, WIDE, MEDIUM, LARGE, XLARGE)
}

/** 위젯 안쪽 둥근 모양. */
enum class WidgetShape(
    val radius: Dp,
    @param:DrawableRes val solid: Int,
    @param:DrawableRes val container: Int? = null,
) {
    BUTTON(12.dp, R.drawable.widget_round_12, R.drawable.widget_container_12),
    TILE(10.dp, R.drawable.widget_round_10, R.drawable.widget_container_10),
    PILL(999.dp, R.drawable.widget_round_pill),
}

/**
 * 불투명한 색의 둥근 배경. 흰 도형에 색을 입혀 안드로이드 12 미만에서도 둥글게 그린다.
 * (Glance 의 틴트는 색 필터라 투명도를 버리므로 반투명 색에는 쓰지 않는다.)
 */
fun GlanceModifier.roundedBackground(color: ColorProvider, shape: WidgetShape): GlanceModifier =
    cornerRadius(shape.radius).background(ImageProvider(shape.solid), colorFilter = ColorFilter.tint(color))

/** 버튼·칸의 반투명 둥근 배경 (낮/밤 색 리소스 @color/widget_container). */
fun GlanceModifier.containerBackground(shape: WidgetShape): GlanceModifier =
    cornerRadius(shape.radius).background(ImageProvider(requireNotNull(shape.container)))

/**
 * 위젯 공통 틀: 설정한 불투명도의 둥근 배경, 누르면 앱 열기.
 * 위젯 설정은 런처에서 위젯을 길게 눌러 여는 메뉴로 연다(위젯 정보의 reconfigurable).
 */
@Composable
fun WidgetFrame(
    colors: WidgetColors,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(colors.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp),
    ) {
        content()
    }
}

@Composable
fun BandChip(band: FeelsBand, fontSize: TextUnit = 12.sp) {
    val vertical = if (fontSize.value < 12f) 1.dp else 2.dp
    Box(
        GlanceModifier
            .roundedBackground(band.widgetColor(), WidgetShape.PILL)
            .padding(horizontal = 8.dp, vertical = vertical),
    ) {
        Text(
            band.label,
            style = TextStyle(color = band.widgetOnColor(), fontSize = fontSize, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(colors: WidgetColors) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "앱을 한 번 열어 날씨를 불러와 주세요",
            style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** 옷 이미지 리소스 (앱 카드와 같은 규칙). 없으면 null. */
@SuppressLint("DiscouragedApi")
fun clothingImageRes(context: Context, item: ClothingItem, ageGroup: AgeGroup): Int? {
    val res = context.resources
    return listOf("cloth_${ageGroup.name.lowercase()}_${item.key}", "cloth_${item.key}")
        .firstNotNullOfOrNull { name -> res.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 } }
}

/** 크기 판단 도우미. */
fun DpSize.isShort(): Boolean = height < 80.dp
fun DpSize.isWide(): Boolean = width >= 240.dp
fun DpSize.isTall(): Boolean = height >= 190.dp
fun DpSize.isXLarge(): Boolean = width >= 300.dp && height >= 260.dp

