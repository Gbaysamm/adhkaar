package org.adhkaar.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.ui.MainActivity
import java.time.ZonedDateTime

object AlarmScheduler {
    private const val WATCHDOG_REQUEST = 900
    private const val TEST_REQUEST = 901
    private const val CALENDAR_REQUEST = 903
    private const val FRIDAY_REQUEST = 904
    private const val AFTER_SALAH_REQUEST = 905
    private const val BEDTIME_REQUEST = 906
    private const val RERING_REQUEST = 907
    private const val SALAH_REQUEST = 908
    /** How long after the congregation to nudge for the after-salah adhkaar (time to finish praying). */
    const val AFTER_SALAH_DELAY_MINUTES = 20
    /** Evening check for tomorrow's fasts and Eid. */
    private const val CALENDAR_CHECK_MINUTE = 20 * 60
    const val WATCHDOG_INTERVAL_MS = 5 * 60_000L

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun nextTime(context: Context, type: SessionType, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val settings = SettingsStore.get(context).current
        return SessionTimeCalculator.next(
            schedule = settings.schedule(type),
            now = now,
            lastCompleted = SessionState.get(context).lastCompleted(type),
            prayerTime = PrayerClock.provider(settings, type),
        )
    }

    fun scheduleAll(context: Context) {
        SessionType.entries.forEach { scheduleSession(context, it) }
        scheduleCalendar(context)
        scheduleCollections(context)
        scheduleSalahReminders(context)
    }

    /**
     * The next reminder to get ready for salah, at the prayer's time; the receiver schedules the
     * one after when it fires.
     * Exact when allowed: a reminder to leave for the masjid is no use ten minutes late.
     */
    fun scheduleSalahReminders(context: Context) {
        val settings = SettingsStore.get(context).current
        val next = CollectionTimes.nextSalahReminder(ZonedDateTime.now(), settings.salahReminderMinutes)
        if (next == null) {
            context.getSystemService(AlarmManager::class.java).cancel(salahIntent(context, prayerId = null))
            return
        }
        val (prayer, at) = next
        setWakeup(context, at.toInstant().toEpochMilli(), salahIntent(context, prayer.id))
    }

    /** One alarm for all five: it only ever holds the next one, with its prayer in the extras. */
    private fun salahIntent(context: Context, prayerId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, SALAH_REQUEST,
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_SALAH)
                .apply { if (prayerId != null) putExtra(SessionAlarmReceiver.EXTRA_PRAYER, prayerId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * After-salah and bedtime. Exact: in Full screen or Lockdown they open the adhkaar, and an
     * inexact alarm can land 15 minutes late on HyperOS, long after the salah is over.
     */
    fun scheduleCollections(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = SettingsStore.get(context).current
        val now = ZonedDateTime.now()
        val afterSalah = collectionIntent(context, AFTER_SALAH_REQUEST, "after_salah")
        val next = if (settings.afterSalahReminder) {
            // From when the masjid prays, not the reminder to get ready (see Congregation).
            CollectionTimes.nextAfterSalah(now, AFTER_SALAH_DELAY_MINUTES, Congregation.day(settings))
        } else null
        if (next != null) {
            setWakeup(context, next.toInstant().toEpochMilli(), afterSalah)
        } else {
            am.cancel(afterSalah)
        }
        val bedtime = collectionIntent(context, BEDTIME_REQUEST, "before_sleep")
        if (settings.bedtimeMinute >= 0) {
            setWakeup(context, CollectionTimes.nextDaily(now, settings.bedtimeMinute).toInstant().toEpochMilli(), bedtime)
        } else {
            am.cancel(bedtime)
        }
    }

    private fun collectionIntent(context: Context, requestCode: Int, collectionId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_COLLECTION)
                .putExtra(SessionAlarmReceiver.EXTRA_COLLECTION, collectionId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun scheduleSession(context: Context, type: SessionType) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = sessionIntent(context, type, requestCode = 100 + type.ordinal)
        val at = nextTime(context, type)
        val pre = preIntent(context, type)
        if (at == null) {
            am.cancel(operation)
            am.cancel(pre)
            return
        }
        setAlarm(context, at.toInstant().toEpochMilli(), operation)
        // Gentle heads-up a few minutes before; inexact is fine for this.
        val minutes = SettingsStore.get(context).current.preReminderMinutes
        val preAt = at.minusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        if (minutes > 0 && preAt > System.currentTimeMillis()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAt, pre)
        } else {
            am.cancel(pre)
        }
    }

    /** The second ring of a session nobody has answered yet (see AlertPolicy). */
    fun scheduleReRing(context: Context, atMillis: Long) {
        setWakeup(context, atMillis, broadcast(context, RERING_REQUEST, SessionAlarmReceiver.ACTION_RERING))
    }

    fun cancelReRing(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(broadcast(context, RERING_REQUEST, SessionAlarmReceiver.ACTION_RERING))
    }

    /** Daily 20:00 check for tomorrow, and the Friday reminder. Both inexact: a few minutes late is fine. */
    fun scheduleCalendar(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = SettingsStore.get(context).current
        val check = broadcast(context, CALENDAR_REQUEST, SessionAlarmReceiver.ACTION_CALENDAR)
        val friday = broadcast(context, FRIDAY_REQUEST, SessionAlarmReceiver.ACTION_FRIDAY)
        if (!settings.calendarReminders) {
            am.cancel(check)
            am.cancel(friday)
            return
        }
        val now = ZonedDateTime.now()
        fun nextAt(minuteOfDay: Int, matches: (ZonedDateTime) -> Boolean): Long {
            var t = now.withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60).withSecond(0).withNano(0)
            while (!t.isAfter(now) || !matches(t)) t = t.plusDays(1)
            return t.toInstant().toEpochMilli()
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(CALENDAR_CHECK_MINUTE) { true }, check)
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAt(settings.fridayReminderMinute) { it.dayOfWeek == java.time.DayOfWeek.FRIDAY },
            friday,
        )
    }

    private fun broadcast(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SessionAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun preIntent(context: Context, type: SessionType): PendingIntent =
        PendingIntent.getBroadcast(
            context, 110 + type.ordinal,
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_PRE)
                .putExtra(SessionAlarmReceiver.EXTRA_TYPE, type.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** When [type]'s adhkaar time ends today: checks whether they were read (MissedAdhkaar). */
    fun scheduleWindowEnd(context: Context, type: SessionType, atMillis: Long, attempt: Int = 0) {
        val intent = PendingIntent.getBroadcast(
            context, 130 + type.ordinal,
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_WINDOW_END)
                .putExtra(SessionAlarmReceiver.EXTRA_TYPE, type.key)
                .putExtra(SessionAlarmReceiver.EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setWakeup(context, atMillis, intent)
    }

    /** When a collection held by its reminder lets go (see SessionLauncher.onCollectionTime). */
    fun scheduleCollectionEnd(context: Context, id: String, atMillis: Long) {
        val intent = PendingIntent.getBroadcast(
            context, 140 + org.adhkaar.app.data.SettingsStore.ENFORCEABLE.indexOf(id).coerceAtLeast(0),
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_COLLECTION_END)
                .putExtra(SessionAlarmReceiver.EXTRA_COLLECTION, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setWakeup(context, atMillis, intent)
    }

    /** Fires a session shortly from now, so users (and testers) can check it works on their phone. */
    fun scheduleTest(context: Context, type: SessionType, delayMs: Long) {
        setAlarm(context, System.currentTimeMillis() + delayMs, sessionIntent(context, type, TEST_REQUEST, test = true))
    }

    fun scheduleWatchdog(context: Context) {
        setWakeup(context, System.currentTimeMillis() + WATCHDOG_INTERVAL_MS, watchdogIntent(context))
    }

    fun cancelWatchdog(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(watchdogIntent(context))
    }

    /**
     * Exact when allowed, without the status-bar alarm icon of [setAlarm]. An exact alarm also
     * lets the receiver start the foreground service from the background.
     */
    private fun setWakeup(context: Context, atMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun setAlarm(context: Context, atMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            // setAlarmClock is the most reliable exact alarm: Doze and most OEM battery savers respect it.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun sessionIntent(context: Context, type: SessionType, requestCode: Int, test: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SessionAlarmReceiver::class.java)
                .setAction(SessionAlarmReceiver.ACTION_SESSION)
                .putExtra(SessionAlarmReceiver.EXTRA_TYPE, type.key)
                .putExtra(SessionAlarmReceiver.EXTRA_TEST, test),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun watchdogIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, WATCHDOG_REQUEST,
            Intent(context, SessionAlarmReceiver::class.java).setAction(SessionAlarmReceiver.ACTION_WATCHDOG),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
