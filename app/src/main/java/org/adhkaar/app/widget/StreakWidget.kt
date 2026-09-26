package org.adhkaar.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.ui.components.DayRing
import org.adhkaar.app.ui.home.weekOf
import java.time.LocalDate

/** Streak: the flame, the current streak, and this week's mornings and evenings. */
class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context, id)
        val history = SessionState.get(context).historyFlow.value
        val today = LocalDate.now()
        val streak = Streaks.current(history, today)
        // The same week as the Today screen and the streak sheet, Sunday first.
        val week = weekOf(today, history)
        provideContent { StreakContent(context, look, streak, week) }
    }
}

class StreakWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

@Composable
private fun StreakContent(context: Context, look: WidgetLook, streak: Int, week: List<DayRing>) {
    val palette = look.palette
    WidgetFrame(look, openAppAction(context), padding = 14.dp) {
        DirRow(look.dir) {
            item { Image(ImageProvider(R.drawable.widget_flame), null, modifier = GlanceModifier.size(22.dp)) }
            item { Spacer(GlanceModifier.width(6.dp)) }
            item { Text("$streak", style = look.text(palette.text, 30.sp, FontWeight.Medium), maxLines = 1) }
        }
        Text(
            if (streak > 0) context.resources.getQuantityString(R.plurals.widget_days_in_a_row, streak) else context.getString(R.string.widget_streak_start),
            style = look.text(palette.secondary, 11.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        DirRow(look.dir, GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            week.forEach { day ->
                item {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        WeekDot(palette, palette.morning.takeIf { day.morning })
                        Spacer(GlanceModifier.height(3.dp))
                        WeekDot(palette, palette.evening.takeIf { day.evening })
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            day.label,
                            style = look.text(
                                if (day.isToday) palette.text else palette.tertiary, 9.sp,
                                if (day.isToday) FontWeight.Bold else FontWeight.Normal, TextAlign.Center,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Morning above, evening below: one dot each, lit in [done]'s colour once that session is done. */
@Composable
private fun WeekDot(palette: WidgetPalette, done: Color?) {
    val modifier = GlanceModifier.size(7.dp)
    if (done != null) {
        Image(ImageProvider(R.drawable.widget_dot), null, modifier = modifier, colorFilter = ColorFilter.tint(ColorProvider(done)))
    } else {
        Image(ImageProvider(palette.emptyDot), null, modifier = modifier)
    }
}
