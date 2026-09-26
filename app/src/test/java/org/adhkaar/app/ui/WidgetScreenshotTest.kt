package org.adhkaar.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.widget.AdhkaarWidget
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders the real widget RemoteViews (as a launcher would) onto a wallpaper, for the preview board. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class WidgetScreenshotTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @OptIn(ExperimentalGlanceApi::class)
    private fun render(size: DpSize): View = runBlocking {
        val views = AdhkaarWidget().compose(context, size = size)
        val parent = FrameLayout(context)
        views.apply(context, parent)
    }

    @Test
    fun widgets() {
        SettingsStore.get(context).update { it.copy(onboarded = true, latitude = 6.5244, longitude = 3.3792) }
        val state = SessionState.get(context)
        val today = LocalDate.now()
        (1L..5L).forEach { state.complete(SessionType.MORNING, today.minusDays(it)) }
        state.complete(SessionType.MORNING, today)

        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()
        val width = px(393)
        val height = px(300)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // A plain dark wallpaper, so the widget is judged as it would sit on a home screen.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF2B2F3A.toInt(), 0xFF111318.toInt(), Shader.TileMode.CLAMP)
        })
        fun place(view: View, x: Int, y: Int, w: Int, h: Int) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        place(render(DpSize(150.dp, 150.dp)), px(16), px(16), px(150), px(150))
        place(render(DpSize(361.dp, 110.dp)), px(16), px(180), px(361), px(110))

        val out = File("../docs/screenshots/new-widget.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
