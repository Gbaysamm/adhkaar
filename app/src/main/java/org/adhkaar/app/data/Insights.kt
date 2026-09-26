package org.adhkaar.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayRecord(val date: LocalDate, val morning: Boolean, val evening: Boolean) {
    val any get() = morning || evening
    val both get() = morning && evening
}

/** Share of a weekday's dates (in the window) with at least one session. */
data class WeekdayRate(val day: DayOfWeek, val done: Int, val total: Int) {
    val rate get() = if (total == 0) 0f else done.toFloat() / total
}

/** Sessions finished in their best time, out of all sessions with a recorded time. */
data class Punctuality(val onTime: Int, val total: Int)

/** Everything the Insights screen shows, computed from stored history. Pure, so it can be tested. */
object Insights {
    fun day(history: Set<String>, date: LocalDate) = DayRecord(
        date,
        Streaks.isDone(history, date, SessionType.MORNING),
        Streaks.isDone(history, date, SessionType.EVENING),
    )

    fun month(history: Set<String>, month: YearMonth): List<DayRecord> =
        (1..month.lengthOfMonth()).map { day(history, month.atDay(it)) }

    /** Sessions completed in [month] (morning and evening count separately). */
    fun sessions(history: Set<String>, month: YearMonth) =
        month(history, month).sumOf { (if (it.morning) 1 else 0) + (if (it.evening) 1 else 0) }

    fun fullDays(history: Set<String>, month: YearMonth) = month(history, month).count { it.both }

    /**
     * For each weekday (Sunday first, like the week view), how often at least one session was done
     * over the last [weeks] weeks up to [today]. Future dates don't count against you.
     */
    fun weekdayRates(history: Set<String>, today: LocalDate, weeks: Int = 8): List<WeekdayRate> {
        val dates = (0 until weeks * 7).map { today.minusDays(it.toLong()) }
        val order = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
        return order.map { dow ->
            val ofDay = dates.filter { it.dayOfWeek == dow }
            WeekdayRate(dow, ofDay.count { day(history, it).any }, ofDay.size)
        }
    }

    /** Median minute of day the session is completed, from "date|type|minute" records; null if none. */
    fun usualMinute(times: Set<String>, type: SessionType): Int? {
        val minutes = times.mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size == 3 && parts[1] == type.key) parts[2].toIntOrNull() else null
        }.sorted()
        if (minutes.isEmpty()) return null
        return minutes[minutes.size / 2]
    }

    /**
     * The first completion minute of each session per day in [month], from "date|type|minute"
     * records. A session said again later that day doesn't change whether it was on time.
     */
    fun completionMinutes(times: Set<String>, month: YearMonth): Map<Pair<LocalDate, SessionType>, Int> =
        times.mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != 3) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val type = SessionType.fromKey(parts[1]) ?: return@mapNotNull null
            val minute = parts[2].toIntOrNull() ?: return@mapNotNull null
            if (YearMonth.from(date) == month) (date to type) to minute else null
        }.groupBy({ it.first }, { it.second }).mapValues { (_, minutes) -> minutes.min() }

    /**
     * How many of [month]'s timed sessions were finished in their best time, out of all of them.
     * [window] gives the day's window for a session (see [AdhkaarWindows]).
     */
    fun punctuality(times: Set<String>, month: YearMonth, window: (LocalDate, SessionType) -> AdhkaarWindows.Window): Punctuality {
        val done = completionMinutes(times, month)
        return Punctuality(
            onTime = done.count { (key, minute) -> AdhkaarWindows.onTime(window(key.first, key.second), minute) },
            total = done.size,
        )
    }

    /** For each day in [month] with a session finished after its best time, which sessions were late. */
    fun lateDays(times: Set<String>, month: YearMonth, window: (LocalDate, SessionType) -> AdhkaarWindows.Window): Map<LocalDate, Set<SessionType>> =
        completionMinutes(times, month)
            .filter { (key, minute) -> AdhkaarWindows.late(window(key.first, key.second), minute) }
            .keys.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    /** How many times each collection was completed in [month], from "date|id|millis" records. */
    fun collectionCounts(log: Set<String>, month: YearMonth): Map<String, Int> =
        log.mapNotNull { entry ->
            val parts = entry.split('|')
            val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (YearMonth.from(date) == month) parts.getOrNull(1) else null
        }.groupingBy { it }.eachCount()
}
