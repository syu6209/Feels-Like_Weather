package com.zozi.feels_likeweather.ui.cards

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
@OptIn(ExperimentalLayoutApi::class)
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
    val apparent = feels.selected.apparent
    val band = thresholds.bandOf(apparent)
    val recommendation = remember(profile, band, forecast, feels.now.hour) {
        val window = ActivityWindow.of(forecast, profile, feels.now)
        ClothingRecommender.recommend(profile.ageGroup, band, window, thresholds)
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

        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = band.composeColor) {
                            Text(
                                band.label,
                                color = band.onColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "체감 ${apparent.roundToInt()}° · ${if (profile.isMine) "내 체감" else "기본 체감"} 기준",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "${profile.ageGroup.label}(${profile.ageGroup.description}) · 활동 ${recommendation.window.label}",
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
            Spacer(Modifier.height(10.dp))

            // 필요한 날씨 아이템 → 옷 → 불필요한 날씨 아이템 순서
            val needed = recommendation.conditionalItems.filter { it.needed }
            val unneeded = recommendation.conditionalItems.filterNot { it.needed }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                needed.forEach { ClothingTile(it.item, profile.ageGroup, caption = it.reason, emphasized = true) }
                recommendation.items.forEach { ClothingTile(it, profile.ageGroup) }
                unneeded.forEach { ClothingTile(it.item, profile.ageGroup, caption = it.reason, dimmed = true) }
            }

            if (recommendation.tips.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
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

@Composable
private fun ClothingTile(
    item: ClothingItem,
    ageGroup: AgeGroup,
    caption: String? = null,
    emphasized: Boolean = false,
    dimmed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(76.dp)
            .alpha(if (dimmed) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
            border = if (emphasized) BorderStroke(1.5.dp, scheme.primary) else null,
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { ClothingImage(item, ageGroup) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasized) scheme.primary else scheme.onSurfaceVariant,
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
private fun ClothingImage(item: ClothingItem, ageGroup: AgeGroup) {
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
            modifier = Modifier.size(56.dp),
        )
    } else {
        Icon(
            fallbackIcon(item),
            contentDescription = item.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
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
