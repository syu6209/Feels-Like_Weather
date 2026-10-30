package com.zozi.feels_likeweather.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.zozi.feels_likeweather.data.prefs.AppPreferences
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.ui.components.skyBrush
import com.zozi.feels_likeweather.ui.theme.FeelsLikeWeatherTheme
import com.zozi.feels_likeweather.ui.theme.composeColor
import com.zozi.feels_likeweather.ui.theme.onColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class WidgetKind(val title: String) { FEELS("체감 보정 예보 위젯"), CLOTHING("옷차림 추천 위젯") }

/**
 * 위젯 설정 화면. 위젯을 추가할 때, 그리고 런처에서 위젯을 길게 눌러 "설정" 으로 다시 열 때 쓴다.
 * 설정은 위젯마다 따로 저장된다.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 설정을 마치지 않고 나가면 위젯을 추가하지 않는다 (처음 추가할 때).
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
        val kind = if (provider == ClothingWidgetReceiver::class.java.name) WidgetKind.CLOTHING else WidgetKind.FEELS

        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            val current = WidgetSettings.from(
                getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            )
            val userData = AppPreferences(this@WidgetConfigActivity).userData.first()
            setContent {
                FeelsLikeWeatherTheme(themeMode = userData.themeMode) {
                    WidgetConfigScreen(
                        kind = kind,
                        initial = current,
                        profiles = userData.profiles,
                        onClose = { finish() },
                        onSave = { settings -> save(appWidgetId, kind, settings) },
                    )
                }
            }
        }
    }

    private fun save(appWidgetId: Int, kind: WidgetKind, settings: WidgetSettings) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetSettings.OPACITY] = settings.backgroundOpacity
                prefs[WidgetSettings.TEXT_MODE] = settings.textMode.name
                prefs[WidgetSettings.SHOW_SURVEY] = settings.showSurvey
                prefs[WidgetSettings.PROFILE_ID] = settings.profileId
                prefs[WidgetSettings.VERSION] = (prefs[WidgetSettings.VERSION] ?: 0) + 1
            }
            when (kind) {
                WidgetKind.FEELS -> FeelsWidget().update(this@WidgetConfigActivity, glanceId)
                WidgetKind.CLOTHING -> ClothingWidget().update(this@WidgetConfigActivity, glanceId)
            }
            WidgetUpdater.schedule(this@WidgetConfigActivity)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    kind: WidgetKind,
    initial: WidgetSettings,
    profiles: List<Profile>,
    onClose: () -> Unit,
    onSave: (WidgetSettings) -> Unit,
) {
    var settings by remember { mutableStateOf(initial) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${kind.title} 설정") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기") } },
            )
        },
        bottomBar = {
            Button(
                onClick = { onSave(settings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 16.dp)
                    .height(52.dp),
            ) { Text("저장") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            WidgetPreview(kind, settings, profiles.firstOrNull { it.id == settings.profileId })

            Spacer(Modifier.height(20.dp))
            val transparency = ((1 - settings.backgroundOpacity) * 100).roundToInt()
            Text("배경 투명도 ${transparency}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Slider(
                value = 1 - settings.backgroundOpacity,
                onValueChange = { settings = settings.copy(backgroundOpacity = 1 - it) },
                steps = 9,
            )
            Text(
                "0%는 불투명, 100%는 완전히 투명해요. 투명하게 쓸 땐 배경화면에 맞춰 글자색을 골라주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text("글자색", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetTextMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.textMode == mode,
                        onClick = { settings = settings.copy(textMode = mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            when (kind) {
                WidgetKind.FEELS -> {
                    Spacer(Modifier.height(20.dp))
                    ListItem(
                        headlineContent = { Text("체감 설문 보이기") },
                        supportingContent = {
                            Text("위젯이 충분히 클 때(2×3, 4×3 이상) 지금 체감을 위젯에서 바로 응답할 수 있어요. 응답은 내 체감 구간에 반영돼요.")
                        },
                        trailingContent = {
                            Switch(checked = settings.showSurvey, onCheckedChange = { settings = settings.copy(showSurvey = it) })
                        },
                    )
                }
                WidgetKind.CLOTHING -> {
                    Spacer(Modifier.height(20.dp))
                    Text("보여줄 프로필", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    profiles.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name.ifBlank { p.ageGroup.label }) },
                            supportingContent = {
                                Text(
                                    "${p.ageGroup.label} · 활동 ${p.activityStartHour}시~${p.activityEndHour}시" +
                                        if (p.isMine) " · 내 체감 사용" else ""
                                )
                            },
                            leadingContent = { RadioButton(selected = settings.profileId == p.id, onClick = null) },
                            modifier = Modifier.selectable(
                                selected = settings.profileId == p.id,
                                role = Role.RadioButton,
                                onClick = { settings = settings.copy(profileId = p.id) },
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "지원 크기: 2×1, 2×2, 2×3, 4×1, 4×2, 4×3, 5×4 이상. 위젯 크기를 늘리면 더 많은 정보가 보여요.\n" +
                    "설정은 홈 화면에서 위젯을 길게 누른 뒤 '설정' 으로 언제든 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 배경화면 위에서 위젯이 어떻게 보일지 대략 보여주는 미리보기. */
@Composable
private fun WidgetPreview(kind: WidgetKind, settings: WidgetSettings, profile: Profile?) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = (if (dark) Color(0xFF1E1F24) else Color.White).copy(alpha = settings.backgroundOpacity)
    val text = when (settings.textMode) {
        WidgetTextMode.AUTO -> if (dark) Color(0xFFE6E1E5) else Color(0xFF1B1B1F)
        WidgetTextMode.WHITE -> Color.White
        WidgetTextMode.BLACK -> Color(0xFF1B1B1F)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(skyBrush(0, isDay = true), RoundedCornerShape(20.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = background, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val band = FeelsBand.CHILLY
                when (kind) {
                    WidgetKind.FEELS -> {
                        Text("9°", color = text, fontSize = 36.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Surface(shape = RoundedCornerShape(50), color = band.composeColor) {
                                Text(band.label, color = band.onColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Text("쌀쌀하게 느끼실거에요.", color = text, fontSize = 13.sp)
                        }
                    }
                    WidgetKind.CLOTHING -> {
                        Surface(shape = RoundedCornerShape(50), color = band.composeColor) {
                            Text(band.label, color = band.onColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${profile?.name?.ifBlank { profile.ageGroup.label } ?: "내 기준"} · 니트 · 트렌치코트",
                            color = text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
