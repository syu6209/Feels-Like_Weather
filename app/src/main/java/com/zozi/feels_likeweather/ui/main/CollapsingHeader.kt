package com.zozi.feels_likeweather.ui.main

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zozi.feels_likeweather.data.local.WeatherStore
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.model.weatherDescription
import com.zozi.feels_likeweather.ui.components.WeatherIcon
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * CollapsingToolbarLayout(exitUntilCollapsed) 와 같은 동작을 하는 중첩 스크롤 상태.
 * 위로 스크롤하면 목록보다 헤더가 먼저 접히고, 아래로 스크롤해 목록이 맨 위에 닿으면 헤더가 다시 펼쳐진다.
 */
@Stable
class CollapsingHeaderState(val maxHeightPx: Float, initialOffsetPx: Float = 0f) {
    var offsetPx by mutableFloatStateOf(initialOffsetPx.coerceIn(-maxHeightPx, 0f))
        private set

    /** 0 = 완전히 펼침, 1 = 완전히 접힘 */
    val collapsedFraction: Float get() = if (maxHeightPx == 0f) 0f else -offsetPx / maxHeightPx

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0) return Offset.Zero
            return Offset(0f, consume(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0) return Offset.Zero
            return Offset(0f, consume(available.y))
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    private fun consume(dy: Float): Float {
        val new = (offsetPx + dy).coerceIn(-maxHeightPx, 0f)
        val used = new - offsetPx
        offsetPx = new
        return used
    }

    /** 반쯤 접힌 상태로 멈추지 않도록 가까운 쪽으로 붙인다. */
    private suspend fun settle() {
        val f = collapsedFraction
        if (f <= 0f || f >= 1f) return
        val target = if (f > 0.5f) -maxHeightPx else 0f
        animate(offsetPx, target, animationSpec = tween(220)) { value, _ -> offsetPx = value }
    }
}

@Composable
fun rememberCollapsingHeaderState(maxHeightPx: Float): CollapsingHeaderState =
    // 다른 화면에 다녀와도 목록 스크롤 위치와 헤더 접힘 상태가 어긋나지 않도록 저장한다.
    rememberSaveable(
        maxHeightPx,
        saver = Saver(save = { it.offsetPx }, restore = { CollapsingHeaderState(maxHeightPx, it) }),
    ) { CollapsingHeaderState(maxHeightPx) }

/** 현재 날씨를 가운데에 가장 크게 보여주는 헤더 (애플·구글·삼성 날씨 스타일 종합). */
@Composable
fun CurrentWeatherHeader(
    forecast: WeatherForecast,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val c = forecast.current
    val today = forecast.today()
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(c.weatherCode, c.isDay, size = 76.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${c.temperature.roundToInt()}°",
                fontSize = 104.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White,
                lineHeight = 104.sp,
            )
        }
        Text(
            weatherDescription(c.weatherCode),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        if (today != null) {
            Text(
                "최고 ${today.temperatureMax.roundToInt()}° · 최저 ${today.temperatureMin.roundToInt()}°",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Text(
            "체감 ${c.apparentTemperature.roundToInt()}° · 습도 ${c.humidity}%",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
        // 저장된 날씨가 6시간 이상 지났으면(오프라인 등) 언제 받은 날씨인지 작게 알려준다.
        val fetchedAt = forecast.fetchedAtMillis
        if (nowMillis - fetchedAt >= WeatherStore.SHOW_BASIS_AFTER_MILLIS) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Instant.ofEpochMilli(fetchedAt).atOffset(forecast.zoneOffset).format(BasisFormat)} 기준 날씨",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

private val BasisFormat = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
