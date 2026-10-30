package com.zozi.feels_likeweather.ui.cards

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.TimeSlots
import java.time.LocalDate
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.onColor
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
    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    val thresholds = if (profile.isMine) feels.myThresholds else FeelsThresholds.DEFAULT

    // 날짜: 사용자가 고르지 않았다면 오늘 밤(활동 종료 후)은 내일, 그 전에는 오늘
    var dayOverride by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    val dayOffset = dayOverride ?: ActivityWindow.defaultDayOffset(profile, feels.now)
    val date = feels.now.toLocalDate().plusDays(dayOffset.toLong())

    val window = remember(profile, dayOffset, forecast, feels.now.hour) {
        ActivityWindow.forDay(forecast, profile, feels.now, dayOffset)
    }
    val basis = clothingBasis(feels, window, date, profile, thresholds)
    val recommendation = remember(profile, basis.band, window, thresholds) {
        ClothingRecommender.recommend(profile.ageGroup, basis.band, window, thresholds)
    }

    WeatherCard(modifier = modifier, title = "옷차림 추천", contentPadding = false) {
        LazyRow(
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = basis.band.composeColor) {
                            Text(
                                basis.band.label,
                                color = basis.band.onColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "체감 ${basis.apparent.roundToInt()}° · ${if (profile.isMine) "내 체감" else "기본 체감"} 기준",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "${basis.label} · 활동 ${window.label} · ${profile.ageGroup.label}",
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
        }
        Spacer(Modifier.height(10.dp))

        // 필요한 날씨 아이템 + 옷 = 유효 아이템. 9개 이하면 3열로 크게, 많으면 4열로 카드 폭을 꽉 채운다.
        val needed = recommendation.conditionalItems.filter { it.needed }
        val unneeded = recommendation.conditionalItems.filterNot { it.needed }
        val valid = needed.map { TileData(it.item, it.reason, emphasized = true) } +
            recommendation.items.map { TileData(it) }
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
            LazyRow(
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
}

private val TileSpacing = 10.dp

/** 옷차림 추천의 기준 체감. */
private data class ClothingBasis(val apparent: Double, val band: FeelsBand, val label: String)

/**
 * 고른 날짜에 체감 보정 예보 카드에서 선택한 시간대가 있으면 그 시간대(설문 응답 반영)를,
 * 없으면 그날 활동 시간대의 평균 체감을 기준으로 삼는다.
 */
private fun clothingBasis(
    feels: FeelsUi,
    window: ActivityWindow,
    date: LocalDate,
    profile: Profile,
    thresholds: FeelsThresholds,
): ClothingBasis {
    val slot = feels.selected
    if (slot.slot.hour.toLocalDate() == date) {
        val band = if (profile.isMine) slot.displayBand else thresholds.bandOf(slot.apparent)
        val label = if (slot.slot.isCurrent) "지금 체감" else "${slot.slot.dayLabel} ${slot.slot.title} 체감"
        return ClothingBasis(slot.apparent, band, label)
    }
    val mean = window.meanApparent ?: slot.apparent
    return ClothingBasis(mean, thresholds.bandOf(mean), "활동 시간 평균 체감")
}

@Composable
private fun DaySelector(today: LocalDate, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..2).forEach { offset ->
            val d = today.plusDays(offset.toLong())
            FilterChip(
                selected = offset == selected,
                onClick = { onSelect(offset) },
                label = {
                    Text(
                        "${TimeSlots.dayLabel(offset.toLong())} ${d.monthValue}/${d.dayOfMonth}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = if (offset == selected) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
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
