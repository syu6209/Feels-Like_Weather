package com.zozi.feels_likeweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.zozi.feels_likeweather.data.cache.ForecastCache
import com.zozi.feels_likeweather.widget.ClothingWidget
import com.zozi.feels_likeweather.widget.FeelsWidget
import com.zozi.feels_likeweather.widget.WidgetSettings
import com.zozi.feels_likeweather.widget.WidgetSizes
import com.zozi.feels_likeweather.widget.WidgetTextMode
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 위젯을 실제 RemoteViews 로 만들어 크기·설정별로 그려본다.
 * 결과물: app/build/screenshots/widget_*.png (검증은 하지 않는다)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cacheForecast() {
        val json = javaClass.classLoader!!.getResource("forecast_seoul.json").readText()
        runBlocking { ForecastCache(context).save(json, "중구(서울특별시)", 37.5665, 126.978) }
    }

    private fun settings(
        opacity: Float = 0.92f,
        textMode: WidgetTextMode = WidgetTextMode.AUTO,
        survey: Boolean = true,
        profile: String = "mine",
    ) = mutablePreferencesOf(
        WidgetSettings.OPACITY to opacity,
        WidgetSettings.TEXT_MODE to textMode.name,
        WidgetSettings.SHOW_SURVEY to survey,
        WidgetSettings.PROFILE_ID to profile,
    )

    private fun render(widget: GlanceAppWidget, size: DpSize, state: Any): Bitmap = runBlocking {
        val views = widget.compose(context, size = size, state = state)
        val density = context.resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** 배경화면처럼 보이는 그라데이션 위에 여러 위젯을 붙여 한 장으로 저장한다. */
    private fun sheet(name: String, shots: List<Bitmap>) {
        val gap = 40
        val width = shots.maxOf { it.width } + gap * 2
        val height = shots.sumOf { it.height + gap } + gap
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(47, 128, 237), Color.rgb(158, 216, 245), Shader.TileMode.CLAMP)
            },
        )
        var y = gap
        shots.forEach { canvas.drawBitmap(it, gap.toFloat(), y.toFloat(), null); y += it.height + gap }
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val sizes = listOf(
        WidgetSizes.SMALL, WidgetSizes.WIDE, WidgetSizes.SQUARE, WidgetSizes.MEDIUM,
        WidgetSizes.TALL, WidgetSizes.LARGE, WidgetSizes.XLARGE,
    )

    @Test
    fun feelsWidgetAllSizes() = sheet("widget_feels_sizes", sizes.map { render(FeelsWidget(), it, settings()) })

    @Test
    fun feelsWidgetOptions() = sheet(
        "widget_feels_options",
        listOf(
            // 투명도 70% + 흰 글자
            render(FeelsWidget(), WidgetSizes.LARGE, settings(opacity = 0.3f, textMode = WidgetTextMode.WHITE)),
            // 설문 끄기
            render(FeelsWidget(), WidgetSizes.LARGE, settings(survey = false)),
            render(FeelsWidget(), WidgetSizes.TALL, settings(survey = false)),
        ),
    )

    @Test
    fun clothingWidgetAllSizes() = sheet("widget_clothing_sizes", sizes.map { render(ClothingWidget(), it, settings()) })

    @Test
    fun clothingWidgetOptions() = sheet(
        "widget_clothing_options",
        listOf(
            render(ClothingWidget(), WidgetSizes.LARGE, settings(profile = "default-infant")),
            render(ClothingWidget(), WidgetSizes.MEDIUM, settings(opacity = 0f, textMode = WidgetTextMode.WHITE, profile = "default-senior")),
        ),
    )
}
