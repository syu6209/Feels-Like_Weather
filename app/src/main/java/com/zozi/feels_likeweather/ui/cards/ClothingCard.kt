package com.zozi.feels_likeweather.ui.cards

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.domain.ActivityPlan
import com.zozi.feels_likeweather.domain.ActivityPlanner
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.domain.TimeSlots
import com.zozi.feels_likeweather.ui.components.horizontalFadingEdges
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.onColor
import java.time.LocalDate
import kotlin.math.roundToInt

/** 세 번째 카드: 옷차림 추천. */
@Composable
fun ClothingCard(
    feels: FeelsUi,
    forecast: WeatherForecast,
    profiles: List<Profile>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    val thresholds = if (profile.isMine) feels.myThresholds else FeelsThresholds.DEFAULT

    // 날짜: 사용자가 고르지 않았다면 오늘 밤(활동 종료 후)은 내일, 그 전에는 오늘
    var dayOverride by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    val dayOffset = dayOverride ?: ActivityWindow.defaultDayOffset(profile, feels.now)

    val window = remember(profile, dayOffset, forecast, feels.now.hour) {
        ActivityWindow.forDay(forecast, profile, feels.now, dayOffset)
    }
    // '내 기준' 은 설문으로 응답한 시간의 구간을 그대로 반영한다.
    val answers = if (profile.isMine) feels.answers else emptyMap()
    val plan = remember(window, thresholds, answers, forecast.current) {
        ActivityPlanner.plan(
            ActivityPlanner.hourFeels(window, thresholds, feels.now, forecast.current.apparentTemperature, answers)
        )
    }
    val fallbackBand = if (profile.isMine) feels.selected.displayBand else thresholds.bandOf(feels.selected.apparent)
    val recommendation = remember(profile.ageGroup, plan, fallbackBand, window) {
        ClothingRecommender.recommend(profile.ageGroup, plan, fallbackBand, window)
    }

    WeatherCard(
        modifier = modifier,
        title = "옷차림 추천",
        action = {
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "추천 근거",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        contentPadding = false,
    ) {
        val chipsState = rememberLazyListState()
        LazyRow(
            state = chipsState,
            modifier = Modifier.horizontalFadingEdges(state = chipsState),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                FilterChip(
                    selected = p.id == profile.id,
                    onClick = { onSelectProfile(p.id) },
                    label = { Text(p.name.ifBlank { p.ageGroup.label }, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = if (p.isMine) {
                        { Icon(Icons.Filled.Person, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
            item(key = "add") {
                AssistChip(
                    onClick = onAddProfile,
                    label = { Icon(Icons.Filled.Add, contentDescription = "프로필 추가", Modifier.size(18.dp)) },
                )
            }
        }

        DaySelector(
            today = feels.now.toLocalDate(),
            selected = dayOffset,
            onSelect = { dayOverride = it },
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BandRange(plan, recommendation.band)
                    Text(
                        "${profile.ageGroup.label} · 활동 ${window.label} · ${if (profile.isMine) "내 체감" else "기본 체감"} 기준",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onEditProfile(profile.id) }) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("편집")
                }
            }
            if (plan != null) {
                Spacer(Modifier.height(10.dp))
                FeelsStats(plan, thresholds)
                Spacer(Modifier.height(10.dp))
                FeelsTimeline(plan)
            }
            plan?.headline?.let { headline ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 필요한 날씨 아이템 + 옷 = 유효 아이템. 9개 이하면 3열로 크게, 많으면 4열로 카드 폭을 꽉 채운다.
        val needed = recommendation.conditionalItems.filter { it.needed }
        val unneeded = recommendation.conditionalItems.filterNot { it.needed }
        val valid = needed.map { TileData(it.item, it.reason, emphasized = true) } +
            recommendation.items.map { TileData(it.item, it.note) }
        BoxWithConstraints(Modifier.padding(horizontal = 20.dp)) {
            val columns = if (valid.size <= 9) 3 else 4
            val tileWidth = (maxWidth - TileSpacing * (columns - 1)) / columns
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                valid.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                        row.forEach { ClothingTile(it, profile.ageGroup, tileWidth) }
                    }
                }
            }
        }

        // 지금은 필요 없는 날씨 아이템은 아래에 작게, 가로로 스크롤해서 본다.
        if (unneeded.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "필요 없는 아이템",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
            val unneededState = rememberLazyListState()
            LazyRow(
                state = unneededState,
                modifier = Modifier.horizontalFadingEdges(state = unneededState),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                items(unneeded, key = { it.item.key }) {
                    ClothingTile(TileData(it.item, it.reason, dimmed = true), profile.ageGroup, 72.dp)
                }
            }
        }

        if (recommendation.tips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                recommendation.tips.forEach { tip ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showInfo) {
        ClothingInfoSheet(
            profile = profile,
            recommendation = recommendation,
            usesMyBands = profile.isMine,
            onDismiss = { showInfo = false },
        )
    }
}

private val TileSpacing = 10.dp

/** 구간 태그. 활동 시간 중 구간이 바뀌면 "쌀쌀함 ~ 따뜻함 (주로 따뜻함)" 으로 보여준다. */
@Composable
private fun BandRange(plan: ActivityPlan?, core: FeelsBand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (plan != null && plan.bandSpan > 0) {
            BandTag(plan.coldBand)
            Text(" ~ ", style = MaterialTheme.typography.bodyMedium)
            BandTag(plan.warmBand)
            Spacer(Modifier.width(6.dp))
            Text("주로 ${core.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        } else {
            BandTag(core)
            Spacer(Modifier.width(6.dp))
            Text("활동 시간 내내", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BandTag(band: FeelsBand) {
    Surface(shape = RoundedCornerShape(50), color = band.composeColor) {
        Text(
            band.label,
            color = band.onColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

/** 활동 시간의 최저·평균·최고 체감. */
@Composable
private fun FeelsStats(plan: ActivityPlan, thresholds: FeelsThresholds) {
    val coldest = plan.hours.minBy { it.apparent }
    val warmest = plan.hours.maxBy { it.apparent }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("최저 체감", plan.minApparent, coldest.band, "${coldest.time.hour}시", Modifier.weight(1f))
        Stat("평균 체감", plan.meanApparent, thresholds.bandOf(plan.meanApparent), null, Modifier.weight(1f))
        Stat("최고 체감", plan.maxApparent, warmest.band, "${warmest.time.hour}시", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: Double, band: FeelsBand, at: String?, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(band.composeColor)
                )
                Spacer(Modifier.width(4.dp))
                Text("${value.roundToInt()}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (at != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 활동 시간 동안의 구간 흐름. 구간 묶음마다 대표색 막대와 시작 시각·구간 이름을 보여준다. */
@Composable
private fun FeelsTimeline(plan: ActivityPlan) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        plan.segments.forEachIndexed { i, seg ->
            val first = i == 0
            val last = i == plan.segments.lastIndex
            Column(Modifier.weight(seg.hours.toFloat())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (first) 4.dp else 0.dp,
                                bottomStart = if (first) 4.dp else 0.dp,
                                topEnd = if (last) 4.dp else 0.dp,
                                bottomEnd = if (last) 4.dp else 0.dp,
                            )
                        )
                        .background(seg.band.composeColor)
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${seg.start.hour}시",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (last) {
                        Text(
                            seg.rangeLabel.substringAfter("~"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    seg.band.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = seg.band.composeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** 오늘·내일·모레 선택. 가로를 꽉 채우고, 선택된 칩은 조금 더 넓어지며 부드럽게 바뀐다. */
@Composable
private fun DaySelector(today: LocalDate, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..2).forEach { offset ->
            val isSelected = offset == selected
            val weight by animateFloatAsState(
                if (isSelected) 1.45f else 1f,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                label = "dayWeight",
            )
            val container by animateColorAsState(
                if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerLowest, label = "dayBg",
            )
            val border by animateColorAsState(
                if (isSelected) scheme.secondary else scheme.outlineVariant, label = "dayBorder",
            )
            val d = today.plusDays(offset.toLong())
            Surface(
                onClick = { onSelect(offset) },
                shape = RoundedCornerShape(12.dp),
                color = container,
                border = BorderStroke(1.dp, border),
                modifier = Modifier
                    .weight(weight)
                    .height(38.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Row {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Text(
                        "${TimeSlots.dayLabel(offset.toLong())} ${d.monthValue}/${d.dayOfMonth}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class TileData(
    val item: ClothingItem,
    val caption: String? = null,
    val emphasized: Boolean = false,
    val dimmed: Boolean = false,
)

@Composable
private fun ClothingTile(tile: TileData, ageGroup: AgeGroup, width: Dp) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(width)
            .alpha(if (tile.dimmed) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(if (width > 90.dp) 22.dp else 18.dp),
            color = if (tile.emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
            border = if (tile.emphasized) BorderStroke(1.5.dp, scheme.primary) else null,
            modifier = Modifier.size(width),
        ) {
            Box(contentAlignment = Alignment.Center) { ClothingImage(tile.item, ageGroup, width * 0.78f) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tile.item.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (tile.caption != null) {
            Text(
                tile.caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (tile.emphasized) scheme.primary else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 옷 이미지. 생성 이미지(drawable)가 추가되면 자동으로 사용한다.
 * 탐색 순서: `cloth_<연령대>_<key>` → `cloth_<key>` → 기본 아이콘.
 */
@SuppressLint("DiscouragedApi", "LocalContextGetResourceValueCall")
@Composable
private fun ClothingImage(item: ClothingItem, ageGroup: AgeGroup, size: Dp) {
    val context = LocalContext.current
    val resId = remember(item, ageGroup) {
        val res = context.resources
        val pkg = context.packageName
        listOf("cloth_${ageGroup.name.lowercase()}_${item.key}", "cloth_${item.key}")
            .firstNotNullOfOrNull { name -> res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } }
    }
    if (resId != null) {
        Image(
            painter = painterResource(resId),
            contentDescription = item.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    } else {
        Icon(
            fallbackIcon(item),
            contentDescription = item.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun fallbackIcon(item: ClothingItem): ImageVector = when (item) {
    ClothingItem.UMBRELLA, ClothingItem.RAINCOAT, ClothingItem.RAIN_BOOTS -> Icons.Outlined.Umbrella
    ClothingItem.SUN_HAT, ClothingItem.SUNSCREEN -> Icons.Outlined.WbSunny
    ClothingItem.SUNGLASSES -> Icons.Outlined.Visibility
    ClothingItem.WATER_BOTTLE -> Icons.Outlined.LocalDrink
    ClothingItem.HAND_WARMER -> Icons.Outlined.LocalFireDepartment
    ClothingItem.SANDALS -> Icons.Outlined.BeachAccess
    ClothingItem.BEANIE, ClothingItem.GLOVES, ClothingItem.SCARF, ClothingItem.NECK_WARMER,
    ClothingItem.WINTER_BOOTS -> Icons.Outlined.AcUnit
    else -> Icons.Outlined.Checkroom
}
