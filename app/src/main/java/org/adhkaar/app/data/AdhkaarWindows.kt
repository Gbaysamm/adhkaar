package org.adhkaar.app.data

import org.adhkaar.app.schedule.PrayerClock
import org.adhkaar.app.schedule.SessionTimeCalculator
import org.adhkaar.app.schedule.PrayerTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the morning and evening adhkaar may be started by hand. Pure, so it can be unit tested.
 *
 * Morning: from Fajr until Dhuhr, best before sunrise. Evening: from Asr until Isha, best before
 * Maghrib. Outside that span a manual start is refused, so the evening adhkaar can't be "done"
 * at noon; between the best time and the close it is allowed but counts as late.
 */
object AdhkaarWindows {

    /** One day's span for a session. [byPrayer] is false when the default times were used instead of the user's. */
    data class Window(
        val opens: ZonedDateTime,
        val idealEnd: ZonedDateTime,
        val closes: ZonedDateTime,
        val byPrayer: Boolean,
    )

    sealed class Status(val canStart: Boolean) {
        /** Before today's window; it opens at [window].opens. */
        data class NotYet(val window: Window) : Status(false)
        data class Ideal(val window: Window) : Status(true)
        /** Past the best time, still open until [window].closes. */
        data class Late(val window: Window) : Status(true)
        /** Today's window has closed; [next] is tomorrow's. */
        data class Closed(val next: Window) : Status(false)
    }

    /**
     * The session's window for [date]. It opens at the session's own time, after the prayer has
     * been prayed (e.g. Asr prayed around 4:15, evening adhkaar from 4:45), not when the prayer's
     * time enters; it still closes with the prayer times.
     */
    fun window(settings: AppSettings, type: SessionType, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Window {
        val base = window(PrayerClock.day(settings), type, date, zone)
        val session = SessionTimeCalculator.sessionTime(settings.schedule(type), date, PrayerClock.provider(settings, type))
        val opens = ZonedDateTime.of(date, session, zone)
        val opened = if (opens > base.opens && opens < base.closes) base.copy(opens = opens) else base
        if (type != SessionType.MORNING) return opened
        // The morning adhkaar are done by 7:30: an hour and a half after they open, and the time
        // the lock lifts. Kept later than sunrise, which often comes before people finish.
        val ends = ZonedDateTime.of(date, MORNING_ENDS, zone)
        return if (ends > opened.opens) opened.copy(idealEnd = ends, closes = ends) else opened
    }

    private val MORNING_ENDS: LocalTime = LocalTime.of(7, 30)

    /** The window from a day's prayer times (as [PrayerClock.day] gives them). */
    fun window(prayers: List<PrayerTime>, type: SessionType, date: LocalDate, zone: ZoneId): Window {
        val span = if (type == SessionType.MORNING) listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR) else listOf(Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        val given = span.mapNotNull { prayer -> prayers.firstOrNull { it.name == prayer.id }?.time }
        // Times the user set can be out of order (Asr after Maghrib); the defaults never are.
        val inOrder = given.size == span.size && given.zipWithNext().all { (a, b) -> a < b }
        val (open, ideal, close) = if (inOrder) given else span.map { LocalTime.of(it.defaultMinute / 60, it.defaultMinute % 60) }
        return Window(
            ZonedDateTime.of(date, open, zone),
            ZonedDateTime.of(date, ideal, zone),
            ZonedDateTime.of(date, close, zone),
            byPrayer = inOrder,
        )
    }

    fun status(settings: AppSettings, type: SessionType, now: ZonedDateTime = ZonedDateTime.now()): Status =
        status(now, type) { window(settings, type, it, now.zone) }

    fun status(now: ZonedDateTime, type: SessionType, windowOn: (LocalDate) -> Window): Status {
        val today = windowOn(now.toLocalDate())
        return when {
            now < today.opens -> Status.NotYet(today)
            now < today.idealEnd -> Status.Ideal(today)
            now < today.closes -> Status.Late(today)
            else -> Status.Closed(windowOn(now.toLocalDate().plusDays(1)))
        }
    }

    /** Whether the session may be started by hand right now. */
    fun canStart(settings: AppSettings, type: SessionType, now: ZonedDateTime = ZonedDateTime.now()): Boolean =
        status(settings, type, now).canStart

    /** A completion at [minuteOfDay] was inside the best time, the same span [status] calls Ideal. */
    fun onTime(window: Window, minuteOfDay: Int): Boolean =
        minuteOfDay >= window.opens.minuteOfDay() && !late(window, minuteOfDay)

    /** A completion at [minuteOfDay] came after the best time had passed. */
    fun late(window: Window, minuteOfDay: Int): Boolean = minuteOfDay >= window.idealEnd.minuteOfDay()

    private fun ZonedDateTime.minuteOfDay() = hour * 60 + minute
}

/** Which collection fits the moment on the Today screen. Pure, so it can be unit tested. */
object CollectionMoments {
    const val AFTER_SALAH = "after_salah"
    const val BEFORE_SLEEP = "before_sleep"
    const val WAKING = "waking"

    /** [afterPrayer] names the prayer (as in [PrayerTime.name]) when it is the after-salah adhkaar. */
    data class Moment(val collectionId: String, val afterPrayer: String? = null)

    /**
     * Minutes after a prayer's congregation that the after-salah adhkaar is suggested. The
     * congregation time is only an estimate, and some finish late, so this runs past 30.
     */
    const val AFTER_SALAH_MINUTES = 15

    private val fivePrayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    private const val DAY = 24 * 60

    /**
     * After salah right after each of the five [congregation]s (as
     * [org.adhkaar.app.schedule.Congregation] gives them); on waking in the early morning (an hour
     * either side of Fajr to sunrise, from the [prayers] times); before sleep from half an hour
     * before the user's bedtime, or after Isha's after-salah span when no bedtime is set, until
     * the waking span. Null when nothing fits.
     */
    fun at(now: LocalTime, prayers: List<PrayerTime>, congregation: List<PrayerTime>, bedtimeMinute: Int): Moment? {
        val m = now.hour * 60 + now.minute

        fivePrayers.lastOrNull { name ->
            val p = minuteOf(congregation, name)
            within(m, p, p + AFTER_SALAH_MINUTES)
        }?.let { return Moment(AFTER_SALAH, it) }

        val wakeStart = minuteOf(prayers, "Fajr") - 60
        if (within(m, wakeStart, minuteOf(prayers, "Sunrise") + 60)) return Moment(WAKING)

        val sleepStart = if (bedtimeMinute >= 0) bedtimeMinute - 30 else minuteOf(congregation, "Isha") + AFTER_SALAH_MINUTES
        if (within(m, sleepStart, wakeStart)) return Moment(BEFORE_SLEEP)
        return null
    }

    /**
     * The next collection moment after [now] and when it starts: the next of the five
     * [congregation]s (after salah) or bedtime (before sleep), whichever comes first.
     */
    fun next(now: LocalTime, congregation: List<PrayerTime>, bedtimeMinute: Int): Pair<Moment, LocalTime> {
        val m = now.hour * 60 + now.minute
        val candidates = fivePrayers.map { name ->
            Moment(AFTER_SALAH, name) to congregation.first { it.name == name }.time
        } + listOfNotNull(
            if (bedtimeMinute >= 0) Moment(BEFORE_SLEEP) to LocalTime.of((bedtimeMinute - 30).mod(DAY) / 60, (bedtimeMinute - 30).mod(60)) else null,
        )
        return candidates.minBy { (_, t) -> Math.floorMod(t.hour * 60 + t.minute - m - 1, DAY) }
    }

    private fun minuteOf(times: List<PrayerTime>, name: String) = times.first { it.name == name }.time.let { it.hour * 60 + it.minute }

    /** [m] in [start, end) on a 24-hour clock, where the span may cross midnight. */
    private fun within(m: Int, start: Int, end: Int): Boolean {
        val s = Math.floorMod(start, DAY)
        val e = Math.floorMod(end, DAY)
        return if (s <= e) m in s until e else m >= s || m < e
    }
}
