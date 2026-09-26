package org.adhkaar.app.schedule

import org.adhkaar.app.data.SessionSchedule
import org.adhkaar.app.data.TimeMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Pure scheduling logic, kept free of Android types so it can be unit tested. */
object SessionTimeCalculator {

    /** The session time on [day]: prayer time + offset, falling back to the fixed time. */
    fun sessionTime(schedule: SessionSchedule, day: LocalDate, prayerTime: (LocalDate) -> LocalTime?): LocalTime {
        val fixed = LocalTime.of(schedule.fixedMinuteOfDay / 60, schedule.fixedMinuteOfDay % 60)
        if (schedule.mode != TimeMode.PRAYER) return fixed
        return prayerTime(day)?.plusMinutes(schedule.offsetMinutes.toLong()) ?: fixed
    }

    /**
     * The next time the session should start, strictly after [now].
     * Days on which the session was already completed are skipped.
     */
    fun next(
        schedule: SessionSchedule,
        now: ZonedDateTime,
        lastCompleted: LocalDate?,
        prayerTime: (LocalDate) -> LocalTime?,
    ): ZonedDateTime? {
        if (!schedule.enabled) return null
        for (i in 0L..2L) {
            val day = now.toLocalDate().plusDays(i)
            if (day == lastCompleted) continue
            val at = ZonedDateTime.of(day, sessionTime(schedule, day, prayerTime), now.zone)
            if (at.isAfter(now)) return at
        }
        return null
    }

    /**
     * True when today's session time has passed within the last [graceMinutes] and it wasn't
     * completed, e.g. the phone was off at the time. Used to catch up after a reboot.
     */
    fun missedToday(
        schedule: SessionSchedule,
        now: ZonedDateTime,
        lastCompleted: LocalDate?,
        graceMinutes: Int,
        prayerTime: (LocalDate) -> LocalTime?,
    ): Boolean {
        val today = now.toLocalDate()
        if (!schedule.enabled || lastCompleted == today) return false
        val at = ZonedDateTime.of(today, sessionTime(schedule, today, prayerTime), now.zone)
        return !at.isAfter(now) && at.plusMinutes(graceMinutes.toLong()).isAfter(now)
    }
}
