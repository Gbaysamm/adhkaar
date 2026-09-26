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
        return latest(settings, state.historyFlow.value, state.missedSeen(), state.firstSeen(), now)
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

    fun markSeen(context: Context, missed: Missed) = SessionState.get(context).markMissedSeen(missed.key)

    /**
     * When a session's adhkaar time ends (an alarm set when it rang): if they weren't read, let
     * the waiting session go and say so, gently.
     */
    fun onWindowEnd(context: Context, type: SessionType) {
        val state = SessionState.get(context)
        val today = LocalDate.now()
        if (Streaks.isDone(state.historyFlow.value, today, type)) return
        val pending = state.pending
        if (pending != null && pending.type == type && !pending.test) {
            state.clearPending()
            NotificationManagerCompat.from(context).cancel(Notifications.SESSION_ID)
            EnforcementService.stop(context)
            AlertPlayer.stop()
        }
        Notifications.showMissed(context, type)
    }
}
