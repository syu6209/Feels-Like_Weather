package com.zozi.feels_likeweather.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.zozi.feels_likeweather.domain.FeelsBand
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** 위젯 색. 배경 불투명도와 글자색 설정을 반영한다. */
class WidgetColors(settings: WidgetSettings) {
    private val alpha = settings.backgroundOpacity.coerceIn(0f, 1f)

    val background: ColorProvider = DayNightColorProvider(
        day = Color(0xFFFFFFFF).copy(alpha = alpha),
        night = Color(0xFF1E1F24).copy(alpha = alpha),
    )

    val text: ColorProvider = when (settings.textMode) {
        WidgetTextMode.AUTO -> DayNightColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFE6E1E5))
        WidgetTextMode.WHITE -> ColorProvider(Color.White)
        WidgetTextMode.BLACK -> ColorProvider(Color(0xFF1B1B1F))
    }

    val subText: ColorProvider = when (settings.textMode) {
        WidgetTextMode.AUTO -> DayNightColorProvider(day = Color(0xFF5F5B66), night = Color(0xFFC9C4CF))
        WidgetTextMode.WHITE -> ColorProvider(Color.White.copy(alpha = 0.8f))
        WidgetTextMode.BLACK -> ColorProvider(Color(0xFF1B1B1F).copy(alpha = 0.7f))
    }

    val accent: ColorProvider = DayNightColorProvider(day = Color(0xFF6650A4), night = Color(0xFFD0BCFF))
}

fun FeelsBand.widgetColor(): ColorProvider = ColorProvider(Color(color))

/** 대표색 위 글자색 (밝은 대표색에는 어두운 글자). */
fun FeelsBand.widgetOnColor(): ColorProvider = ColorProvider(
    when (this) {
        FeelsBand.CHILLY, FeelsBand.WARM -> Color(0xFF1B1B1F)
        else -> Color.White
    }
)
