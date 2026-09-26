package org.adhkaar.app.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.ui.theme.ChartColors
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Why the session can't be started by hand yet: "Opens at 4:45 PM" or "Opens tomorrow at 6:05 AM".
 * Just the time: the window opens after the prayer has been prayed, not at the prayer itself.
 * Null when it can be started.
 */
fun opensLabel(context: Context, status: AdhkaarWindows.Status, now: ZonedDateTime): String? {
    val window = when (status) {
        is AdhkaarWindows.Status.NotYet -> status.window
        is AdhkaarWindows.Status.Closed -> status.next
        else -> return null
    }
    val tomorrow = window.opens.toLocalDate() != now.toLocalDate()
    return context.getString(if (tomorrow) R.string.window_opens_time_tomorrow else R.string.window_opens_time, formatTime(context, window.opens))
}

/** "Past the best time · open until 12:30 PM" while the session is late, else null. */
fun lateLabel(context: Context, status: AdhkaarWindows.Status): String? =
    (status as? AdhkaarWindows.Status.Late)?.let { context.getString(R.string.window_late_open_until, formatTime(context, it.window.closes)) }

enum class DayMark { NONE, MORNING, EVENING, BOTH }

/**
 * "Both sessions" is a clean diagonal split, morning over evening. A smooth blend of the two
 * passes through grey and reads as a third colour; a hard split reads as literally both.
 */
fun bothBrush(start: Offset, end: Offset) = Brush.linearGradient(
    0f to ChartColors.morning, 0.5f to ChartColors.morning, 0.5f to ChartColors.evening, 1f to ChartColors.evening,
    start = start, end = end,
)

/**
 * Last 30 days as a 10×3 grid, oldest first. Colours match the week rings:
 * dawn = morning, blue = evening, both colours = both.
 */
@Composable
fun MonthGrid(days: List<DayMark>, modifier: Modifier = Modifier) {
    val columns = 10
    Canvas(modifier.fillMaxWidth().height(((days.size + columns - 1) / columns * 30).dp)) {
        val gap = 6.dp.toPx()
        val cell = (size.width - gap * (columns - 1)) / columns
        val rows = (days.size + columns - 1) / columns
        val cellH = (size.height - gap * (rows - 1)) / rows
        val side = minOf(cell, cellH)
        days.forEachIndexed { i, mark ->
            val x = (i % columns) * (cell + gap) + (cell - side) / 2
            val y = (i / columns) * (cellH + gap) + (cellH - side) / 2
            val topLeft = Offset(x, y)
            val s = Size(side, side)
            val radius = CornerRadius(side * 0.3f)
            when (mark) {
                DayMark.NONE -> drawRoundRect(Color.White.copy(alpha = 0.07f), topLeft, s, radius)
                DayMark.MORNING -> drawRoundRect(ChartColors.morning, topLeft, s, radius)
                DayMark.EVENING -> drawRoundRect(ChartColors.evening, topLeft, s, radius)
                DayMark.BOTH -> drawRoundRect(bothBrush(topLeft, Offset(x + side, y + side)), topLeft, s, radius)
            }
            if (i == days.lastIndex) {
                drawRoundRect(Color.White, topLeft, s, radius, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/**
 * Light for the completion moment: slow rays turning behind the orb, and a single burst of
 * gold motes that drift out and fade.
 */
@Composable
fun Celebration(modifier: Modifier = Modifier, color: Color = Nur.gold) {
    val turn by rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "turn",
    )
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(2200, easing = org.adhkaar.app.ui.theme.Motion.emphasized)) }
    val motes = remember {
        val r = Random(3)
        List(28) { Triple(r.nextFloat() * 360f, 0.55f + r.nextFloat() * 0.6f, 1.2f + r.nextFloat() * 2.2f) }
    }
    Spacer(
        modifier.drawWithCache {
            val c = size.center
            val reach = size.minDimension * 0.62f
            // The rays only turn, so each one's gradient is built once and the turn is applied while drawing.
            val rays = List(12) { i ->
                val a = Math.toRadians(i * 30.0)
                val end = Offset(c.x + cos(a).toFloat() * reach, c.y + sin(a).toFloat() * reach)
                end to Brush.linearGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent), c, end)
            }
            val rayWidth = size.minDimension * 0.05f
            onDrawBehind {
                rotate(turn, c) {
                    rays.forEach { (end, brush) -> drawLine(brush, c, end, strokeWidth = rayWidth) }
                }
                val t = burst.value
                // Once the burst is over the motes are fully faded, so they are no longer drawn.
                if (t < 1f) {
                    motes.forEach { (angle, distance, radius) ->
                        val a = Math.toRadians(angle.toDouble())
                        val d = reach * distance * t
                        val p = Offset(c.x + cos(a).toFloat() * d, c.y + sin(a).toFloat() * d)
                        drawCircle(color.copy(alpha = (1f - t) * 0.9f), radius.dp.toPx(), p)
                    }
                }
            }
        },
    )
}

@Composable
fun LegendDot(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(8.dp).height(8.dp)) {
            if (color != null) drawCircle(color) else drawCircle(bothBrush(Offset.Zero, Offset(size.width, size.height)))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = Type.caption, textAlign = TextAlign.Start)
    }
}

