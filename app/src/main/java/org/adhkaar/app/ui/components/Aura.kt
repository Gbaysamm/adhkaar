package org.adhkaar.app.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.adhkaar.app.ui.theme.Aura
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import kotlin.math.roundToInt
import kotlin.random.Random

/** Soft light from three drifting glows over a dark base, with fine grain so gradients don't band. */
@Composable
fun AuraBackground(
    modifier: Modifier = Modifier,
    aura: Aura = LocalAura.current,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val driftFrame = rememberInfiniteTransition(label = "aura").animateFloat(
        0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    // The glows cross a small fraction of a pixel per frame, so the backdrop (and the blur of it
    // under the tab bar) is redrawn only when they have moved a step: under a pixel even on a
    // tablet, about 18 times a second instead of on every frame.
    val drift by remember { derivedStateOf { (driftFrame.value * DRIFT_STEPS).roundToInt() / DRIFT_STEPS } }
    val grain = rememberGrain()
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            // Built once per size; the drift only moves them while drawing.
            val base = Brush.verticalGradient(aura.base)
            val first = Glow(aura.glows[0], w * 1.0f, 0.55f * intensity)
            val second = Glow(aura.glows[1], w * 0.8f, 0.36f * intensity)
            val third = Glow(aura.glows[2], w * 0.85f, 0.24f * intensity)
            onDrawBehind {
                drawRect(Nur.ink)
                drawRect(base)
                drawGlow(first, Offset(w * (0.12f + 0.12f * drift), h * (0.06f + 0.03f * drift)))
                drawGlow(second, Offset(w * (0.98f - 0.1f * drift), h * 0.34f))
                drawGlow(third, Offset(w * (0.2f + 0.08f * drift), h * (1.02f - 0.04f * drift)))
                drawRect(grain)
            }
        },
        content = content,
    )
}

private const val DRIFT_STEPS = 400f

/** A soft radial glow, its gradient made around the origin so it can be moved without rebuilding it. */
private class Glow(color: Color, val radius: Float, alpha: Float) {
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = alpha),
        0.45f to color.copy(alpha = alpha * 0.32f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = radius,
    )
}

private fun DrawScope.drawGlow(glow: Glow, center: Offset) {
    translate(center.x, center.y) { drawCircle(glow.brush, glow.radius, Offset.Zero) }
}

@Composable
private fun rememberGrain(): Brush = remember {
    val sizePx = 128
    val random = Random(42)
    val pixels = IntArray(sizePx * sizePx) {
        val v = random.nextInt(256)
        val a = random.nextInt(9) // 0–3.5% alpha: felt more than seen
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

enum class OrbSymbol { Crescent, Sunrise }

/**
 * The brand mark: a glass sphere holding light. A crescent for the evening (and the brand),
 * a sun rising over a horizon for the morning. It floats slowly.
 * [bloom] (0–1) intensifies the glow, used for the completion moment.
 */
@Composable
fun NurOrb(
    size: Dp,
    modifier: Modifier = Modifier,
    aura: Aura = LocalAura.current,
    symbol: OrbSymbol = if (aura == Auras.dawn) OrbSymbol.Sunrise else OrbSymbol.Crescent,
    bloom: Float = 0f,
    float: Boolean = true,
    sunHeight: Float = 0f,
) {
    NurOrb(size, modifier, aura, symbol, bloom, float, sunHeight = { sunHeight })
}

/**
 * [NurOrb] for a sun that moves every frame: [sunHeight] is read while drawing, so the orb is
 * redrawn without recomposing whatever holds it.
 */
@Composable
fun NurOrb(
    size: Dp,
    modifier: Modifier = Modifier,
    aura: Aura = LocalAura.current,
    symbol: OrbSymbol = if (aura == Auras.dawn) OrbSymbol.Sunrise else OrbSymbol.Crescent,
    bloom: Float = 0f,
    float: Boolean = true,
    sunHeight: () -> Float,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val bob by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(5_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val shimmer by transition.animateFloat(
        0.85f, 1f, infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "shimmer",
    )
    Spacer(
        modifier
            .size(size)
            .graphicsLayer { translationY = if (float) bob * 3.dp.toPx() else 0f }
            .drawWithCache {
                // Only the halo shimmers. Everything else is fixed for a size, aura, bloom and sun
                // height, so its gradients, paths and blur are built here once, not on every frame.
                val r = this.size.minDimension / 2f
                val c = this.size.center
                val haloRadius = r * (1.9f + 0.6f * bloom)
                val haloEdge = aura.glows[1].copy(alpha = 0.14f + 0.2f * bloom)
                // Body: dark core, coloured edge (fresnel).
                val body = Brush.radialGradient(
                    0f to Color(0xFF0A0F24).copy(alpha = 0.85f),
                    0.62f to aura.glows[0].copy(alpha = 0.32f),
                    1f to aura.glows[1].copy(alpha = 0.75f),
                    center = Offset(c.x, c.y + r * 0.12f), radius = r,
                )
                // Light pooling at the bottom, like light through glass.
                val pool = Brush.radialGradient(
                    0f to aura.accent.copy(alpha = 0.45f), 1f to Color.Transparent,
                    center = Offset(c.x, c.y + r * 0.85f), radius = r * 0.7f,
                )
                val drawSymbol = if (symbol == OrbSymbol.Sunrise) sunrise(c, r, bloom, sunHeight()) else crescent(c, r * 0.46f, bloom)
                val rim = Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.75f),
                    ),
                    center = c,
                )
                val rimRadius = r - 0.75.dp.toPx()
                val rimStroke = Stroke(1.5.dp.toPx())
                val spec = Rect(Offset(c.x - r * 0.62f, c.y - r * 0.84f), Size(r * 0.8f, r * 0.42f))
                val specular = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.32f), 0.6f to Color.White.copy(alpha = 0.08f), 1f to Color.Transparent,
                    center = spec.center, radius = spec.width / 2f,
                )
                onDrawBehind {
                    // Halo
                    drawCircle(
                        Brush.radialGradient(
                            0f to aura.glows[0].copy(alpha = (0.45f + 0.35f * bloom) * shimmer),
                            0.5f to haloEdge,
                            1f to Color.Transparent,
                            center = c, radius = haloRadius,
                        ),
                        haloRadius, c,
                    )
                    drawCircle(body, r, c)
                    drawCircle(pool, r, c)
                    drawSymbol()
                    // Rim light
                    drawCircle(rim, rimRadius, c, style = rimStroke)
                    // Specular highlight
                    drawOval(specular, topLeft = spec.topLeft, size = spec.size)
                }
            },
    )
}

/**
 * A sun over a glassy horizon, clipped to the sphere. [height] 0 is half-risen at dawn; towards 1
 * it climbs clear of the horizon, which fades away, and shines fully, as at midday.
 * Builds the drawing once; the returned block only draws it.
 */
private fun CacheDrawScope.sunrise(c: Offset, r: Float, bloom: Float, height: Float): DrawScope.() -> Unit {
    val sphere = Path().apply { addOval(Rect(c, r * 0.97f)) }
    val horizonY = c.y + r * 0.22f
    val sun = Offset(c.x, horizonY - (horizonY - (c.y - r * 0.02f)) * height)
    val sunR = r * (0.36f + 0.04f * height)
    val below = 1f - height
    // Corona, wider and brighter as the sun climbs.
    val corona = sunR * (2.6f + 0.8f * bloom + 0.9f * height)
    val coronaBrush = Brush.radialGradient(
        0f to Color(0xFFFFE2A8).copy(alpha = 0.85f + 0.15f * height),
        0.5f to Color(0xFFFFA86B).copy(alpha = 0.35f + 0.2f * bloom + 0.15f * height),
        1f to Color.Transparent, center = sun, radius = corona,
    )
    // Rays, only once the sun is well up.
    val rayStrength = (height - 0.3f) / 0.7f
    val rays = if (height > 0.3f) {
        List(12) { i ->
            val angle = i * (Math.PI / 6).toFloat()
            val dir = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
            sun + dir * (sunR * 1.35f) to sun + dir * (sunR * (1.35f + 0.45f * rayStrength))
        }
    } else {
        emptyList()
    }
    val rayColor = Color(0xFFFFE9C2).copy(alpha = (0.35f * rayStrength).coerceIn(0f, 1f))
    // Disc, clipped at the horizon while it is still rising.
    val discBottom = horizonY + (c.y + r - horizonY) * height
    val disc = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE2B0)), startY = sun.y - sunR, endY = sun.y + sunR * height)
    // Water / glass below the horizon, with a soft reflection. It fades as the sun climbs.
    val water = Brush.verticalGradient(
        listOf(Color(0xFF2A1430).copy(alpha = 0.55f * below), Color(0xFF0B0714).copy(alpha = 0.85f * below)),
        startY = horizonY, endY = c.y + r,
    )
    val reflection = Brush.radialGradient(
        0f to Color(0xFFFFD9A0).copy(alpha = 0.55f * below), 1f to Color.Transparent,
        center = Offset(sun.x, horizonY + sunR * 0.35f), radius = sunR * 1.1f,
    )
    val horizon = Brush.horizontalGradient(
        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.7f * below), 1f to Color.Transparent,
        startX = c.x - r, endX = c.x + r,
    )
    val horizonStroke = 1.dp.toPx()
    return {
        clipPath(sphere) {
            drawCircle(coronaBrush, corona, sun)
            rays.forEach { (from, to) -> drawLine(rayColor, from, to, strokeWidth = r * 0.035f, cap = StrokeCap.Round) }
            clipRect(bottom = discBottom) { drawCircle(disc, sunR, sun) }
            if (below > 0f) {
                drawRect(water, topLeft = Offset(c.x - r, horizonY), size = Size(r * 2, r))
                drawOval(
                    reflection,
                    topLeft = Offset(sun.x - sunR * 1.1f, horizonY + sunR * 0.1f), size = Size(sunR * 2.2f, sunR * 0.5f),
                )
                drawLine(horizon, Offset(c.x - r, horizonY), Offset(c.x + r, horizonY), strokeWidth = horizonStroke)
            }
        }
    }
}

/** The crescent and its warm glow. Builds the shape and the blur once; the returned block only draws them. */
private fun CacheDrawScope.crescent(c: Offset, r: Float, bloom: Float): DrawScope.() -> Unit {
    val center = Offset(c.x - r * 0.08f, c.y + r * 0.02f)
    val outer = Path().apply { addOval(Rect(center, r)) }
    val inner = Path().apply { addOval(Rect(Offset(center.x + r * 0.42f, center.y - r * 0.3f), r * 0.84f)) }
    val crescent = Path.combine(PathOperation.Difference, outer, inner)
    val warm = Color(0xFFFFE6B0)
    val glow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = warm.copy(alpha = 0.75f + 0.25f * bloom).toArgb()
            maskFilter = BlurMaskFilter(r * (0.45f + 0.3f * bloom), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }
    val fill = Brush.linearGradient(listOf(Color.White, warm), start = Offset(center.x - r, center.y - r), end = Offset(center.x + r, center.y + r))
    return {
        if (glow != null) drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(crescent.asAndroidPath(), glow) }
        drawPath(crescent, fill)
    }
}
