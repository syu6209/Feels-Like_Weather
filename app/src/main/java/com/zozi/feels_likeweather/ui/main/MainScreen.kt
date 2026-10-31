package com.zozi.feels_likeweather.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zozi.feels_likeweather.data.model.Place
import com.zozi.feels_likeweather.data.model.weatherDescription
import com.zozi.feels_likeweather.ui.cards.ClothingCard
import com.zozi.feels_likeweather.ui.cards.DailyForecastCard
import com.zozi.feels_likeweather.ui.cards.FeelsForecastCard
import com.zozi.feels_likeweather.ui.cards.FeelsSurveyCard
import com.zozi.feels_likeweather.ui.cards.HourlyForecastCard
import com.zozi.feels_likeweather.ui.cards.WeatherCard
import com.zozi.feels_likeweather.ui.cards.WeatherDetailsCard
import com.zozi.feels_likeweather.ui.components.SystemBarIcons
import com.zozi.feels_likeweather.ui.components.skyBrush
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val HeaderHeight = 300.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenRegion: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
) {
    val weather by vm.weather.collectAsStateWithLifecycle()
    val feels by vm.feels.collectAsStateWithLifecycle()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.startIfNeeded() }

    LaunchedEffect(Unit) {
        if (vm.needsLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        } else {
            vm.startIfNeeded()
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // 화면이 보이는 동안 1분마다: 받은 지 30분이 지났으면 새로 받고, 저장된 예보의 현재 시각을 맞춘다.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.onTick()
                delay(60_000)
            }
        }
    }

    SystemBarIcons(lightIcons = true)

    val forecast = weather.forecast
    val sky = forecast?.current?.let { skyBrush(it.weatherCode, it.isDay) } ?: skyBrush(0, true)
    val headerMaxPx = with(LocalDensity.current) { HeaderHeight.toPx() }
    val header = rememberCollapsingHeaderState(headerMaxPx)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(sky)
    ) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        Column(Modifier.fillMaxSize()) {
            MainTopBar(
                title = weather.placeName?.let { "${it}의 날씨" } ?: "날씨 불러오는 중…",
                usingCurrentLocation = weather.usingCurrentLocation,
                savedPlaces = userData.savedPlaces,
                selectedPlace = userData.selectedPlace,
                onSelectPlace = vm::selectPlace,
                collapsedSubtitle = forecast?.current?.let {
                    "${it.temperature.roundToInt()}° ${weatherDescription(it.weatherCode)} · 체감 ${it.apparentTemperature.roundToInt()}°"
                },
                collapsedFraction = header.collapsedFraction,
                onOpenRegion = onOpenRegion,
                onOpenSettings = onOpenSettings,
            )

            PullToRefreshBox(
                isRefreshing = weather.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(header.connection)
                ) {
                    // 접히는 현재 날씨 헤더 (패럴랙스, 완전히 접힘)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val full = headerMaxPx.roundToInt()
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = full, maxHeight = full)
                                )
                                val visible = (headerMaxPx + header.offsetPx).roundToInt().coerceAtLeast(0)
                                layout(placeable.width, visible) {
                                    placeable.place(0, (header.offsetPx * 0.5f).roundToInt())
                                }
                            }
                            .graphicsLayer { alpha = (1f - header.collapsedFraction * 1.3f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            forecast != null -> CurrentWeatherHeader(
                                forecast,
                                Modifier.fillMaxSize(),
                                // 1분마다 바뀌는 체감 화면 시각에 맞춰 기준 시각 표시 여부를 다시 계산한다.
                                nowMillis = remember(feels?.now) { System.currentTimeMillis() },
                            )
                            weather.loading -> CircularProgressIndicator(color = Color.White)
                            else -> Text(weather.error.orEmpty(), color = Color.White)
                        }
                    }

                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        weather.notice?.let { notice ->
                            item(key = "notice", span = StaggeredGridItemSpan.FullLine) {
                                NoticeCard(notice, actionLabel = "지역 선택", onAction = onOpenRegion)
                            }
                        }
                        if (weather.error != null) {
                            item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                                NoticeCard(weather.error!!, actionLabel = "다시 시도", onAction = vm::refresh)
                            }
                        }
                        val f = feels
                        if (forecast != null && f != null) {
                            item(key = "feels") {
                                FeelsForecastCard(f, userData.surveys, onSelectSlot = vm::selectSlot)
                            }
                            item(key = "survey") {
                                FeelsSurveyCard(f, onAnswer = { band -> vm.answerSurvey(f.selected, band) })
                            }
                            item(key = "clothing") {
                                ClothingCard(
                                    feels = f,
                                    forecast = forecast,
                                    profiles = userData.profiles,
                                    selectedProfileId = userData.selectedProfileId,
                                    onSelectProfile = vm::selectProfile,
                                    onEditProfile = onEditProfile,
                                    onAddProfile = onAddProfile,
                                )
                            }
                            item(key = "hourly") { HourlyForecastCard(forecast) }
                            item(key = "daily") { DailyForecastCard(forecast) }
                            item(key = "details") { WeatherDetailsCard(forecast) }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun MainTopBar(
    title: String,
    usingCurrentLocation: Boolean,
    savedPlaces: List<Place>,
    selectedPlace: Place?,
    onSelectPlace: (Place?) -> Unit,
    collapsedSubtitle: String?,
    collapsedFraction: Float,
    onOpenRegion: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var placesOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 지역명을 누르면 저장한 지역 목록(+ 지역 추가), 저장한 지역이 없으면 바로 지역 선택으로 간다.
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (savedPlaces.isEmpty()) onOpenRegion() else placesOpen = true
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (usingCurrentLocation) {
                        Icon(Icons.Filled.LocationOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "지역 바꾸기", tint = Color.White)
                }
                if (collapsedSubtitle != null) {
                    // 헤더가 접힐수록 상단바에 현재 날씨 요약이 나타난다.
                    val alpha = ((collapsedFraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    Text(
                        collapsedSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .graphicsLayer { this.alpha = alpha }
                            .layout { m, c ->
                                val p = m.measure(c)
                                val h = (p.height * alpha).roundToInt()
                                layout(p.width, h) { p.place(0, 0) }
                            },
                    )
                }
            }
            PlaceMenu(
                expanded = placesOpen,
                savedPlaces = savedPlaces,
                selectedPlace = selectedPlace,
                onSelect = { place ->
                    placesOpen = false
                    onSelectPlace(place)
                },
                onAdd = {
                    placesOpen = false
                    onOpenRegion()
                },
                onDismiss = { placesOpen = false },
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "메뉴", tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("지역 선택") },
                    leadingIcon = { Icon(Icons.Outlined.Map, null) },
                    onClick = {
                        menuOpen = false
                        onOpenRegion()
                    },
                )
                DropdownMenuItem(
                    text = { Text("설정") },
                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                    onClick = {
                        menuOpen = false
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

/** 상단바 지역명 드롭다운: 현재 위치, 저장한 지역, 맨 아래 지역 추가. */
@Composable
private fun PlaceMenu(
    expanded: Boolean,
    savedPlaces: List<Place>,
    selectedPlace: Place?,
    onSelect: (Place?) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 420.dp),
    ) {
        @Composable
        fun Item(label: String, sub: String?, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (!sub.isNullOrBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                leadingIcon = { Icon(icon, null) },
                trailingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, "선택됨", tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = onClick,
            )
        }

        Item("현재 위치", null, Icons.Filled.MyLocation, selected = selectedPlace == null) { onSelect(null) }
        savedPlaces.forEach { place ->
            Item(place.name, place.region, Icons.Outlined.Place, selected = place.id == selectedPlace?.id) { onSelect(place) }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("지역 추가", color = MaterialTheme.colorScheme.primary) },
            leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = onAdd,
        )
    }
}

@Composable
private fun NoticeCard(message: String, actionLabel: String, onAction: () -> Unit) {
    WeatherCard {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
