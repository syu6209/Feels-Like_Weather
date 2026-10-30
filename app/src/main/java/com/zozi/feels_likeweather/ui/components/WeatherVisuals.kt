package com.zozi.feels_likeweather.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.zozi.feels_likeweather.data.model.WeatherKind
import com.zozi.feels_likeweather.data.model.weatherDescription
import com.zozi.feels_likeweather.data.model.weatherKindOf

fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (weatherKindOf(code)) {
    WeatherKind.CLEAR -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    WeatherKind.PARTLY_CLOUDY -> if (isDay) Icons.Filled.WbCloudy else Icons.Filled.NightsStay
    WeatherKind.CLOUDY -> Icons.Filled.Cloud
    WeatherKind.FOG -> Icons.Filled.Dehaze
    WeatherKind.DRIZZLE -> Icons.Filled.Grain
    WeatherKind.RAIN -> Icons.Filled.Umbrella
    WeatherKind.SNOW -> Icons.Filled.AcUnit
    WeatherKind.THUNDER -> Icons.Filled.Thunderstorm
}

fun weatherIconTint(code: Int, isDay: Boolean): Color = when (weatherKindOf(code)) {
    WeatherKind.CLEAR -> if (isDay) Color(0xFFFFC233) else Color(0xFFE6E1A8)
    WeatherKind.PARTLY_CLOUDY -> if (isDay) Color(0xFFFFD36E) else Color(0xFFE6E1A8)
    WeatherKind.CLOUDY, WeatherKind.FOG -> Color(0xFF9EA9B8)
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> Color(0xFF5AA9F0)
    WeatherKind.SNOW -> Color(0xFF9FD3F5)
    WeatherKind.THUNDER -> Color(0xFF8E7CC3)
}

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp = 24.dp, tint: Color? = null, modifier: Modifier = Modifier) {
    Icon(
        imageVector = weatherIcon(code, isDay),
        contentDescription = weatherDescription(code),
        tint = tint ?: weatherIconTint(code, isDay),
        modifier = modifier.size(size),
    )
}

/** 애플·구글 날씨처럼 날씨/낮밤에 따라 바뀌는 하늘 배경. */
fun skyBrush(code: Int, isDay: Boolean): Brush {
    val colors = when (weatherKindOf(code)) {
        WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY ->
            if (isDay) listOf(Color(0xFF2F80ED), Color(0xFF56CCF2), Color(0xFF9ED8F5))
            else listOf(Color(0xFF0B1437), Color(0xFF1E2A5A), Color(0xFF3A4A7A))

        WeatherKind.CLOUDY, WeatherKind.FOG ->
            if (isDay) listOf(Color(0xFF5D7186), Color(0xFF8396AA), Color(0xFFB2C0CE))
            else listOf(Color(0xFF1F2733), Color(0xFF39444F), Color(0xFF55606B))

        WeatherKind.DRIZZLE, WeatherKind.RAIN ->
            if (isDay) listOf(Color(0xFF3E4F66), Color(0xFF5C7089), Color(0xFF8396AA))
            else listOf(Color(0xFF141C27), Color(0xFF263241), Color(0xFF3B4859))

        WeatherKind.SNOW ->
            if (isDay) listOf(Color(0xFF7C9CBF), Color(0xFFA9C1DA), Color(0xFFD9E5F1))
            else listOf(Color(0xFF26354A), Color(0xFF41546C), Color(0xFF64788F))

        WeatherKind.THUNDER -> listOf(Color(0xFF20232E), Color(0xFF3A3F55), Color(0xFF5B5F78))
    }
    return Brush.verticalGradient(colors)
}

/** 화면별 상태바 아이콘 색상 지정. [lightIcons] 가 true 면 흰 아이콘(어두운 배경용). */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !lightIcons
            controller.isAppearanceLightNavigationBars = !lightIcons
        }
        onDispose { }
    }
}

/** 일반(테마 배경) 화면용: 배경 밝기에 맞춰 상태바 아이콘 색을 정한다. */
@Composable
fun ThemedSystemBarIcons() {
    SystemBarIcons(lightIcons = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
}
