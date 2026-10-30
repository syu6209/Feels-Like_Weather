package com.zozi.feels_likeweather.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.SurveyRecord
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zozi.feels_likeweather.ui.components.BriefingText
import com.zozi.feels_likeweather.ui.components.WeatherIcon
import com.zozi.feels_likeweather.ui.components.horizontalFadingEdges
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.main.SlotUi
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.brushHighlight
import com.zozi.feels_likeweather.ui.theme.onColor
import kotlin.math.roundToInt

/** 첫 번째 카드: 체감 보정 예보. */
@Composable
fun FeelsForecastCard(
    feels: FeelsUi,
    surveys: List<SurveyRecord>,
    onSelectSlot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val surveyCount = surveys.count { !it.isDeleted }
    val selected = feels.selected
    val band = selected.displayBand
    val reaction = feels.reaction
    val cardIsLight = MaterialTheme.colorScheme.surfaceContainerLowest.luminance() > 0.5f

    WeatherCard(
        modifier = modifier,
        title = "체감 보정 예보",
        action = {
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "체감 구간 산정 방법",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        contentPadding = false,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${selected.apparent.roundToInt()}°",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    BandTagWithCorrection(from = reaction?.from ?: selected.correctedFrom, to = band)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "실제 ${selected.temperature.roundToInt()}° · " +
                            if (selected.slot.isCurrent) "지금" else "${selected.slot.dayLabel} ${selected.slot.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selected.answer == null && surveyCount > 0 && band != selected.defaultBand) {
                        Text(
                            text = "기본 기준이라면 '${selected.defaultBand.label}'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 설문 응답이 예보와 다르면 2초간 반응 문구("생각보다 더 '춥게' 느끼셨군요!")를 보여준 뒤
            // 새 구간의 브리핑으로 넘어간다. 문구가 바뀔 때마다 확대 애니메이션이 다시 재생된다.
            val phrase = reaction?.phrase ?: feels.briefing
            BriefingText(
                phrase = phrase,
                color = band.composeColor,
                highlight = band.brushHighlight(cardIsLight),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                playKey = Triple(selected.slot.key, band, phrase),
            )
        }
        Spacer(Modifier.height(14.dp))
        TimeSlotRow(feels = feels, onSelect = onSelectSlot)
    }

    if (showInfo) {
        FeelsInfoSheet(
            myThresholds = feels.myThresholds,
            surveys = surveys,
            currentApparent = selected.apparent,
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun TimeSlotRow(feels: FeelsUi, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    val currentIndex = feels.slots.indexOfFirst { it.slot.isCurrent }
    // 처음에는 현재 박스가 보이도록 스크롤 (앞쪽 박스 하나는 살짝 보이게)
    val currentKey = feels.slots.getOrNull(currentIndex)?.slot?.key
    LaunchedEffect(currentKey) {
        if (currentIndex >= 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier.horizontalFadingEdges(state = listState),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(feels.slots, key = { it.slot.key }) { slot ->
            TimeSlotBox(
                slot = slot,
                selected = slot.slot.key == feels.selected.slot.key,
                answered = feels.answers.containsKey(slot.slot.key),
                onClick = { onSelect(slot.slot.key) },
            )
        }
    }
}

@Composable
private fun TimeSlotBox(slot: SlotUi, selected: Boolean, answered: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, label = "slotBg",
    )
    val borderColor by animateColorAsState(
        if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f), label = "slotBorder",
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "slotBorderWidth")
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(min = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (slot.slot.isCurrent) "지금" else slot.slot.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (slot.slot.isCurrent) scheme.primary else content.copy(alpha = 0.7f),
                fontWeight = if (slot.slot.isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = slot.slot.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            WeatherIcon(slot.weatherCode, slot.isDay, size = 22.dp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(slot.displayBand.composeColor)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${slot.apparent.roundToInt()}°",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            }
            Text(
                text = if (answered) "${slot.displayBand.label} ✓" else slot.myBand.label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 구간 태그. 설문으로 구간이 바뀐 시간대는 원래 예보 구간을 흐리게 지운 뒤 "쌀쌀함 → 추움" 처럼 보여준다.
 * 새 구간 태그는 바뀔 때 아래에서 위로 밀려 올라오며 전환된다.
 */
@Composable
private fun BandTagWithCorrection(from: FeelsBand?, to: FeelsBand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = from != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            val shown = from ?: to
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = shown.composeColor.copy(alpha = 0.35f)) {
                    Text(
                        shown.label,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "에서",
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedContent(
            targetState = to,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "bandTag",
        ) { band ->
            Surface(shape = CircleShape, color = band.composeColor) {
                Text(
                    band.label,
                    color = band.onColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
