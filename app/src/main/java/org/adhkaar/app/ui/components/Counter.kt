package org.adhkaar.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.ui.theme.ChartColors
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Type

/**
 * The tap counter: a glass sphere whose ring fills with light as you count.
 * The number rolls up, a band of light moves out through the glass on each tap (a bigger, slower
 * gold one on the last count), and it turns gold when done.
 *
 * [readiness] (0–1) is how far along the wait before the next count may be taken. While waiting, a
 * faint inner arc fills towards it and fades as it closes. It is read while drawing, so a wait
 * animating every frame redraws the orb without recomposing it.
 */
@Composable
fun CounterOrb(
    count: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    readiness: () -> Float = { 1f },
    onTap: () -> Unit,
) {
    val aura = LocalAura.current
    val done = count >= target
    val progress by animateFloatAsState((count.toFloat() / target).coerceIn(0f, 1f), Motion.move(), label = "ring")
    val ring by animateColorAsState(if (done) Nur.gold else aura.accent, tween(400), label = "ringColor")
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(if (count >= target) 1_100 else 700, easing = Motion.emphasized))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .pressable(scale = 0.94f, haptic = false, interaction = interaction, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    // Gradients and strokes are built when the size, count or colour changes; a tap's
                    // wave and the wait for the next count only redraw with them.
                    val r = this.size.minDimension / 2
                    val center = this.size.center
                    // Glass sphere
                    val glass = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.02f), 0.8f to Color.White.copy(alpha = 0.06f),
                        1f to ring.copy(alpha = 0.18f + 0.2f * progress),
                        center = center, radius = r,
                    )
                    val highlight = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.22f), 1f to Color.Transparent,
                        center = Offset(center.x - r * 0.3f, center.y - r * 0.55f), radius = r * 0.6f,
                    )
                    val strength = if (done) 1.8f else 1f
                    val hairline = Stroke(1.5.dp.toPx())
                    // Track + progress (with a wider, faint pass for glow)
                    val stroke = 5.dp.toPx()
                    val inset = 12.dp.toPx()
                    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    val track = Stroke(stroke)
                    val progressGlow = Stroke(stroke * 3.2f, cap = StrokeCap.Round)
                    val progressLine = Stroke(stroke, cap = StrokeCap.Round)
                    val waitInset = inset + stroke + 4.dp.toPx()
                    val waitTopLeft = Offset(waitInset, waitInset)
                    val waitSize = Size(this.size.width - waitInset * 2, this.size.height - waitInset * 2)
                    val waitLine = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                    val rim = Stroke(1.dp.toPx())
                    val rimRadius = r - 0.5.dp.toPx()
                    onDrawBehind {
                        drawCircle(glass)
                        drawCircle(highlight)
                        // The tap: a soft band of light travels out from the centre through the glass, which
                        // brightens for a moment. The last count sends a stronger one.
                        val wave = ripple.value
                        if (wave < 1f) {
                            val fade = 1f - wave
                            val band = r * (0.2f + 0.9f * wave)
                            drawCircle(
                                Brush.radialGradient(
                                    0f to Color.Transparent, 0.62f to Color.Transparent,
                                    0.84f to ring.copy(alpha = (0.26f * strength * fade).coerceAtMost(1f)), 1f to Color.Transparent,
                                    center = center, radius = band,
                                ),
                                band,
                            )
                            drawCircle(ring.copy(alpha = 0.08f * strength * fade * fade))
                            drawCircle(ring.copy(alpha = 0.32f * fade), radius = r * (0.8f + 0.2f * wave), style = hairline)
                        }
                        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = track)
                        drawArc(ring.copy(alpha = 0.22f), -90f, 360f * progress, false, topLeft, arcSize, style = progressGlow)
                        drawArc(ring, -90f, 360f * progress, false, topLeft, arcSize, style = progressLine)
                        // The wait for the next count: a hairline inside the track, gone over its last tenth.
                        val wait = readiness().coerceIn(0f, 1f)
                        if (!done && wait < 1f) {
                            drawArc(
                                ring.copy(alpha = 0.35f * ((1f - wait) * 10f).coerceAtMost(1f)),
                                -90f, 360f * wait, false, waitTopLeft, waitSize, style = waitLine,
                            )
                        }
                        // Rim
                        drawCircle(Color.White.copy(alpha = 0.14f), rimRadius, style = rim)
                    }
                },
        )
        AnimatedContent(
            targetState = done to count,
            transitionSpec = {
                if (targetState.first && !initialState.first) {
                    (scaleIn(spring(dampingRatio = 0.55f)) + fadeIn()) togetherWith fadeOut(tween(120))
                } else {
                    (slideInVertically(Motion.move()) { it / 2 } + fadeIn(tween(140))) togetherWith
                        (slideOutVertically(Motion.move()) { -it / 2 } + fadeOut(tween(100)))
                }
            },
            label = "count",
        ) { (isDone, value) ->
            if (isDone) {
                Icon(Icons.Rounded.Check, stringResource(R.string.counter_done), tint = Nur.gold, modifier = Modifier.size(size * 0.3f))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$value",
                        style = Type.stat.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
                    )
                    Text(stringResource(R.string.counter_of_target, target), style = Type.caption)
                }
            }
        }
    }
}

data class DayRing(val label: String, val morning: Boolean, val evening: Boolean, val isToday: Boolean)

/** Seven days, two concentric rings each: dawn outside, evening inside. */
@Composable
fun WeekRings(days: List<DayRing>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.label,
                    style = Type.caption,
                    color = if (day.isToday) Nur.textPrimary else Nur.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.size(34.dp)) {
                    val outer = 3.dp.toPx()
                    fun ring(inset: Float, color: Color, filled: Boolean) {
                        val s = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(
                            if (filled) color else Color.White.copy(alpha = 0.09f),
                            -90f, 360f, false, Offset(inset, inset), s, style = Stroke(outer, cap = StrokeCap.Round),
                        )
                    }
                    ring(outer / 2, ChartColors.morning, day.morning)
                    ring(outer / 2 + 5.dp.toPx(), ChartColors.evening, day.evening)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .then(if (day.isToday) Modifier.background(Nur.textPrimary) else Modifier),
                )
            }
        }
    }
}

