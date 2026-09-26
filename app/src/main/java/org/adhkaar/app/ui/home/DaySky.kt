package org.adhkaar.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.OrbSymbol
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.Duration
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One end of the day's arc: the morning or the evening adhkaar. */
data class SkyEnd(val label: String, val detail: String, val done: Boolean)

/**
 * Where the day is, from Fajr (0) to Maghrib (1). From Maghrib until the next Fajr it is night,
 * so the crescent rests at the evening end, including the hours after midnight.
 */
fun dayProgress(now: LocalTime, fajr: LocalTime, maghrib: LocalTime): Float {
    val total = Duration.between(fajr, maghrib).toMinutes().toFloat()
    if (total <= 0f || now.isBefore(fajr)) return 1f
    return (Duration.between(fajr, now).toMinutes() / total).coerceIn(0f, 1f)
}

/**
 * The day as one arc over still water: the sun rises at the morning end, travels, and becomes
 * the crescent at the evening end. The orb sits where the day is now; the sky warms towards the
 * morning end and cools and fills with stars towards the evening end.
 * Morning is on the start side, so the day reads right to left in Arabic and Urdu.
 */
@Composable
fun DaySky(progress: Float, morning: SkyEnd, evening: SkyEnd, modifier: Modifier = Modifier) {
    // The orb travels to where the day is when the screen opens. Where it is on the way is read
    // only while placing and drawing, so the journey moves the scene without recomposing it.
    val travel = remember { Animatable(0f) }
    LaunchedEffect(progress) { travel.animateTo(progress, tween(1_800, easing = FastOutSlowInEasing)) }
    // All composition needs is which of the sun and the crescent are showing.
    val showSun by remember { derivedStateOf { nightAt(travel.value) < 1f } }
    val showCrescent by remember { derivedStateOf { nightAt(travel.value) > 0f } }
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(8_000, easing = LinearEasing)), label = "twinkle",
    )
    val stars = remember {
        val r = Random(7)
        List(40) { Triple(Offset(r.nextFloat(), r.nextFloat() * 0.8f), 0.5f + r.nextFloat() * 1.1f, r.nextFloat() * 6.28f) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val orbSize = 56.dp
    val warm = Auras.dawn
    val cool = Auras.evening

    BoxWithConstraints(modifier.fullBleed(Space.gutter).height(248.dp)) {
        val w = with(density) { maxWidth.toPx() }
        val hy = with(density) { (maxHeight * 0.72f).toPx() }
        val orbR = with(density) { (orbSize / 2).toPx() }
        // The arc: a slice of a true circle standing on the horizon, like the sun's path.
        val cx = w / 2
        val half = w * 0.38f
        val rise = hy - orbR * 3.2f
        val radius = (half * half + rise * rise) / (2 * rise)
        val centreY = hy - rise + radius
        val span = kotlin.math.asin(half / radius)
        fun onArc(p: Float): Offset {
            val a = -span + 2 * span * p
            val x = cx + radius * sin(a)
            return Offset(if (rtl) w - x else x, centreY - radius * cos(a))
        }
        // The orb stays centred on the line, travelling only where it clears the water.
        val clear = with(density) { 4.dp.toPx() }
        val reach = kotlin.math.acos(((radius - rise + orbR + clear) / radius).coerceIn(-1f, 1f))
        val ends = ((span - reach) / (2 * span)).coerceIn(0f, 0.3f)
        fun orbAt(t: Float) = onArc(ends + (1 - 2 * ends) * t)

        // The sky behind the stars.
        Canvas(Modifier.fillMaxSize()) {
            val t = travel.value
            val night = nightAt(t)
            val sunHeight = sunHeightAt(t)
            val morningX = onArc(0f).x
            val eveningX = onArc(1f).x
            // The two ends of the day, each glowing its own colour at the horizon.
            drawCircle(
                Brush.radialGradient(0f to warm.glows[0].copy(alpha = 0.34f * (1f - 0.6f * night)), 1f to Color.Transparent, center = Offset(morningX, hy), radius = w * 0.42f),
                w * 0.42f, Offset(morningX, hy),
            )
            drawCircle(
                Brush.radialGradient(0f to cool.glows[0].copy(alpha = 0.2f + 0.18f * night), 1f to Color.Transparent, center = Offset(eveningX, hy), radius = w * 0.42f),
                w * 0.42f, Offset(eveningX, hy),
            )
            // Around midday, the sun lights the sky around it.
            if (sunHeight > 0f && night < 1f) {
                val orb = orbAt(t)
                val glow = w * 0.35f
                drawCircle(
                    Brush.radialGradient(0f to Color(0xFFFFC98A).copy(alpha = 0.22f * sunHeight * (1f - night)), 1f to Color.Transparent, center = orb, radius = glow),
                    glow, orb,
                )
            }
        }
        // Stars gather towards the evening end, and brighten as night comes. They twinkle on every
        // frame, so they have a layer of their own and the rest of the scene is not redrawn with them.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val night = nightAt(travel.value)
            stars.forEach { (p, radius, phase) ->
                val towardsEvening = if (rtl) 1f - p.x else p.x
                val a = (0.2f + 0.8f * night) * (0.15f + 0.85f * towardsEvening) * (0.35f + 0.65f * ((sin(twinkle + phase) + 1f) / 2f))
                drawCircle(Color.White.copy(alpha = a * (1f - p.y * 0.7f)), radius.dp.toPx(), Offset(p.x * w, p.y * (hy - orbR)))
            }
        }
        // The path and the water, in front of the stars.
        Canvas(Modifier.fillMaxSize()) {
            val t = travel.value
            val orb = orbAt(t)
            val light = lerp(warm.accent, cool.accent, nightAt(t))
            // The path: quiet dots ahead, brighter where the day has been.
            val steps = 56
            for (i in 0..steps) {
                val p = i / steps.toFloat()
                val at = onArc(p)
                val passed = p <= t
                drawCircle(
                    (if (passed) lerp(warm.accent, cool.accent, smoothstep(0.78f, 0.96f, p)) else Color.White).copy(alpha = if (passed) 0.55f else 0.16f),
                    (if (passed) 1.6f else 1.2f).dp.toPx(), at,
                )
            }
            // Water below the horizon, fading into the page.
            drawRect(
                Brush.verticalGradient(0f to Color(0xFF04060F).copy(alpha = 0.55f), 1f to Color.Transparent, startY = hy, endY = size.height),
                topLeft = Offset(0f, hy), size = androidx.compose.ui.geometry.Size(w, size.height - hy),
            )
            // The orb's light on the water, directly beneath it.
            listOf(0.08f to 0.9f, 0.2f to 0.6f, 0.34f to 0.36f).forEachIndexed { i, (dy, len) ->
                val y = hy + (size.height - hy) * dy
                val half = orbR * 1.4f * len
                drawLine(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to light.copy(alpha = 0.5f - i * 0.12f), 1f to Color.Transparent,
                        startX = orb.x - half, endX = orb.x + half,
                    ),
                    Offset(orb.x - half, y), Offset(orb.x + half, y), strokeWidth = (2f - i * 0.4f).dp.toPx(),
                )
            }
            // Horizon, brightest under the orb.
            drawLine(
                Brush.horizontalGradient(
                    0f to Color.Transparent, (orb.x / w).coerceIn(0.05f, 0.95f) to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
                    startX = 0f, endX = w,
                ),
                Offset(0f, hy), Offset(w, hy), strokeWidth = 1.dp.toPx(),
            )
        }

        // The sun and the crescent share one place; one fades into the other.
        // A faded layer clips to its bounds, so each orb gets room for its halo.
        val halo = orbSize
        // Absolute placement: the arc is already mirrored for right-to-left.
        Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset {
                    val orb = orbAt(travel.value)
                    IntOffset((orb.x - orbR - halo.toPx()).toInt(), (orb.y - orbR - halo.toPx()).toInt())
                },
        ) {
            if (showSun) {
                Box(Modifier.graphicsLayer { alpha = 1f - nightAt(travel.value) }.padding(halo)) {
                    NurOrb(orbSize, aura = warm, symbol = OrbSymbol.Sunrise, sunHeight = { sunHeightAt(travel.value) })
                }
            }
            if (showCrescent) {
                Box(Modifier.graphicsLayer { alpha = nightAt(travel.value) }.padding(halo)) { NurOrb(orbSize, aura = cool, symbol = OrbSymbol.Crescent) }
            }
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = Space.gutter, vertical = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EndLabel(morning, warm.accent, Alignment.Start)
            EndLabel(evening, cool.accent, Alignment.End)
        }
    }
}

/** How far night has fallen with the orb at [t] of the day: it becomes the crescent near Maghrib. */
private fun nightAt(t: Float) = smoothstep(0.78f, 0.96f, t)

/** How high the sun is at [t]: it climbs to full height at midday and sinks again. */
private fun sunHeightAt(t: Float) = smoothstep(0f, 1f, sin(PI * t).toFloat() * 1.25f - 0.15f)

@Composable
private fun EndLabel(end: SkyEnd, accent: Color, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(end.label.uppercase(), style = Type.overline)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (end.done) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Space.xs))
            }
            Text(end.detail, style = Type.caption.copy(color = if (end.done) accent else Nur.textSecondary))
        }
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/** Lets the scene run edge to edge, past the screen's side gutter. */
private fun Modifier.fullBleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
