package org.adhkaar.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.schedule.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/** When the widgets next need redrawing. Pure, so it can be unit tested. */
object WidgetSchedule {
    /** The countdown and the sky drift; half an hour keeps them close without costing battery. */
    val EVERY: Duration = Duration.ofMinutes(30)

    /**
     * The first of: a [moments] change of state (a window opening, a session time), midnight (the
     * date, the Hijri date and the week change), or [EVERY] from [now].
     */
    fun nextTick(now: ZonedDateTime, moments: List<ZonedDateTime>): ZonedDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return (moments + midnight + now.plus(EVERY)).filter { it.isAfter(now) }.minBy { it.toInstant() }
    }
}

/**
 * Keeps the time-based widgets current. The alarm is inexact and doesn't wake the phone: it is
 * delivered the next time the phone is awake anyway, which is exactly when a widget can be seen.
 */
object WidgetTicker {
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val tick = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!Widgets.anyPlaced(context)) {
            alarms.cancel(tick)
            return
        }
        val now = ZonedDateTime.now()
        alarms.set(AlarmManager.RTC, WidgetSchedule.nextTick(now, moments(context, now)).toInstant().toEpochMilli(), tick)
    }

    /** Today's window edges and the next session times, where the Today and Next widgets change what they say. */
    private fun moments(context: Context, now: ZonedDateTime): List<ZonedDateTime> {
        val settings = SettingsStore.get(context).current
        return SessionType.entries.flatMap { type ->
            val window = AdhkaarWindows.window(settings, type, now.toLocalDate(), now.zone)
            listOfNotNull(window.opens, window.idealEnd, window.closes, AlarmScheduler.nextTime(context, type, now))
        }
    }
}

/** The ticker's alarm, and the clock, time zone or phone language changing under the widgets. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Widgets.updateAll(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
