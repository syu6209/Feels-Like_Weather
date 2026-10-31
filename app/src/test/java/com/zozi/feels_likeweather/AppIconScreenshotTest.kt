package com.zozi.feels_likeweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 앱 아이콘(적응형) 레이어를 원형·사각 마스크와 테마 아이콘으로 그려 본다. 결과: app/build/screenshots/app_icon.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppIconScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun layer(id: Int, size: Int, tint: Int? = null): Bitmap {
        val d = ContextCompat.getDrawable(context, id)!!.mutate()
        tint?.let { d.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        d.setBounds(0, 0, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { d.draw(Canvas(it)) }
    }

    private fun masked(canvas: Canvas, x: Float, size: Int, mask: Path, layers: List<Bitmap>, background: Int? = null) {
        canvas.save()
        canvas.translate(x, 40f)
        canvas.clipPath(mask)
        background?.let { canvas.drawColor(it) }
        layers.forEach { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restore()
    }

    @Test
    fun appIcon() {
        val size = 432
        val bg = layer(R.drawable.ic_launcher_background, size)
        val fg = layer(R.drawable.ic_launcher_foreground, size)
        val mono = layer(R.drawable.ic_launcher_monochrome, size, tint = Color.rgb(28, 60, 110))
        val circle = Path().apply { addCircle(size / 2f, size / 2f, size / 3f, Path.Direction.CW) }
        val inset = size / 6f
        val squircle = Path().apply { addRoundRect(inset, inset, size - inset, size - inset, 60f, 60f, Path.Direction.CW) }

        val out = Bitmap.createBitmap(size * 3 + 80, size + 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawPaint(Paint().apply { color = Color.rgb(236, 239, 244) })
        masked(canvas, 0f, size, circle, listOf(bg, fg))
        masked(canvas, size.toFloat() + 40, size, squircle, listOf(bg, fg))
        masked(canvas, size * 2f + 80, size, circle, listOf(mono), background = Color.rgb(214, 228, 250))
        File("build/screenshots").mkdirs()
        File("build/screenshots/app_icon.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
