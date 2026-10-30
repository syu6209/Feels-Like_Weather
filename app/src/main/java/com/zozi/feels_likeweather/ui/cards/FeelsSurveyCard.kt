package com.zozi.feels_likeweather.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.ui.components.ParticleLayer
import com.zozi.feels_likeweather.ui.components.rememberParticleBurstState
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.highlightColor
import com.zozi.feels_likeweather.ui.theme.onColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PeekWidth = 18.dp
private val ButtonSpacing = 8.dp

/** 두 번째 카드: 체감 설문. 체감 보정 예보 카드에서 선택한 시간대의 체감을 묻는다. */
@Composable
fun FeelsSurveyCard(
    feels: FeelsUi,
    onAnswer: (FeelsBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = feels.selected
    val slot = selected.slot
    val future = slot.isFuture(feels.now)
    val answer = feels.answers[slot.key]
    val primary = MaterialTheme.colorScheme.primary

    val particles = rememberParticleBurstState()
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    WeatherCard(modifier = modifier, title = "체감 설문", contentPadding = false) {
        Box(Modifier.onGloballyPositioned { cardCoordinates = it }) {
            Column {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primary)) {
                            append(slot.questionSubject)
                        }
                        append(
                            when {
                                future -> "는 아직이에요. 시간이 지난 뒤 어땠는지 알려주세요."
                                slot.isCurrent -> "는 어떻게 느끼시나요?"
                                else -> "는 어떻게 느끼셨나요?"
                            }
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "체감 ${selected.apparent.roundToInt()}° · 예보 구간 '${selected.myBand.label}'" +
                        (answer?.let { " · 내 응답 '${it.surveyLabel}'" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(14.dp))

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // 한 화면에 버튼 3개 + 양쪽에 다음 버튼 모서리만 살짝 보이도록 너비 계산
                    val buttonWidth = (maxWidth - PeekWidth * 2 - ButtonSpacing * 4) / 3
                    val listState = rememberLazyListState()
                    val centerBand = answer ?: selected.myBand
                    LaunchedEffect(slot.key) {
                        listState.scrollToItem((centerBand.ordinal - 1).coerceAtLeast(0))
                    }
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = PeekWidth + ButtonSpacing),
                        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
                        flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
                    ) {
                        itemsIndexed(FeelsBand.entries, key = { _, band -> band.name }) { _, band ->
                            SurveyButton(
                                band = band,
                                width = buttonWidth,
                                predicted = band == selected.myBand,
                                chosen = band == answer,
                                enabled = !future,
                                onClick = { center ->
                                    val origin = cardCoordinates?.let { card -> card.localPositionOf(center.first, center.second) }
                                    if (origin != null) {
                                        particles.burst(
                                            origin,
                                            listOf(band.composeColor, band.highlightColor, primary, band.composeColor),
                                        )
                                    }
                                    onAnswer(band)
                                },
                            )
                        }
                    }
                }
            }
            ParticleLayer(particles, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SurveyButton(
    band: FeelsBand,
    width: androidx.compose.ui.unit.Dp,
    predicted: Boolean,
    chosen: Boolean,
    enabled: Boolean,
    onClick: (Pair<LayoutCoordinates, Offset>) -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val bounce = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val color = band.composeColor

    Surface(
        onClick = {
            scope.launch {
                // 확대 → 통통 튀며 원래 크기로
                scale.animateTo(1.22f, tween(110))
                scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
            }
            scope.launch {
                bounce.animateTo(-14f, tween(120))
                bounce.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessMedium))
            }
            coordinates?.let { c ->
                onClick(c to Offset(c.size.width / 2f, c.size.height / 3f))
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = if (chosen) color else color.copy(alpha = 0.14f),
        border = BorderStroke(if (predicted || chosen) 2.dp else 1.dp, color.copy(alpha = if (predicted || chosen) 1f else 0.4f)),
        modifier = Modifier
            .width(width)
            .padding(top = 18.dp, bottom = 6.dp)
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = bounce.value * density
                alpha = if (enabled) 1f else 0.45f
            },
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = band.surveyLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (chosen) band.onColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (predicted || chosen) {
                Text(
                    text = if (chosen) "내 응답" else "예보",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosen) band.onColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
