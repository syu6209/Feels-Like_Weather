package com.zozi.feels_likeweather.widget

import android.content.Context
import com.zozi.feels_likeweather.data.cache.ForecastCache
import com.zozi.feels_likeweather.data.prefs.AppPreferences
import com.zozi.feels_likeweather.data.prefs.UserData
import com.zozi.feels_likeweather.domain.ActivityPlanner
import com.zozi.feels_likeweather.domain.ActivityWindow
import com.zozi.feels_likeweather.domain.BriefingPhrase
import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.ClothingRecommendation
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.ui.main.SlotUi
import com.zozi.feels_likeweather.ui.main.buildSlots
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 체감 보정 예보 위젯 데이터. */
data class FeelsWidgetModel(
    val placeName: String,
    val current: SlotUi,
    /** 지금 이후의 시간대 (아침·점심·저녁 …). */
    val upcoming: List<SlotUi>,
    val briefing: BriefingPhrase,
    /** 지금 시간대에 대한 설문 응답 (없으면 null). */
    val answer: FeelsBand?,
    val updatedAtMillis: Long,
)

/** 옷차림 추천 위젯 데이터. */
data class ClothingWidgetModel(
    val placeName: String,
    val profile: Profile,
    val recommendation: ClothingRecommendation,
    val usesMyBands: Boolean,
)

/** 위젯이 앱 없이 그릴 수 있도록 캐시된 예보와 저장된 사용자 데이터로 화면 데이터를 만든다. */
object WidgetData {

    private suspend fun load(context: Context): Pair<com.zozi.feels_likeweather.data.cache.CachedForecast, UserData>? {
        val cached = ForecastCache(context).load() ?: return null
        val data = AppPreferences(context).userData.first()
        return cached to data
    }

    suspend fun feels(context: Context): FeelsWidgetModel? {
        val (cached, data) = load(context) ?: return null
        val forecast = cached.forecast
        val mine = FeelsLearner.estimate(data.surveys, LocalDate.now())
        val now = forecast.localNow()
        val slots = buildSlots(forecast, now, mine, data.surveys)
        val current = slots.firstOrNull { it.slot.isCurrent } ?: return null

        // 앱과 같은 규칙으로 브리핑 문구를 고르고, 새로 고정했다면 저장한다(1시간 고정).
        val band = current.displayBand
        val pin = data.briefingPins[band]
        val (phrase, newPin) = Briefings.pick(band, pin, System.currentTimeMillis())
        if (newPin != pin) AppPreferences(context).saveBriefingPins(data.briefingPins + (band to newPin))

        return FeelsWidgetModel(
            placeName = cached.placeName,
            current = current,
            upcoming = slots.filter { it.slot.hour.isAfter(current.slot.hour) },
            briefing = phrase,
            answer = current.answer,
            updatedAtMillis = forecast.fetchedAtMillis,
        )
    }

    suspend fun clothing(context: Context, profileId: String): ClothingWidgetModel? {
        val (cached, data) = load(context) ?: return null
        val forecast = cached.forecast
        val profile = data.profiles.firstOrNull { it.id == profileId } ?: data.profiles.first()
        val thresholds = if (profile.isMine) FeelsLearner.estimate(data.surveys, LocalDate.now()) else FeelsThresholds.DEFAULT
        val now = forecast.localNow()

        val window = ActivityWindow.forDay(forecast, profile, now, ActivityWindow.defaultDayOffset(profile, now))
        val answers = if (profile.isMine) {
            data.surveys.filterNot { it.isDeleted }.associate { it.slotKey to it.reported }
        } else {
            emptyMap()
        }
        val plan = ActivityPlanner.plan(
            ActivityPlanner.hourFeels(window, thresholds, now, forecast.current.apparentTemperature, answers)
        )
        val fallback = thresholds.bandOf(forecast.current.apparentTemperature)
        return ClothingWidgetModel(
            placeName = cached.placeName,
            profile = profile,
            recommendation = ClothingRecommender.recommend(profile.ageGroup, plan, fallback, window),
            usesMyBands = profile.isMine,
        )
    }
}
