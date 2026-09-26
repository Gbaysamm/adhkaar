package org.adhkaar.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * A session that has started but not been completed.
 * [enforced] is true when it was started by the alarm (so strictness applies),
 * false when the user opened it themselves from the home screen.
 * [pausedUntilMillis]: the end of the break the user took, if any (0 = none); until then nothing
 * rings or blocks (see org.adhkaar.app.session.AlertPolicy.isPaused).
 */
data class PendingSession(
    val type: SessionType,
    val date: LocalDate,
    val startedAtMillis: Long,
    val enforced: Boolean,
    val pausedUntilMillis: Long = 0L,
    /** Started by Test morning/evening: never recorded, never shown as due, and can be ended. */
    val test: Boolean = false,
)

/** Completion history, stored as "2026-09-25|morning" entries. */
object Streaks {
    fun key(date: LocalDate, type: SessionType) = "$date|${type.key}"

    fun isDone(history: Set<String>, date: LocalDate, type: SessionType) = key(date, type) in history

    fun anyDone(history: Set<String>, date: LocalDate) = SessionType.entries.any { isDone(history, date, it) }

    /** Longest run of consecutive days with at least one session, looking back up to a year. */
    fun best(history: Set<String>, today: LocalDate): Int {
        var best = 0
        var run = 0
        for (i in 365L downTo 0L) {
            if (anyDone(history, today.minusDays(i))) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    /** Consecutive days with at least one session done. Today counts once done; until then the streak runs to yesterday. */
    fun current(history: Set<String>, today: LocalDate): Int {
        var day = if (anyDone(history, today)) today else today.minusDays(1)
        var count = 0
        while (anyDone(history, day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

class SessionState private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val pendingState = MutableStateFlow(readPending())
    private val completedState = MutableStateFlow(readCompleted())
    private val historyState = MutableStateFlow(prefs.getStringSet(KEY_HISTORY, emptySet())!!.toSet())
    private val favoritesState = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet())!!.toSet())

    /** Ids of favourite adhkaar. */
    val favoritesFlow: StateFlow<Set<String>> = favoritesState.asStateFlow()

    fun toggleFavorite(id: String) {
        val next = favoritesState.value.let { if (id in it) it - id else it + id }
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        favoritesState.value = next
    }

    private val collectionLogState = MutableStateFlow(prefs.getStringSet(KEY_COLLECTION_LOG, emptySet())!!.toSet())

    /** Collection completions as "2026-09-25|after_salah|<millis>" (one entry per completion). */
    val collectionLogFlow: StateFlow<Set<String>> = collectionLogState.asStateFlow()

    fun logCollection(collectionId: String, today: LocalDate, nowMillis: Long = System.currentTimeMillis()) {
        val next = (collectionLogState.value + "$today|$collectionId|$nowMillis").sorted().takeLast(3000).toSet()
        prefs.edit().putStringSet(KEY_COLLECTION_LOG, next).apply()
        collectionLogState.value = next
    }

    /** Completion times of morning/evening sessions as "date|type|minuteOfDay". */
    fun completionTimes(): Set<String> = prefs.getStringSet(KEY_TIMES, emptySet())!!.toSet()

    /**
     * The user has answered this session's alert (opened it, counted, unlocked the phone or
     * pressed Stop), so it must not ring again.
     */
    val alertAcknowledged: Boolean get() = prefs.getBoolean(KEY_ALERT_ACK, false)

    fun acknowledgeAlert() {
        prefs.edit().putBoolean(KEY_ALERT_ACK, true).commit()
    }

    /** See [Streaks]. */
    val historyFlow: StateFlow<Set<String>> = historyState.asStateFlow()

    val pendingFlow: StateFlow<PendingSession?> = pendingState.asStateFlow()

    /** Last completion date for each session type. */
    val completedFlow: StateFlow<Map<SessionType, LocalDate>> = completedState.asStateFlow()

    val pending: PendingSession? get() = pendingState.value

    fun lastCompleted(type: SessionType): LocalDate? = completedState.value[type]

    /** Starts (or keeps) the pending session. Progress is kept when it's the same session. */
    fun start(type: SessionType, today: LocalDate, nowMillis: Long, enforced: Boolean, test: Boolean = false) {
        val current = pending
        if (test) {
            // A test always starts clean and never touches the real session's progress.
            prefs.edit().remove(KEY_PROGRESS).remove(KEY_PAGE).remove(KEY_ALERT_ACK).apply()
            writePending(PendingSession(type, today, nowMillis, enforced, test = true))
            return
        }
        if (current != null && current.type == type && current.date == today && !current.test) {
            if (enforced && !current.enforced) {
                // Opened by hand earlier, and now the session time has come: it rings like any other.
                prefs.edit().remove(KEY_ALERT_ACK).apply()
                writePending(current.copy(enforced = true, startedAtMillis = nowMillis))
            }
            return
        }
        prefs.edit().remove(KEY_PROGRESS).remove(KEY_PAGE).remove(KEY_ALERT_ACK).apply()
        writePending(PendingSession(type, today, nowMillis, enforced))
    }

    fun progress(): Map<String, Int> =
        prefs.getString(KEY_PROGRESS, null)?.split(';')?.mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@mapNotNull null
            parts[1].toIntOrNull()?.let { parts[0] to it }
        }?.toMap() ?: emptyMap()

    fun saveProgress(progress: Map<String, Int>) {
        prefs.edit().putString(KEY_PROGRESS, progress.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    /**
     * The page the pending session was last on, or -1. Kept with its progress, so the session
     * reopens on it even when its screen is created afresh (from the notification or the widget).
     */
    fun page(): Int = prefs.getInt(KEY_PAGE, -1)

    fun savePage(page: Int) {
        prefs.edit().putInt(KEY_PAGE, page).apply()
    }

    fun complete(type: SessionType, today: LocalDate, minuteOfDay: Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }) {
        // Keep about a year of history; it powers the streak, the week view and Insights.
        val history = (historyState.value + Streaks.key(today, type)).sorted().takeLast(800).toSet()
        val times = (prefs.getStringSet(KEY_TIMES, emptySet())!! + "$today|${type.key}|$minuteOfDay").sorted().takeLast(800).toSet()
        prefs.edit()
            .putString("completed_${type.key}", today.toString())
            .putStringSet(KEY_HISTORY, history)
            .putStringSet(KEY_TIMES, times)
            .apply()
        historyState.value = history
        completedState.value = readCompleted()
        clearPending()
    }

    /**
     * Pauses the pending session until [untilMillis]. The alert is un-answered again, so it rings
     * when the break ends, like the second ring.
     */
    fun startBreak(untilMillis: Long) {
        val current = pending ?: return
        prefs.edit().remove(KEY_ALERT_ACK).apply()
        writePending(current.copy(pausedUntilMillis = untilMillis))
    }

    fun clearPending() {
        prefs.edit().remove(KEY_PENDING).remove(KEY_PROGRESS).remove(KEY_PAGE).remove(KEY_ALERT_ACK).commit()
        pendingState.value = null
    }

    private fun writePending(p: PendingSession) {
        // commit() rather than apply(): the process may be killed right after an alarm.
        prefs.edit()
            .putString(KEY_PENDING, "${p.type.key}|${p.date}|${p.startedAtMillis}|${p.enforced}|${p.pausedUntilMillis}|${p.test}")
            .commit()
        pendingState.value = p
    }

    private fun readPending(): PendingSession? {
        val parts = prefs.getString(KEY_PENDING, null)?.split('|') ?: return null
        // Four fields were written before breaks existed; such a session has no break.
        if (parts.size !in 4..6) return null
        return PendingSession(
            type = SessionType.fromKey(parts[0]) ?: return null,
            date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null,
            startedAtMillis = parts[2].toLongOrNull() ?: return null,
            enforced = parts[3].toBoolean(),
            pausedUntilMillis = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            test = parts.getOrNull(5).toBoolean(),
        )
    }

    private fun readCompleted(): Map<SessionType, LocalDate> = SessionType.entries.mapNotNull { type ->
        prefs.getString("completed_${type.key}", null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { type to it }
    }.toMap()

    /** The first day the app ran; nothing before it can have been missed. */
    fun firstSeen(): LocalDate {
        prefs.getString(KEY_FIRST_SEEN, null)?.let { saved -> runCatching { LocalDate.parse(saved) }.getOrNull()?.let { return it } }
        val today = LocalDate.now()
        prefs.edit().putString(KEY_FIRST_SEEN, today.toString()).apply()
        return today
    }

    /** Missed sessions ("date|type") whose card has been shown. */
    fun missedSeen(): Set<String> = prefs.getStringSet(KEY_MISSED_SEEN, emptySet())!!.toSet()

    fun markMissedSeen(key: String) {
        val next = (missedSeen() + key).sorted().takeLast(60).toSet()
        prefs.edit().putStringSet(KEY_MISSED_SEEN, next).apply()
    }

    companion object {
        private const val KEY_FIRST_SEEN = "first_seen"
        private const val KEY_MISSED_SEEN = "missed_seen"
        private const val KEY_PENDING = "pending"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_PAGE = "page"
        private const val KEY_HISTORY = "history"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_TIMES = "completion_times"
        private const val KEY_COLLECTION_LOG = "collection_log"
        private const val KEY_ALERT_ACK = "alert_acknowledged"

        @Volatile private var instance: SessionState? = null
        fun get(context: Context): SessionState = instance ?: synchronized(this) {
            instance ?: SessionState(context).also { instance = it }
        }
    }
}
