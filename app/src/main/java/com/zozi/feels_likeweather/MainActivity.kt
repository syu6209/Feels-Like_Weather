package com.zozi.feels_likeweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.ui.main.MainScreen
import com.zozi.feels_likeweather.ui.main.MainViewModel
import com.zozi.feels_likeweather.ui.profile.ProfileEditScreen
import com.zozi.feels_likeweather.ui.region.RegionScreen
import com.zozi.feels_likeweather.ui.settings.SettingsScreen
import com.zozi.feels_likeweather.ui.theme.FeelsLikeWeatherTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userData by vm.userData.collectAsStateWithLifecycle()
            FeelsLikeWeatherTheme(themeMode = userData.themeMode) {
                val nav = rememberNavController()
                val editProfile: (String) -> Unit = { id -> nav.navigate("profile/$id") }
                val addProfile: () -> Unit = { nav.navigate("profile/$NEW_PROFILE") }

                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            vm = vm,
                            onOpenRegion = { nav.navigate("region") },
                            onOpenSettings = { nav.navigate("settings") },
                            onEditProfile = editProfile,
                            onAddProfile = addProfile,
                        )
                    }
                    composable("region") {
                        RegionScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onEditProfile = editProfile,
                            onAddProfile = addProfile,
                        )
                    }
                    composable(
                        "profile/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id") ?: NEW_PROFILE
                        val draft = remember(id) { Profile.new() }
                        val existing = userData.profiles.firstOrNull { it.id == id }
                        val isNew = existing == null
                        val initial = existing ?: draft
                        val myThresholds by vm.myThresholds.collectAsStateWithLifecycle()
                        val weather by vm.weather.collectAsStateWithLifecycle()
                        ProfileEditScreen(
                            initial = initial,
                            isNew = isNew,
                            forecast = weather.forecast,
                            myThresholds = myThresholds,
                            onSave = { profile ->
                                vm.saveProfile(profile)
                                nav.popBackStack()
                            },
                            onDelete = {
                                vm.deleteProfile(initial.id)
                                nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val NEW_PROFILE = "new"
    }
}
