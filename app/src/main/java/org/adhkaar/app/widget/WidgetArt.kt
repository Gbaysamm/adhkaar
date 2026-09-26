package org.adhkaar.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.adhkaar.app.R
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * What Glance can't draw itself (arcs, glows, a chosen Arabic font), painted into small bitmaps.
 * Bitmaps travel to the launcher inside the widget's RemoteViews, so they are drawn at no more
 * than 2 px per dp: sharp on any phone, and a few hundred KB at most.
 */
object WidgetArt {
    private val dawnGlows = intArrayOf(0xFFFF7A59.toInt(), 0xFFC04CFF.toInt())
    private val eveningGlows = intArrayOf(0xFF2B5BFF.toInt(), 0xFF6A4CFF.toInt())
    private val dawnAccent = 0xFFFFB88A.toInt()
    private val eveningAccent = 0xFF8FB1FF.toInt()

    private fun scale(context: Context) = min(context.resources.displayMetrics.density, 2f)

    /** A bitmap that reports [widthDp] × [heightDp] to the launcher, whatever its pixel size. */
    private fun canvasBitmap(scale: Float, widthDp: Float, heightDp: Float): Bitmap =
        Bitmap.createBitmap((widthDp * scale).toInt(), (heightDp * scale).toInt(), Bitmap.Config.ARGB_8888).apply {
            density = (DisplayMetrics.DENSITY_DEFAULT * scale).toInt()
        }

    /** The app's orb ([org.adhkaar.app.ui.components.NurOrb]): a sun rising for the morning, a crescent for the evening. */
    fun orb(context: Context, sizeDp: Float, sun: Boolean): Bitmap {
        val scale = scale(context)
        // Room around the sphere for its halo.
        val bitmap = canvasBitmap(scale, sizeDp, sizeDp)
        val r = bitmap.width / 2f / 1.6f
        drawOrb(Canvas(bitmap), bitmap.width / 2f, bitmap.height / 2f, r, sun, sunHeight = 0f)
        return bitmap
    }

    /**
     * The day as one arc, as on the Today screen ([org.adhkaar.app.ui.home.DaySky]): the orb at
     * [progress] (Fajr 0 → Maghrib 1), the path behind it lit, stars gathering towards night.
     * Morning is on the start side, so in Arabic and Urdu the day runs right to left.
     */
    fun sky(context: Context, progress: Float, palette: WidgetPalette, rtl: Boolean, widthDp: Float, heightDp: Float): Bitmap {
        val scale = scale(context)
        val bitmap = canvasBitmap(scale, widthDp, heightDp)
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val t = progress.coerceIn(0f, 1f)
        val night = smoothstep(0.78f, 0.96f, t)
        val sunHeight = smoothstep(0f, 1f, sin(PI * t).toFloat() * 1.25f - 0.15f)
        val nightSky = palette.text.luminance() > 0.5f

        // A slice of a true circle standing on the horizon, like the sun's path; its top leaves room for the halo.
        val hy = h * 0.86f
        val orbR = h * 0.15f
        val half = w * 0.4f
        val rise = hy - orbR * 1.9f
        val radius = (half * half + rise * rise) / (2 * rise)
        val centreY = hy - rise + radius
        val span = asin(half / radius)
        fun onArc(p: Float): Pair<Float, Float> {
            val a = -span + 2 * span * p
            val x = w / 2 + radius * sin(a)
            return (if (rtl) w - x else x) to centreY - radius * cos(a)
        }
        // The orb travels only where it clears the horizon.
        val reach = acos(((radius - rise + orbR + 2 * scale) / radius).coerceIn(-1f, 1f))
        val ends = ((span - reach) / (2 * span)).coerceIn(0f, 0.3f)
        val (orbX, orbY) = onArc(ends + (1 - 2 * ends) * t)

        // Each end of the day glows its own colour at the horizon; softer on parchment, where glow reads as a stain.
        val glowStrength = if (nightSky) 1f else 0.55f
        fun glow(x: Float, color: Int, alpha: Float) {
            val reachPx = w * 0.3f
            paint.shader = RadialGradient(x, hy, reachPx, withAlpha(color, alpha * glowStrength), withAlpha(color, 0f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, hy, reachPx, paint)
        }
        glow(onArc(0f).first, dawnGlows[0], 0.34f * (1f - 0.6f * night))
        glow(onArc(1f).first, eveningGlows[0], 0.2f + 0.18f * night)
        paint.shader = null

        // Stars gather towards the evening end and brighten as night comes. Not on parchment: there is no night sky on paper.
        if (nightSky) {
            val random = Random(7)
            repeat(22) {
                val x = random.nextFloat()
                val y = random.nextFloat() * 0.8f
                val size = 0.5f + random.nextFloat() * 0.9f
                val towardsEvening = if (rtl) 1f - x else x
                val a = (0.2f + 0.8f * night) * (0.15f + 0.85f * towardsEvening) * (1f - y * 0.7f) * 0.8f
                paint.color = withAlpha(android.graphics.Color.WHITE, a)
                canvas.drawCircle(x * w, y * (hy - orbR), size * scale, paint)
            }
        }

        // The path: brighter where the day has been, quiet dots ahead.
        val steps = 48
        for (i in 0..steps) {
            val p = i / steps.toFloat()
            val (x, y) = onArc(p)
            val passed = p <= t
            paint.color = if (passed) {
                withAlpha(lerp(palette.morning, palette.evening, smoothstep(0.78f, 0.96f, p)).toArgb(), 0.85f)
            } else {
                withAlpha(palette.text.toArgb(), 0.22f)
            }
            canvas.drawCircle(x, y, (if (passed) 1.5f else 1.1f) * scale, paint)
        }

        // Horizon, brightest under the orb.
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(withAlpha(palette.text.toArgb(), 0f), withAlpha(palette.text.toArgb(), 0.5f), withAlpha(palette.text.toArgb(), 0f)),
            floatArrayOf(0f, (orbX / w).coerceIn(0.05f, 0.95f), 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, hy - scale / 2, w, hy + scale / 2, paint)
        paint.shader = null

        // The sun and the crescent share one place; one fades into the other.
        if (night < 1f) {
            canvas.saveLayerAlpha(null, ((1f - night) * 255).toInt())
            drawOrb(canvas, orbX, orbY, orbR, sun = true, sunHeight = sunHeight)
            canvas.restore()
        }
        if (night > 0f) {
            canvas.saveLayerAlpha(null, (night * 255).toInt())
            drawOrb(canvas, orbX, orbY, orbR, sun = false, sunHeight = 0f)
            canvas.restore()
        }
        return bitmap
    }

    /**
     * Arabic set in Amiri Quran, centred, right to left, at most [maxLines] lines. Glance text can
     * only use the phone's fonts, which vary widely in how they draw tashkeel.
     */
    fun arabic(context: Context, text: String, color: Color, shadow: Boolean, widthDp: Float, textSizeSp: Float, maxLines: Int): Bitmap {
        val scale = scale(context)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(R.font.amiri_quran)
            textSize = textSizeSp * scale * context.resources.configuration.fontScale
            this.color = color.toArgb()
            if (shadow) setShadowLayer(3 * scale, 0f, scale, 0x99000000.toInt())
        }
        val width = (widthDp * scale).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        // Room for the shadow, and for marks that reach past the line box.
        val pad = (4 * scale).toInt()
        val bitmap = Bitmap.createBitmap(width, layout.height + 2 * pad, Bitmap.Config.ARGB_8888).apply {
            density = (DisplayMetrics.DENSITY_DEFAULT * scale).toInt()
        }
        Canvas(bitmap).apply {
            translate(0f, pad.toFloat())
            layout.draw(this)
        }
        return bitmap
    }

    /** A glass sphere holding light, drawn as in [org.adhkaar.app.ui.components.NurOrb]. */
    private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, r: Float, sun: Boolean, sunHeight: Float) {
        val glows = if (sun) dawnGlows else eveningGlows
        val accent = if (sun) dawnAccent else eveningAccent
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Halo
        paint.shader = RadialGradient(
            cx, cy, r * 1.6f,
            intArrayOf(withAlpha(glows[0], 0.5f), withAlpha(glows[1], 0.16f), withAlpha(glows[1], 0f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r * 1.6f, paint)
        // Body: dark core, coloured edge.
        paint.shader = RadialGradient(
            cx, cy + r * 0.12f, r,
            intArrayOf(withAlpha(0xFF0A0F24.toInt(), 0.85f), withAlpha(glows[0], 0.32f), withAlpha(glows[1], 0.75f)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        // Light pooling at the bottom, like light through glass.
        paint.shader = RadialGradient(cx, cy + r * 0.85f, r * 0.7f, withAlpha(accent, 0.45f), withAlpha(accent, 0f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        if (sun) drawSunrise(canvas, cx, cy, r, sunHeight) else drawCrescent(canvas, cx, cy, r * 0.46f)
        // Rim light
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.05f
        paint.color = withAlpha(android.graphics.Color.WHITE, 0.4f)
        canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2, paint)
        paint.style = Paint.Style.FILL
        // Specular highlight
        val spec = RectF(cx - r * 0.62f, cy - r * 0.84f, cx + r * 0.18f, cy - r * 0.42f)
        paint.shader = RadialGradient(
            spec.centerX(), spec.centerY(), spec.width() / 2,
            intArrayOf(withAlpha(android.graphics.Color.WHITE, 0.32f), withAlpha(android.graphics.Color.WHITE, 0.08f), withAlpha(android.graphics.Color.WHITE, 0f)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawOval(spec, paint)
    }

    /** A sun over a glassy horizon: half-risen at [height] 0, clear of the horizon and shining towards 1. */
    private fun drawSunrise(canvas: Canvas, cx: Float, cy: Float, r: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val horizonY = cy + r * 0.22f
        val sunY = horizonY - (horizonY - (cy - r * 0.02f)) * height
        val sunR = r * (0.36f + 0.04f * height)
        val below = 1f - height
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r * 0.97f, Path.Direction.CW) })
        val corona = sunR * (2.6f + 0.9f * height)
        paint.shader = RadialGradient(
            cx, sunY, corona,
            intArrayOf(withAlpha(0xFFFFE2A8.toInt(), 0.85f + 0.15f * height), withAlpha(0xFFFFA86B.toInt(), 0.35f + 0.15f * height), withAlpha(0xFFFFA86B.toInt(), 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, sunY, corona, paint)
        paint.shader = null
        if (height > 0.3f) {
            val a = (height - 0.3f) / 0.7f
            paint.color = withAlpha(0xFFFFE9C2.toInt(), 0.35f * a)
            paint.strokeWidth = r * 0.035f
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 12) {
                val angle = i * PI / 6
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val from = sunR * 1.35f
                val to = sunR * (1.35f + 0.45f * a)
                canvas.drawLine(cx + dx * from, sunY + dy * from, cx + dx * to, sunY + dy * to, paint)
            }
        }
        // The disc is cut by the horizon while it is still rising.
        canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, horizonY + (cy + r - horizonY) * height)
        paint.shader = LinearGradient(0f, sunY - sunR, 0f, sunY + sunR, android.graphics.Color.WHITE, 0xFFFFE2B0.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, sunY, sunR, paint)
        canvas.restore()
        if (below > 0f) {
            // Water below the horizon, with the sun's reflection; it fades as the sun climbs.
            paint.shader = LinearGradient(
                0f, horizonY, 0f, cy + r,
                withAlpha(0xFF2A1430.toInt(), 0.55f * below), withAlpha(0xFF0B0714.toInt(), 0.85f * below), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(cx - r, horizonY, cx + r, cy + r, paint)
            paint.shader = LinearGradient(
                cx - r, 0f, cx + r, 0f,
                intArrayOf(withAlpha(android.graphics.Color.WHITE, 0f), withAlpha(android.graphics.Color.WHITE, 0.7f * below), withAlpha(android.graphics.Color.WHITE, 0f)), null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(cx - r, horizonY - r * 0.02f, cx + r, horizonY + r * 0.02f, paint)
        }
        canvas.restore()
    }

    private fun drawCrescent(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val x = cx - r * 0.08f
        val y = cy + r * 0.02f
        val crescent = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
        crescent.op(Path().apply { addCircle(x + r * 0.42f, y - r * 0.3f, r * 0.84f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        val warm = 0xFFFFE6B0.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Its glow first, blurred, then the crisp crescent over it.
        paint.color = withAlpha(warm, 0.75f)
        paint.maskFilter = BlurMaskFilter(r * 0.45f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(crescent, paint)
        paint.maskFilter = null
        paint.shader = LinearGradient(x - r, y - r, x + r, y + r, android.graphics.Color.WHITE, warm, Shader.TileMode.CLAMP)
        canvas.drawPath(crescent, paint)
    }

    // Gradients fade to the same colour at zero alpha, not to transparent black, which would grey the edge.
    private fun withAlpha(color: Int, alpha: Float): Int =
        (((color ushr 24) * alpha.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun smoothstep(from: Float, to: Float, x: Float): Float {
        val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }
}
