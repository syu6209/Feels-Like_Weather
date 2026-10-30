package com.zozi.feels_likeweather.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.zozi.feels_likeweather.domain.BriefingPhrase
import kotlinx.coroutines.delay

/**
 * 브리핑 문구. [BriefingPhrase.keyword] 에만
 *  1) 등장 시 크게 확대되었다가 원래 크기로 돌아오는 효과와
 *  2) 대표색 ↔ 보조색 브러쉬 그라데이션이 계속 흐르는 효과를 준다.
 * 앞·뒤 문구와 한 [Text] 에 들어가므로 공간이 부족하면 자연스럽게 줄바꿈된다(줄 수 제한 없음).
 *
 * @param playKey 값이 바뀔 때마다 확대 애니메이션을 다시 재생한다.
 */
@Composable
fun BriefingText(
    phrase: BriefingPhrase,
    color: Color,
    highlight: Color,
    style: TextStyle,
    playKey: Any,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1.7f,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        scale.snapTo(1f)
        scale.animateTo(zoomScale, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        delay(900)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    val baseSize: TextUnit = if (style.fontSize.isUnspecified) 16.sp else style.fontSize
    val periodPx = with(LocalDensity.current) { (baseSize * 7).toPx() }
    val transition = rememberInfiniteTransition(label = "brush")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "brushShift",
    )
    val start = shift * periodPx
    val brush = Brush.linearGradient(
        colors = listOf(color, highlight, color),
        start = Offset(start, 0f),
        end = Offset(start + periodPx, periodPx * 0.25f),
        tileMode = TileMode.Repeated,
    )

    val text = buildAnnotatedString {
        append(phrase.prefix)
        withStyle(
            SpanStyle(
                brush = brush,
                fontWeight = FontWeight.ExtraBold,
                fontSize = baseSize * scale.value,
                // 보조색(흰색)이 밝은 배경 위에서도 읽히도록 대표색 그림자를 살짝 깐다.
                shadow = Shadow(color = color.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 3f),
            )
        ) { append(phrase.keyword) }
        append(phrase.suffix)
    }
    Text(text = text, style = style, color = LocalContentColor.current, modifier = modifier)
}
