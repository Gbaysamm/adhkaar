package org.adhkaar.app.session

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.enforce.EnforcementService
import org.adhkaar.app.schedule.AlarmScheduler
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * A morning or evening whose adhkaar time ended without them being read. The app says so once,
 * gently: a notification when the time ends, and a card the next time the app is opened.
 */
object MissedAdhkaar {
    data class Missed(val type: SessionType, val date: LocalDate) {
        val key get() = "$date|${type.key}"
    }

    /**
     * The most recent missed session, if it hasn't been shown yet. Only the latest counts: one
     * gentle word, never a list of every day gone by. Days before the app was first opened, and
     * sessions switched off, are never missed.
     */
    fun latest(context: Context, now: ZonedDateTime = ZonedDateTime.now()): Missed? {
        val settings = SettingsStore.get(context).current
        val state = SessionState.get(context)
        val waiting = state.pending?.takeIf { it.collection == null && !it.test }?.let { Missed(it.type, it.date) }
        return latest(settings, state.historyFlow.value, state.missedSeen(), state.firstSeen(), now).takeIf { it != waiting }
    }

    /** Pure, for tests: [history] as "date|type" keys, [seen] the missed keys already shown. */
    fun latest(settings: AppSettings, history: Set<String>, seen: Set<String>, since: LocalDate, now: ZonedDateTime): Missed? {
        val today = now.toLocalDate()
        val candidates = listOf(
            Missed(SessionType.EVENING, today), Missed(SessionType.MORNING, today), Missed(SessionType.EVENING, today.minusDays(1)),
        )
        for (m in candidates) {
            if (m.date.isBefore(since) || !settings.schedule(m.type).enabled) continue
            val closes = AdhkaarWindows.window(settings, m.type, m.date, now.zone).closes
            if (now.isBefore(closes)) continue
            if (Streaks.isDone(history, m.date, m.type)) return null
            return if (m.key in seen) null else m
        }
        return null
    }

    /** While someone is still reading when the time ends, how often and how long to wait before calling it missed. */
    private const val GRACE_MINUTES = 15L
    private const val GRACE_CHECKS = 4

    fun markSeen(context: Context, missed: Missed) = SessionState.get(context).markMissedSeen(missed.key)

    /**
     * When a session's adhkaar time ends (an alarm set when it rang): if they weren't read, let
     * the waiting session go and say so, gently.
     */
    fun onWindowEnd(context: Context, type: SessionType, attempt: Int = 0) {
        val state = SessionState.get(context)
        val today = LocalDate.now()
        if (Streaks.isDone(state.historyFlow.value, today, type)) return
        val pending = state.pending
        // Still reading when the time ends: that isn't missing them. Let go of the ringing and the
        // lock, leave them open to finish, and look again in a while (up to an hour).
        val mine = pending != null && pending.type == type && pending.collection == null && !pending.test
        val reading = mine && (org.adhkaar.app.ui.SessionActivity.isVisible || state.progress().values.any { it > 0 })
        if (reading && attempt < GRACE_CHECKS) {
            AlertPlayer.stop()
            AlarmScheduler.cancelReRing(context)
            AlarmScheduler.cancelWatchdog(context)
            state.relax()
            EnforcementService.stop(context)
            Notifications.refresh(context)
            AlarmScheduler.scheduleWindowEnd(context, type, System.currentTimeMillis() + GRACE_MINUTES * 60_000L, attempt + 1)
            return
        }
        if (pending != null && pending.type == type && !pending.test) {
            state.clearPending()
            NotificationManagerCompat.from(context).cancel(Notifications.SESSION_ID)
            EnforcementService.stop(context)
            AlertPlayer.stop()
        }
        Notifications.showMissed(context, type)
    }
}
