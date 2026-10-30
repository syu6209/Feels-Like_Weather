package com.zozi.feels_likeweather.ui.region

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zozi.feels_likeweather.data.model.Place
import com.zozi.feels_likeweather.ui.components.ThemedSystemBarIcons
import com.zozi.feels_likeweather.ui.main.MainViewModel
import kotlinx.coroutines.launch

private val MajorCities = listOf(
    Place("서울특별시", "대한민국", 37.5665, 126.9780),
    Place("부산광역시", "대한민국", 35.1796, 129.0756),
    Place("인천광역시", "대한민국", 37.4563, 126.7052),
    Place("대구광역시", "대한민국", 35.8714, 128.6014),
    Place("대전광역시", "대한민국", 36.3504, 127.3845),
    Place("광주광역시", "대한민국", 35.1595, 126.8526),
    Place("울산광역시", "대한민국", 35.5384, 129.3114),
    Place("세종특별자치시", "대한민국", 36.4800, 127.2890),
    Place("수원시", "경기도", 37.2636, 127.0286),
    Place("춘천시", "강원특별자치도", 37.8813, 127.7298),
    Place("전주시", "전북특별자치도", 35.8242, 127.1480),
    Place("제주시", "제주특별자치도", 33.4996, 126.5312),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegionScreen(vm: MainViewModel, onBack: () -> Unit) {
    ThemedSystemBarIcons()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }

    fun choose(place: Place?) {
        vm.selectPlace(place)
        onBack()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { choose(null) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            results = vm.searchPlaces(q)
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지역 선택") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("도시·구·동 이름으로 검색") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                results = null
                            }) { Icon(Icons.Filled.Close, "지우기") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val found = results
            if (searching) {
                item { ListItem(headlineContent = { Text("검색 중…") }, leadingContent = { CircularProgressIndicator() }) }
            } else if (found != null) {
                item { SectionTitle("검색 결과") }
                if (found.isEmpty()) {
                    item { ListItem(headlineContent = { Text("검색 결과가 없어요. 다른 이름으로 검색해보세요.") }) }
                }
                items(found, key = { "r-" + it.id }) { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = { Text(place.region) },
                        leadingContent = { Icon(Icons.Outlined.Place, null) },
                        modifier = Modifier.clickable { choose(place) },
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }

            item {
                ListItem(
                    headlineContent = { Text("현재 위치", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("위치 권한으로 지금 있는 곳의 날씨를 보여줘요") },
                    leadingContent = { Icon(Icons.Filled.MyLocation, null) },
                    trailingContent = {
                        if (userData.selectedPlace == null) Icon(Icons.Filled.Check, "선택됨", tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        if (vm.hasLocationPermission()) {
                            choose(null)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                            )
                        }
                    },
                )
            }

            if (userData.savedPlaces.isNotEmpty()) {
                item { SectionTitle("저장한 지역") }
                items(userData.savedPlaces, key = { "s-" + it.id }) { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = { Text(place.region) },
                        leadingContent = {
                            if (userData.selectedPlace?.id == place.id) {
                                Icon(Icons.Filled.Check, "선택됨", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Outlined.Place, null)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.removeSavedPlace(place) }) { Icon(Icons.Filled.Close, "삭제") }
                        },
                        modifier = Modifier.clickable { choose(place) },
                    )
                }
            }

            item { SectionTitle("주요 도시") }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    MajorCities.forEach { city ->
                        AssistChip(
                            onClick = { choose(city) },
                            label = { Text(city.name) },
                            leadingIcon = { Icon(Icons.Outlined.LocationCity, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
