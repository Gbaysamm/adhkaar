package org.adhkaar.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.session.CalendarReminders
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.session.SessionLauncher

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SESSION -> SessionType.fromKey(intent.getStringExtra(EXTRA_TYPE))
                ?.let { SessionLauncher.onSessionTime(context, it, test = intent.getBooleanExtra(EXTRA_TEST, false)) }
            ACTION_WATCHDOG -> SessionLauncher.onWatchdog(context)
            ACTION_PRE -> SessionType.fromKey(intent.getStringExtra(EXTRA_TYPE))
                ?.let { SessionLauncher.onPreReminder(context, it) }
            ACTION_RERING -> SessionLauncher.onReRing(context)
            ACTION_COLLECTION_END -> SessionLauncher.endCollection(context, intent.getStringExtra(EXTRA_COLLECTION))
            ACTION_WINDOW_END -> SessionType.fromKey(intent.getStringExtra(EXTRA_TYPE))
                ?.let { org.adhkaar.app.session.MissedAdhkaar.onWindowEnd(context, it) }
            ACTION_SILENCE -> SessionLauncher.silence(context)
            ACTION_CALENDAR -> CalendarReminders.onEveningCheck(context)
            ACTION_FRIDAY -> CalendarReminders.onFriday(context)
            ACTION_COLLECTION -> {
                AlarmScheduler.scheduleCollections(context)
                intent.getStringExtra(EXTRA_COLLECTION)?.let { SessionLauncher.onCollectionTime(context, it) }
            }
            ACTION_SALAH -> {
                AlarmScheduler.scheduleSalahReminders(context)
                Prayer.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_PRAYER) }
                    ?.let { Notifications.showSalahReminder(context, it) }
            }
        }
    }

    companion object {
        const val ACTION_SESSION = "org.adhkaar.app.action.SESSION"
        /** A test from Settings: runs even if that session was already done today. */
        const val EXTRA_TEST = "test"
        const val ACTION_WATCHDOG = "org.adhkaar.app.action.WATCHDOG"
        const val ACTION_PRE = "org.adhkaar.app.action.PRE"
        const val ACTION_RERING = "org.adhkaar.app.action.RERING"
        const val ACTION_WINDOW_END = "org.adhkaar.app.action.WINDOW_END"
        const val ACTION_COLLECTION_END = "org.adhkaar.app.action.COLLECTION_END"
        const val ACTION_SILENCE = "org.adhkaar.app.action.SILENCE"
        const val ACTION_CALENDAR = "org.adhkaar.app.action.CALENDAR"
        const val ACTION_FRIDAY = "org.adhkaar.app.action.FRIDAY"
        const val ACTION_COLLECTION = "org.adhkaar.app.action.COLLECTION"
        const val ACTION_SALAH = "org.adhkaar.app.action.SALAH"
        const val EXTRA_TYPE = "type"
        const val EXTRA_COLLECTION = "collection"
        /** A [Prayer.id]. */
        const val EXTRA_PRAYER = "prayer"
    }
}

/** Boot, app update, clock/timezone changes and exact-alarm grants all invalidate scheduled alarms. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        AlarmScheduler.scheduleAll(context)
        SessionLauncher.catchUpOrResume(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
