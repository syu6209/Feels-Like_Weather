package com.zozi.feels_likeweather.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Season
import com.zozi.feels_likeweather.domain.SurveyRecord
import com.zozi.feels_likeweather.ui.components.BandGraph
import com.zozi.feels_likeweather.ui.components.ThemedSystemBarIcons
import com.zozi.feels_likeweather.ui.main.MainViewModel
import com.zozi.feels_likeweather.ui.theme.composeColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 설정 > 내 체감 설문 기록. 삭제(7일간 되돌리기 가능)와 되돌리기를 지원한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyHistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    ThemedSystemBarIcons()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val mine by vm.myThresholds.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 체감 설문 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        SurveyHistoryContent(
            records = userData.surveys,
            thresholds = mine,
            nowMillis = System.currentTimeMillis(),
            onDelete = { key ->
                scope.launch {
                    val message = vm.deleteSurvey(key)
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(message, actionLabel = "되돌리기", withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) {
                        snackbar.showSnackbar(vm.restoreSurvey(key))
                    }
                }
            },
            onRestore = { key ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(vm.restoreSurvey(key))
                }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun SurveyHistoryContent(
    records: List<SurveyRecord>,
    thresholds: FeelsThresholds,
    nowMillis: Long,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = FeelsLearner.purge(records, nowMillis)
    val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val active = visible.filterNot { it.isDeleted }.sortedByDescending { it.slotKey }
    val deleted = visible.filter { it.isDeleted }.sortedByDescending { it.deletedAtMillis }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "summary") {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .animateContentSize()
            ) {
                Text("내 체감 그래프", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (active.isEmpty()) "반영 중인 응답이 없어 기본 구간과 같아요."
                    else "응답 ${active.size}개 중 지금(${Season.of(today).label})과 비슷한 시기의 응답 " +
                        "${FeelsLearner.inSeasonCount(records, today)}개를 중심으로 반영했어요. " +
                        "최신 ${FeelsLearner.FULL_WEIGHT_COUNT}개 이후 응답은 점점 덜 반영되고 " +
                        "${FeelsLearner.ZERO_WEIGHT_COUNT}번째부터는 반영하지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BandGraph(thresholds)
            }
        }

        item(key = "active-title") { SectionTitle("반영 중인 기록 ${active.size}") }
        if (active.isEmpty()) {
            item(key = "empty") {
                Text(
                    "아직 설문 기록이 없어요. 메인 화면의 체감 설문 카드에서 느낌을 알려주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        items(active, key = { "a-" + it.slotKey }) { record ->
            RecordRow(
                record = record,
                trailing = {
                    IconButton(onClick = { onDelete(record.slotKey) }) {
                        Icon(Icons.Outlined.Delete, "삭제")
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }

        if (deleted.isNotEmpty()) {
            item(key = "deleted-title") {
                Column {
                    SectionTitle("삭제한 기록 ${deleted.size}")
                    Text(
                        "구간 계산에서 빠져 있어요. 삭제 후 7일이 지나면 완전히 지워져요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(deleted, key = { "d-" + it.slotKey }) { record ->
                val left = FeelsLearner.PURGE_AFTER_MILLIS - (nowMillis - record.deletedAtMillis!!)
                val days = ceil(left / (24.0 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
                RecordRow(
                    record = record,
                    dimmed = true,
                    extra = "${days}일 후 완전 삭제",
                    trailing = {
                        TextButton(onClick = { onRestore(record.slotKey) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("되돌리기")
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun RecordRow(
    record: SurveyRecord,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    extra: String? = null,
) {
    val predicted = record.predicted
    val change = when {
        predicted == null -> "응답 '${record.reported.surveyLabel}'"
        predicted == record.reported -> "예보 '${predicted.label}'와 같았음"
        else -> "예보 '${predicted.label}' → 응답 '${record.reported.label}'"
    }
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(record.reported.composeColor)
                    .alpha(if (dimmed) 0.4f else 1f)
            )
        },
        headlineContent = {
            Text(
                slotLabel(record.slotKey),
                modifier = Modifier.alpha(if (dimmed) 0.5f else 1f),
            )
        },
        supportingContent = {
            Column(Modifier.alpha(if (dimmed) 0.6f else 1f)) {
                Text("체감 ${record.apparentTemperature.roundToInt()}° · $change")
                if (extra != null) {
                    Text(extra, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { trailing() } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** "2026-10-30T15:00" → "10월 30일(금) 오후 3시" */
private fun slotLabel(slotKey: String): String = runCatching {
    val t = LocalDateTime.parse(slotKey)
    val day = t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val ampm = if (t.hour < 12) "오전" else "오후"
    val h = (t.hour % 12).let { if (it == 0) 12 else it }
    "${t.format(DateTimeFormatter.ofPattern("M월 d일"))}($day) $ampm ${h}시"
}.getOrDefault(slotKey)
