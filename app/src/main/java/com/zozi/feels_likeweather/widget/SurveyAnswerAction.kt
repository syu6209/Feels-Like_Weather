package com.zozi.feels_likeweather.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.zozi.feels_likeweather.data.prefs.AppPreferences
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.withAnswer
import kotlinx.coroutines.flow.first

/** 위젯의 설문 버튼. 지금 시간대 응답을 저장하고 모든 위젯을 갱신한다(앱의 내 체감 구간에도 그대로 반영). */
class SurveyAnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val slotKey = parameters[SLOT_KEY] ?: return
        val apparent = parameters[APPARENT] ?: return
        val reported = parameters[REPORTED]?.let { FeelsBand.entries.getOrNull(it) } ?: return
        val predicted = parameters[PREDICTED]?.let { FeelsBand.entries.getOrNull(it) } ?: reported

        val prefs = AppPreferences(context)
        val surveys = prefs.userData.first().surveys
        prefs.saveSurveys(surveys.withAnswer(slotKey, apparent, reported, predicted, System.currentTimeMillis()))
        WidgetUpdater.updateAll(context)
    }

    companion object {
        val SLOT_KEY = ActionParameters.Key<String>("slot")
        val APPARENT = ActionParameters.Key<Double>("apparent")
        val REPORTED = ActionParameters.Key<Int>("reported")
        val PREDICTED = ActionParameters.Key<Int>("predicted")
    }
}
