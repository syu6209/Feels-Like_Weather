package com.zozi.feels_likeweather.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.TimeSlot
import com.zozi.feels_likeweather.ui.components.WeatherIcon
import com.zozi.feels_likeweather.ui.components.HorizontalListMargin
import com.zozi.feels_likeweather.ui.components.horizontalFadingEdges
import com.zozi.feels_likeweather.ui.theme.composeColor
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val RainBlue = Color(0xFF3A8EE6)

@Composable
fun HourlyForecastCard(forecast: WeatherForecast, modifier: Modifier = Modifier) {
    val now = forecast.localNow().withMinute(0).withSecond(0).withNano(0)
    val hours = forecast.hourly.filter { !it.time.isBefore(now) }.take(25)
    WeatherCard(modifier = modifier, title = "시간별 예보", contentPadding = false) {
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            modifier = Modifier
                .padding(horizontal = HorizontalListMargin)
                .horizontalFadingEdges(state = listState),
            contentPadding = PaddingValues(horizontal = 20.dp - HorizontalListMargin),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(hours, key = { it.time.toString() }) { h ->
                Column(
                    Modifier.width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val label = when {
                        h.time == now -> "지금"
                        h.time.hour == 0 -> "${h.time.monthValue}/${h.time.dayOfMonth}"
                        else -> "${if (h.time.hour < 12) "오전" else "오후"} ${TimeSlot.twelve(h.time.hour)}시"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    WeatherIcon(h.weatherCode, h.isDay, size = 26.dp)
                    Text(
                        if (h.precipitationProbability > 0) "${h.precipitationProbability}%" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = RainBlue,
                    )
                    Text("${h.temperature.roundToInt()}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "체감 ${h.apparentTemperature.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = FeelsThresholds.DEFAULT.bandOf(h.apparentTemperature).composeColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun DailyForecastCard(forecast: WeatherForecast, modifier: Modifier = Modifier) {
    val today = forecast.localNow().toLocalDate()
    val days = forecast.daily.filter { !it.date.isBefore(today) }
    val low = days.minOfOrNull { it.temperatureMin } ?: 0.0
    val high = days.maxOfOrNull { it.temperatureMax } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    WeatherCard(modifier = modifier, title = "주간 예보") {
        days.forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = when (d.date) {
                    today -> "오늘"
                    today.plusDays(1) -> "내일"
                    else -> d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(44.dp))
                WeatherIcon(d.weatherCode, true, size = 24.dp)
                Text(
                    if (d.precipitationProbabilityMax > 0) "${d.precipitationProbabilityMax}%" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = RainBlue,
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 4.dp),
                )
                Text(
                    "${d.temperatureMin.roundToInt()}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                ) {
                    val span = (high - low).coerceAtLeast(1.0)
                    val from = ((d.temperatureMin - low) / span * size.width).toFloat()
                    val to = ((d.temperatureMax - low) / span * size.width).toFloat()
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                FeelsThresholds.DEFAULT.bandOf(d.apparentMin).composeColor,
                                FeelsThresholds.DEFAULT.bandOf(d.apparentMax).composeColor,
                            ),
                            startX = from,
                            endX = to,
                        ),
                        topLeft = Offset(from, 0f),
                        size = Size((to - from).coerceAtLeast(size.height), size.height),
                        cornerRadius = r,
                    )
                }
                Text(
                    "${d.temperatureMax.roundToInt()}°",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDetailsCard(forecast: WeatherForecast, modifier: Modifier = Modifier) {
    val c = forecast.current
    val today = forecast.today()
    val nowHour = forecast.hourlyAt(forecast.localNow())
    val tiles = buildList {
        add("체감 온도" to "${c.apparentTemperature.roundToInt()}°")
        add("습도" to "${c.humidity}%")
        add("바람" to "%.1fm/s".format(c.windSpeed))
        val uv = nowHour?.uvIndex ?: 0.0
        add("자외선" to "${uv.roundToInt()} ${ClothingRecommender.uvLevel(uv)}")
        if (today != null) {
            add("오늘 강수량" to "%.1fmm".format(today.precipitationSum))
            add("일출 · 일몰" to "${today.sunrise?.toLocalTime() ?: "-"} · ${today.sunset?.toLocalTime() ?: "-"}")
        }
    }
    WeatherCard(modifier = modifier, title = "상세 날씨") {
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            tiles.forEach { (label, value) ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Weather data by Open-Meteo.com (CC BY 4.0)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
