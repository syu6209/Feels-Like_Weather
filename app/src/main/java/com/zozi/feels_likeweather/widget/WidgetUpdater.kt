package com.zozi.feels_likeweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zozi.feels_likeweather.data.local.WeatherStore
import com.zozi.feels_likeweather.data.remote.OpenMeteoClient
import java.util.concurrent.TimeUnit

/** 위젯 갱신과 주기적 예보 갱신 예약. */
object WidgetUpdater {

    private const val WORK_NAME = "widget-forecast-refresh"

    /** 모든 위젯이 데이터를 다시 읽고 그리도록 한다. */
    suspend fun updateAll(context: Context) {
        update(context, FeelsWidget())
        update(context, ClothingWidget())
    }

    private suspend fun update(context: Context, widget: GlanceAppWidget) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            // 위젯 세션이 살아 있어도 다시 읽도록 버전 값을 올린다.
            updateAppWidgetState(context, id) { prefs ->
                prefs[WidgetSettings.VERSION] = (prefs[WidgetSettings.VERSION] ?: 0) + 1
            }
            widget.update(context, id)
        }
    }

    /** 위젯이 하나라도 있으면 30분마다 마지막으로 본 지역의 예보를 받아 위젯을 갱신한다. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** 위젯이 모두 없어지면 주기 갱신을 멈춘다. */
    suspend fun cancelIfNoWidgets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val remaining = manager.getGlanceIds(FeelsWidget::class.java).size +
            manager.getGlanceIds(ClothingWidget::class.java).size
        if (remaining == 0) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/**
 * 백그라운드 위치 권한 없이, 마지막으로 본 지역의 좌표로 예보만 다시 받아 로컬 DB 와 위젯을 갱신한다.
 * 앱이 이미 30분 안에 받아 두었으면 API 를 부르지 않는다.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = WeatherStore(applicationContext)
        val last = store.lastViewed() ?: return Result.success()
        // 주기 작업은 실행 시각이 몇 분씩 흔들리므로 5분 여유를 둔다.
        if (!last.isStale(System.currentTimeMillis(), WeatherStore.REFRESH_INTERVAL_MILLIS - FLEX_MILLIS)) {
            WidgetUpdater.updateAll(applicationContext)
            return Result.success()
        }
        return try {
            val forecast = OpenMeteoClient().fetchForecast(last.latitude, last.longitude)
            store.save(last.key, last.placeName, last.latitude, last.longitude, forecast)
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private companion object {
        const val FLEX_MILLIS = 5 * 60 * 1000L
    }
}
