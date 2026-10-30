package com.zozi.feels_likeweather.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 0~24시가 나열된 막대 그래프 위에서 시작/끝 핸들을 드래그해 활동 시간을 고른다.
 *
 * @param hourlyApparent 시간별(0..23) 체감온도. 막대 높이와 색에 쓰이며, 없으면 같은 높이로 그린다.
 * @param barColor 시간별 막대 색상 (활동 시간 안쪽에 쓰임)
 */
@Composable
fun ActivityRangeSelector(
    start: Int,
    end: Int,
    onChange: (start: Int, end: Int) -> Unit,
    hourlyApparent: List<Double?>,
    barColor: (Int) -> Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val currentOnChange by rememberUpdatedState(onChange)
    // 드래그 중인 핸들: 0 = 시작, 1 = 끝
    var activeHandle by remember { mutableIntStateOf(-1) }

    val known = hourlyApparent.filterNotNull()
    val minT = known.minOrNull() ?: 0.0
    val maxT = known.maxOrNull() ?: 1.0

    fun hourAt(x: Float, width: Float): Int = (x / width * 24f).roundToInt().coerceIn(0, 24)

    fun move(handle: Int, hour: Int) {
        if (handle == 0) currentOnChange(hour.coerceIn(0, currentEnd - 1), currentEnd)
        else currentOnChange(currentStart, hour.coerceIn(currentStart + 1, 24))
    }

    fun nearestHandle(x: Float, width: Float): Int {
        val h = x / width * 24f
        return if (abs(h - currentStart) <= abs(h - currentEnd)) 0 else 1
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    move(nearestHandle(p.x, size.width.toFloat()), hourAt(p.x, size.width.toFloat()))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p -> activeHandle = nearestHandle(p.x, size.width.toFloat()) },
                    onDragEnd = { activeHandle = -1 },
                    onDragCancel = { activeHandle = -1 },
                ) { change, _ ->
                    change.consume()
                    if (activeHandle >= 0) move(activeHandle, hourAt(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val cell = size.width / 24f
        val labelHeight = 18.dp.toPx()
        val knob = 11.dp.toPx()
        val barBottom = size.height - labelHeight - knob * 2
        val barTopMax = 26.dp.toPx()
        val gap = 2.dp.toPx()

        // 시간 막대
        for (h in 0 until 24) {
            val t = hourlyApparent.getOrNull(h)
            val ratio = if (t == null || maxT == minT) 0.6f else (0.25f + 0.75f * ((t - minT) / (maxT - minT))).toFloat()
            val top = barBottom - (barBottom - barTopMax) * ratio
            val inside = h in start until end
            drawRoundRect(
                color = if (inside) barColor(h) else scheme.surfaceContainerHighest,
                topLeft = Offset(h * cell + gap / 2, top),
                size = Size(cell - gap, barBottom - top),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        // 시간 축 라벨
        val axisStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant)
        listOf(0, 6, 12, 18, 24).forEach { h ->
            val layout = measurer.measure("${h}시", axisStyle)
            val x = (h * cell - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, size.height - layout.size.height))
        }

        // 시작/끝 핸들
        listOf(start to 0, end to 1).forEach { (hour, index) ->
            val x = hour * cell
            val active = activeHandle == index
            drawLine(scheme.primary, Offset(x, barTopMax - 8.dp.toPx()), Offset(x, barBottom + knob), strokeWidth = 2.dp.toPx())
            drawCircle(scheme.primary, radius = if (active) knob * 1.2f else knob, center = Offset(x, barBottom + knob))
            drawCircle(scheme.onPrimary, radius = knob * 0.4f, center = Offset(x, barBottom + knob))
            val label = measurer.measure(
                "${hour}시",
                TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary),
            )
            val lx = (x - label.size.width / 2).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(lx, 0f))
        }
    }
}
