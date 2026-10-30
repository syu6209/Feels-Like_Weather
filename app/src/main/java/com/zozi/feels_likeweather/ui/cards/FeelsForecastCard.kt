package com.zozi.feels_likeweather.ui.cards

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
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.main.SlotUi
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.highlightColor
import com.zozi.feels_likeweather.ui.theme.onColor
import kotlin.math.roundToInt

/** 첫 번째 카드: 체감 보정 예보. */
@Composable
fun FeelsForecastCard(
    feels: FeelsUi,
    surveyCount: Int,
    onSelectSlot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val selected = feels.selected
    val band = selected.myBand

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
                    Surface(shape = CircleShape, color = band.composeColor) {
                        Text(
                            band.label,
                            color = band.onColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "실제 ${selected.temperature.roundToInt()}° · " +
                            if (selected.slot.isCurrent) "지금" else "${selected.slot.dayLabel} ${selected.slot.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (surveyCount > 0 && band != selected.defaultBand) {
                        Text(
                            text = "기본 기준이라면 '${selected.defaultBand.label}'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            BriefingText(
                phrase = feels.briefing,
                color = band.composeColor,
                highlight = band.highlightColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                playKey = band to feels.briefing,
            )
        }
        Spacer(Modifier.height(14.dp))
        TimeSlotRow(feels = feels, onSelect = onSelectSlot)
    }

    if (showInfo) {
        FeelsInfoSheet(
            myThresholds = feels.myThresholds,
            surveyCount = surveyCount,
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
                        .background(slot.myBand.composeColor)
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
                text = if (answered) "응답함 ✓" else slot.myBand.label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
            )
        }
    }
}
