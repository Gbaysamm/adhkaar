package org.adhkaar.app.schedule

import org.adhkaar.app.data.Prayer
import java.time.ZonedDateTime

/** When collection reminders should fire. Pure, so it can be unit tested. */
object CollectionTimes {
    private val prayers = setOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    /**
     * The next time that is [delayMinutes] after one of the five prayers (the same [prayerTimes]
     * every day), strictly after [now]. The earliest wins rather than the first listed: times the
     * user set need not be in order.
     */
    fun nextAfterSalah(now: ZonedDateTime, delayMinutes: Int, prayerTimes: List<PrayerTime>): ZonedDateTime? {
        for (dayOffset in 0L..1L) {
            val day = now.toLocalDate().plusDays(dayOffset)
            prayerTimes.filter { it.name in prayers }
                .map { ZonedDateTime.of(day, it.time, now.zone).plusMinutes(delayMinutes.toLong()) }
                .filter { it.isAfter(now) }
                .minOrNull()
                ?.let { return it }
        }
        return null
    }

    /**
     * The next salah reminder and when, strictly after [now], from the prayers whose reminder is on
     * and their minute of the day ([org.adhkaar.app.data.AppSettings.salahReminderMinutes]); null
     * when all are off.
     */
    fun nextSalahReminder(now: ZonedDateTime, reminders: Map<Prayer, Int>): Pair<Prayer, ZonedDateTime>? =
        reminders.map { (prayer, minute) -> prayer to nextDaily(now, minute) }.minByOrNull { (_, at) -> at }

    /** The next occurrence of [minuteOfDay], strictly after [now]. */
    fun nextDaily(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val today = now.withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
