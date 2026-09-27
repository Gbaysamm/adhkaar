package org.adhkaar.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.R
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.ui.MainActivity
import org.adhkaar.app.ui.components.formatCountdown
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.home.heroSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** What the Today and Next session widgets show, read from the same state as the Today screen. */
data class WidgetData(
    val type: SessionType,
    val time: String?,
    val day: String?,
    val waiting: Boolean,
    val morningDone: Boolean,
    val eveningDone: Boolean,
    val streak: Int,
    /** When the session can't be started yet, when it opens (e.g. "4:05 PM"); null when it can start. */
    val opensAt: String?,
    /** "in 4h 37m" until the session time; null while it is waiting or less than a minute away. */
    val countdown: String?,
) {
    companion object {
        fun load(context: Context): WidgetData {
            val state = SessionState.get(context)
            val now = ZonedDateTime.now()
            val today = LocalDate.now()
            val pending = state.pending
            val type = heroSession(context, pending, now)
            val next = AlarmScheduler.nextTime(context, type, now)
            val status = AdhkaarWindows.status(SettingsStore.get(context).current, type, now)
            val opens = when (status) {
                is AdhkaarWindows.Status.NotYet -> status.window.opens
                is AdhkaarWindows.Status.Closed -> status.next.opens
                else -> null
            }
            val waiting = pending?.collection == null && pending?.type == type
            return WidgetData(
                type = type,
                time = next?.let { formatTime(context, it) },
                day = next?.let { context.getString(if (it.toLocalDate() == today) R.string.widget_today else R.string.widget_tomorrow) },
                waiting = waiting,
                morningDone = state.lastCompleted(SessionType.MORNING) == today,
                eveningDone = state.lastCompleted(SessionType.EVENING) == today,
                streak = Streaks.current(state.historyFlow.value, today),
                opensAt = if (waiting) null else opens?.let { formatTime(context, it) },
                countdown = next?.takeIf { !waiting && Duration.between(now, it).toMinutes() >= 1 }
                    ?.let { context.getString(R.string.widget_countdown_in, formatCountdown(context, now, it)) },
            )
        }
    }
}

/** Today: the next session, its countdown, both sessions' status and one action. */
class AdhkaarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val look = WidgetLook.load(context, id)
        provideContent { WidgetContent(context, data, look) }
    }

    companion object {
        // Responsive picks the largest declared size that fits the cell, so keep these modest:
        // a 4×2 cell can be as short as ~100dp.
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 96.dp)

        /** Refresh every placed widget, of every kind; cheap, and safe to call from anywhere. */
        fun refresh(context: Context) {
            CoroutineScope(Dispatchers.Default).launch { Widgets.updateAll(context.applicationContext) }
        }
    }
}

class AdhkaarWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AdhkaarWidget()
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData, look: WidgetLook) {
    val wide = LocalSize.current.width >= AdhkaarWidget.WIDE.width
    val palette = look.palette
    val done = context.getString(R.string.widget_done)
    val typeLabel = context.getString(if (data.type == SessionType.MORNING) R.string.widget_morning else R.string.widget_evening)
    WidgetFrame(look, openAppAction(context)) {
        DirRow(look.dir, GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.Top) {
            // The next session: what, when, and how long until it.
            item {
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = look.dir.start) {
                    DirRow(look.dir, GlanceModifier.fillMaxWidth()) {
                        item {
                            Image(
                                ImageProvider(R.drawable.ic_notification), null,
                                modifier = GlanceModifier.size(14.dp),
                                colorFilter = ColorFilter.tint(ColorProvider(palette.gold)),
                            )
                        }
                        item { Spacer(GlanceModifier.width(6.dp)) }
                        item {
                            // Wide, the session's name takes the brand's place: a 4×2 cell can be
                            // under 100dp tall, and a line of its own cut off the countdown.
                            Text(
                                if (wide) typeLabel else context.getString(R.string.app_brand_caps),
                                style = if (wide) look.text(palette.accent(data.type), 13.sp, FontWeight.Medium) else look.text(palette.tertiary, 10.sp, FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                            )
                        }
                        if (wide && data.streak > 0) {
                            item { Image(ImageProvider(R.drawable.widget_flame), null, modifier = GlanceModifier.size(12.dp)) }
                            item { Spacer(GlanceModifier.width(4.dp)) }
                            item { Text("${data.streak}", style = look.text(palette.gold, 11.sp, FontWeight.Bold)) }
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    if (!wide) Text(typeLabel, style = look.text(palette.accent(data.type), 14.sp, FontWeight.Medium))
                    Text(
                        if (data.waiting) context.getString(R.string.widget_now) else data.time ?: "—",
                        style = look.text(palette.text, 26.sp, FontWeight.Medium),
                    )
                    Text(
                        when {
                            data.waiting -> context.getString(R.string.widget_waiting)
                            wide -> listOfNotNull(data.day, data.countdown).joinToString(" · ")
                            else -> data.countdown ?: data.day.orEmpty()
                        },
                        style = look.text(palette.secondary, 11.sp),
                        maxLines = 1,
                    )
                }
            }
            if (wide) {
                item { Spacer(GlanceModifier.width(16.dp)) }
                // Today at a glance, and one action.
                item {
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        StatusRow(look, context.getString(R.string.widget_morning), done.takeIf { data.morningDone }, palette.morning)
                        Spacer(GlanceModifier.height(6.dp))
                        StatusRow(look, context.getString(R.string.widget_evening), done.takeIf { data.eveningDone }, palette.evening)
                        Spacer(GlanceModifier.defaultWeight())
                        ActionPill(context, data, look)
                    }
                }
            }
        }
    }
}

/** Begin or Continue opens the session; before its window opens it says when, and opens the app. */
@Composable
private fun ActionPill(context: Context, data: WidgetData, look: WidgetLook) {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .height(32.dp)
            .background(ImageProvider(look.palette.pill(data.type)))
            .clickable(
                actionStartActivity(if (data.opensAt == null) Notifications.sessionIntent(context, data.type) else Intent(context, MainActivity::class.java)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                data.opensAt != null -> context.getString(R.string.widget_opens_at, data.opensAt)
                data.waiting -> context.getString(R.string.widget_continue)
                else -> context.getString(R.string.widget_begin)
            },
            style = look.text(look.palette.onPill, 13.sp, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** A session's dot, name, and [doneLabel] once it is done today. */
@Composable
private fun StatusRow(look: WidgetLook, label: String, doneLabel: String?, color: Color) {
    val palette = look.palette
    DirRow(look.dir, GlanceModifier.fillMaxWidth()) {
        // An image, not a rounded box: Glance only rounds corners on Android 12+.
        item { Image(ImageProvider(R.drawable.widget_dot), null, modifier = GlanceModifier.size(8.dp), colorFilter = ColorFilter.tint(ColorProvider(color))) }
        item { Spacer(GlanceModifier.width(8.dp)) }
        item { Text(label, style = look.text(palette.text, 13.sp), modifier = GlanceModifier.defaultWeight()) }
        item {
            Text(
                doneLabel ?: "—",
                style = look.text(if (doneLabel != null) palette.done else palette.tertiary, 12.sp, FontWeight.Medium, look.dir.textEnd),
            )
        }
    }
}
