package com.zozi.feels_likeweather.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 카드 안 가로 스크롤 목록의 양 끝을 투명하게 흐려, 카드 여백 쪽으로 자연스럽게 사라지게 한다.
 * [state] 를 주면 더 스크롤할 수 있는 쪽에만 흐림을 적용한다.
 */
fun Modifier.horizontalFadingEdges(width: Dp = 20.dp, state: ScrollableState? = null): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = width.toPx().coerceAtMost(size.width / 2)
        if (edge <= 0f) return@drawWithContent
        val fadeStart = state?.canScrollBackward ?: true
        val fadeEnd = state?.canScrollForward ?: true
        if (fadeStart) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge),
                size = size.copy(width = edge),
                blendMode = BlendMode.DstIn,
            )
        }
        if (fadeEnd) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - edge, 0f),
                size = size.copy(width = edge),
                blendMode = BlendMode.DstIn,
            )
        }
    }
