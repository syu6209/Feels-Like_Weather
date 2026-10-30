package com.zozi.feels_likeweather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zozi.feels_likeweather.data.prefs.ThemeMode
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.ui.cards.FeelsInfoSheet
import com.zozi.feels_likeweather.ui.components.ThemedSystemBarIcons
import com.zozi.feels_likeweather.ui.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onOpenSurveyHistory: () -> Unit,
) {
    ThemedSystemBarIcons()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val mine by vm.myThresholds.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val activeCount = userData.surveys.count { !it.isDeleted }
    val deletedCount = userData.surveys.size - activeCount
    var showGraph by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Section("화면 테마")
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(mode.label) },
                    leadingContent = { RadioButton(selected = userData.themeMode == mode, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = userData.themeMode == mode,
                        role = Role.RadioButton,
                        onClick = { vm.setThemeMode(mode) },
                    ),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("내 체감")
            ListItem(
                headlineContent = { Text("내 체감 그래프 보기") },
                supportingContent = { Text("설문 ${activeCount}회 반영 · " + summary(mine)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable { showGraph = true },
            )
            ListItem(
                headlineContent = { Text("내 체감 설문 기록 보기") },
                supportingContent = {
                    Text(
                        "기록 ${activeCount}개" +
                            if (deletedCount > 0) " · 삭제한 기록 ${deletedCount}개(7일 후 완전 삭제)" else ""
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable(onClick = onOpenSurveyHistory),
            )
            ListItem(
                headlineContent = { Text("내 체감 구간 초기화", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("설문 기록을 지우고 기본 체감 구간으로 되돌려요") },
                modifier = Modifier.clickable(enabled = userData.surveys.isNotEmpty()) { confirmReset = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("옷차림 프로필")
            userData.profiles.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        Text("${p.ageGroup.label} · 활동 ${p.activityStartHour}시~${p.activityEndHour}시" + if (p.isMine) " · 내 체감 사용" else "")
                    },
                    leadingContent = { Icon(if (p.isMine) Icons.Filled.Person else Icons.Outlined.Face, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { onEditProfile(p.id) },
                )
            }
            ListItem(
                headlineContent = { Text("프로필 추가") },
                leadingContent = { Icon(Icons.Filled.Add, null) },
                modifier = Modifier.clickable(onClick = onAddProfile),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("정보")
            ListItem(
                headlineContent = { Text("날씨 데이터") },
                supportingContent = { Text("Open-Meteo.com · CC BY 4.0") },
            )
            ListItem(
                headlineContent = { Text("지역명") },
                supportingContent = { Text("Android 내장 Geocoder") },
            )
            ListItem(
                headlineContent = { Text("체감 구간 근거") },
                supportingContent = { Text("기상청 체감온도 단계 · UTCI 열 스트레스 등급 · 기온별 옷차림 지표") },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("내 체감 구간 초기화") },
            text = { Text("설문 기록 ${userData.surveys.size}건이 모두 삭제되고 기본 체감 구간으로 돌아가요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetSurveys()
                }) { Text("초기화") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
    if (showGraph) {
        FeelsInfoSheet(
            myThresholds = mine,
            surveys = userData.surveys,
            currentApparent = vm.weather.value.forecast?.current?.apparentTemperature,
            onDismiss = { showGraph = false },
        )
    }
}

private fun summary(t: FeelsThresholds): String {
    val warm = t.lowerBoundOf(FeelsBand.WARM)!!
    val default = FeelsThresholds.DEFAULT.lowerBoundOf(FeelsBand.WARM)!!
    return "따뜻함 시작 %.1f° (기본 %.0f°)".format(warm, default)
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
