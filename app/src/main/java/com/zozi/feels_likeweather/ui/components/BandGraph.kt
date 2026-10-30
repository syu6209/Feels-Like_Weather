package com.zozi.feels_likeweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.onColor
import kotlin.math.roundToInt

/**
 * 체감 구간 막대 그래프. 구간별 대표색으로 나뉜 가로 막대와,
 * 각 분기점 선 아래에 분기 온도를 표시한다.
 *
 * @param marker 표시할 현재 체감온도 (null 이면 표시 안 함)
 */
@Composable
fun BandGraph(
    thresholds: FeelsThresholds,
    modifier: Modifier = Modifier,
    marker: Double? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.onSurface
    val markerColor = MaterialTheme.colorScheme.onSurface
    val boundaryStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val min = minOf(FeelsThresholds.GRAPH_MIN, thresholds.boundaries.first() - 6)
        val max = maxOf(FeelsThresholds.GRAPH_MAX, thresholds.boundaries.last() + 6)
        fun x(t: Double): Float = ((t - min) / (max - min) * size.width).toFloat()

        val markerSpace = 14.dp.toPx()
        val barTop = markerSpace
        val barHeight = 34.dp.toPx()
        val corner = CornerRadius(10.dp.toPx())

        // 구간 막대
        val clip = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(0f, barTop, size.width, barTop + barHeight, corner)
            )
        }
        clipPath(clip) {
            FeelsBand.entries.forEach { band ->
                val from = thresholds.lowerBoundOf(band)?.let(::x) ?: 0f
                val to = thresholds.upperBoundOf(band)?.let(::x) ?: size.width
                drawRect(band.composeColor, Offset(from, barTop), Size(to - from, barHeight))
                val name = measurer.measure(
                    band.label,
                    TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = band.onColor),
                )
                if (name.size.width + 4.dp.toPx() < to - from) {
                    drawText(
                        name,
                        topLeft = Offset(
                            (from + to) / 2 - name.size.width / 2,
                            barTop + barHeight / 2 - name.size.height / 2,
                        ),
                    )
                }
            }
        }

        // 분기점 선과 분기 온도 (겹치면 두 번째 줄로 내린다)
        var lastLabelEnd = Float.NEGATIVE_INFINITY
        var row = 0
        thresholds.boundaries.forEach { b ->
            val bx = x(b)
            drawLine(lineColor.copy(alpha = 0.7f), Offset(bx, barTop - 2.dp.toPx()), Offset(bx, barTop + barHeight + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            val text = if (b == b.roundToInt().toDouble()) "${b.roundToInt()}°" else "%.1f°".format(b)
            val layout = measurer.measure(text, boundaryStyle)
            val left = (bx - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
            row = if (left < lastLabelEnd + 2.dp.toPx()) (row + 1) % 2 else 0
            if (row == 0) lastLabelEnd = left + layout.size.width
            drawText(layout, topLeft = Offset(left, barTop + barHeight + 6.dp.toPx() + row * (layout.size.height + 1)))
        }

        // 현재 체감온도 마커 (▼)
        if (marker != null) {
            val mx = x(marker.coerceIn(min, max))
            val s = 6.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(mx - s, barTop - s - 2.dp.toPx())
                    lineTo(mx + s, barTop - s - 2.dp.toPx())
                    lineTo(mx, barTop - 1.dp.toPx())
                    close()
                },
                markerColor,
            )
        }
    }
}
