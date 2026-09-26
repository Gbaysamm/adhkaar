package org.adhkaar.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.WeekRings
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.LocalDate

/**
 * Streak lengths worth marking. 40 is there on purpose: forty days is a span that carries weight
 * in the tradition. Past the list, every full year is the next one.
 */
private val Milestones = listOf(3, 7, 14, 30, 40, 100, 200, 365)

private fun nextMilestone(streak: Int): Int = Milestones.firstOrNull { it > streak } ?: ((streak / 365 + 1) * 365)

private fun previousMilestone(streak: Int): Int = Milestones.lastOrNull { it <= streak }?.let { maxOf(it, streak / 365 * 365) } ?: 0

/**
 * Opened from the streak pill on Today: the current streak large with its flame, the best one,
 * this week's rings, how far the next milestone is, and one warm line for where the user stands.
 */
@Composable
fun StreakSheet(history: Set<String>, today: LocalDate, onDismiss: () -> Unit) {
    val current = Streaks.current(history, today)
    val best = Streaks.best(history, today)
    val doneToday = Streaks.anyDone(history, today)
    val next = nextMilestone(current)
    val from = previousMilestone(current)
    // The bar fills on open, so reaching toward the milestone is felt rather than just read.
    val fill = remember { Animatable(0f) }
    LaunchedEffect(current) { fill.animateTo((current - from).toFloat() / (next - from), Motion.enter()) }

    GlassDialog(stringResource(R.string.streak_title), onDismiss) {
        // The moment: flame and number centred, in a soft gold light.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(Nur.gold.copy(alpha = 0.32f), Color.Transparent))) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Nur.gold, modifier = Modifier.size(40.dp))
            }
            Text("$current", style = Type.countdown.copy(fontSize = 60.sp, lineHeight = 64.sp))
            Text(pluralStringResource(R.plurals.insights_day_streak, current), style = Type.caption)
        }
        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.insights_best_streak), style = Type.label, modifier = Modifier.weight(1f))
            Text("$best", style = Type.stat)
        }
        Spacer(Modifier.height(Space.l))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        Spacer(Modifier.height(Space.l))
        Text(stringResource(R.string.today_section_this_week), style = Type.label)
        Spacer(Modifier.height(Space.m))
        WeekRings(weekOf(today, history))
        Spacer(Modifier.height(Space.l))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        Spacer(Modifier.height(Space.l))
        Text(pluralStringResource(R.plurals.streak_to_milestone, next - current, next - current, next), style = Type.label)
        Spacer(Modifier.height(Space.s))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(fill.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(Nur.gold))
        }
        Spacer(Modifier.height(Space.l))
        Text(
            stringResource(
                when {
                    current == 0 -> R.string.streak_line_start
                    !doneToday -> R.string.streak_line_waiting
                    current >= best && current > 1 -> R.string.streak_line_best
                    else -> R.string.streak_line_done
                },
            ),
            style = Type.bodyM,
        )
        Spacer(Modifier.height(Space.xl))
        SecondaryButton(stringResource(R.string.streak_close), onClick = onDismiss)
    }
}
