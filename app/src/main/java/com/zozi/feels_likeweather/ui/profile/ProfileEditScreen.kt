package com.zozi.feels_likeweather.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.ui.components.ThemedSystemBarIcons
import com.zozi.feels_likeweather.ui.theme.composeColor

/**
 * 프로필 추가/수정 화면.
 * 이름 입력, 연령대 칩 선택, 활동 시간(막대 그래프 핸들 드래그), 우측 상단 휴지통, 우측 하단 저장 버튼.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    initial: Profile,
    isNew: Boolean,
    forecast: WeatherForecast?,
    myThresholds: FeelsThresholds,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    ThemedSystemBarIcons()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var age by rememberSaveable { mutableStateOf(initial.ageGroup) }
    var start by rememberSaveable { mutableIntStateOf(initial.activityStartHour) }
    var end by rememberSaveable { mutableIntStateOf(initial.activityEndHour) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val thresholds = if (initial.isMine) myThresholds else FeelsThresholds.DEFAULT
    val today = forecast?.localNow()?.toLocalDate()
    val hourly: List<Double?> = (0 until 24).map { h ->
        today?.let { d -> forecast.hourlyAt(d.atTime(h, 0))?.apparentTemperature }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "프로필 추가" else "프로필 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !initial.isMine) {
                            Icon(Icons.Outlined.Delete, if (initial.isMine) "내 기준 프로필은 삭제할 수 없어요" else "삭제")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { age.label },
                            ageGroup = age,
                            activityStartHour = start,
                            activityEndHour = end,
                        )
                    )
                },
                icon = { Icon(Icons.Filled.Check, null) },
                text = { Text("저장") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("이름") },
                placeholder = { Text(age.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (initial.isMine) {
                Text(
                    "'내 기준' 프로필은 체감 설문으로 보정된 내 체감 구간을 사용하며 삭제할 수 없어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("연령대", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgeGroup.entries.forEach { g ->
                    FilterChip(
                        selected = g == age,
                        onClick = { age = g },
                        label = { Text("${g.label} · ${g.description}") },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("활동 시간", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${start}시 ~ ${end}시 (${end - start}시간) · 핸들을 드래그해서 조절하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ActivityRangeSelector(
                start = start,
                end = end,
                onChange = { s, e ->
                    start = s
                    end = e
                },
                hourlyApparent = hourly,
                barColor = { h -> hourly[h]?.let { thresholds.bandOf(it).composeColor } ?: primary },
            )
            if (hourly.any { it != null }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "막대 높이와 색은 오늘 시간별 체감온도와 구간이에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("프로필 삭제") },
            text = { Text("'${initial.name}' 프로필을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
