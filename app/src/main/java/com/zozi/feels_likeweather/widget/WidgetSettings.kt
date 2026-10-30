package com.zozi.feels_likeweather.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zozi.feels_likeweather.domain.Profile

/** 위젯 글자색. 배경을 투명하게 하면 배경화면에 맞춰 흰색/검정을 고를 수 있다. */
enum class WidgetTextMode(val label: String) { AUTO("자동(테마)"), WHITE("흰색"), BLACK("검정") }

/**
 * 위젯 하나의 설정. Glance 위젯 상태(위젯마다 따로 저장)에 보관한다.
 *
 * @param backgroundOpacity 배경 불투명도 0(완전 투명) ~ 1(불투명)
 * @param showSurvey 체감 보정 예보 위젯에 설문 버튼을 보여줄지
 * @param profileId 옷차림 추천 위젯에 보여줄 프로필
 */
data class WidgetSettings(
    val backgroundOpacity: Float = 0.9f,
    val textMode: WidgetTextMode = WidgetTextMode.AUTO,
    val showSurvey: Boolean = true,
    val profileId: String = Profile.MINE_ID,
) {
    companion object {
        val OPACITY = floatPreferencesKey("bg_opacity")
        val TEXT_MODE = stringPreferencesKey("text_mode")
        val SHOW_SURVEY = booleanPreferencesKey("show_survey")
        val PROFILE_ID = stringPreferencesKey("profile_id")

        /** 값이 바뀌면 위젯이 데이터를 다시 읽는다. */
        val VERSION = intPreferencesKey("data_version")

        fun from(prefs: Preferences): WidgetSettings = WidgetSettings(
            backgroundOpacity = prefs[OPACITY] ?: 0.9f,
            textMode = prefs[TEXT_MODE]?.let { runCatching { WidgetTextMode.valueOf(it) }.getOrNull() } ?: WidgetTextMode.AUTO,
            showSurvey = prefs[SHOW_SURVEY] ?: true,
            profileId = prefs[PROFILE_ID] ?: Profile.MINE_ID,
        )
    }
}
