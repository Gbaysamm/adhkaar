package org.adhkaar.app.schedule

import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.Prayer
import java.time.LocalTime

/**
 * When each of the five prayers is actually prayed in congregation, as near as the app can tell.
 * A prayer's time in the app is a reminder to get ready (see [PrayerClock]), so the congregation is
 * taken to be a set time after it. After-salah suggestions and the sessions' "minutes after Fajr /
 * Asr" count from here, so they don't come before people have prayed.
 */
object Congregation {
    /** From the reminder to the congregation: getting ready, the walk to the masjid, adhan and iqamah. */
    const val AFTER_REMINDER_MINUTES = 20L

    fun time(settings: AppSettings, prayer: Prayer): LocalTime =
        PrayerClock.time(settings, prayer).plusMinutes(AFTER_REMINDER_MINUTES)

    /** The five congregation times, in the order of the day, named as in [PrayerTime.name]. */
    fun day(settings: AppSettings): List<PrayerTime> = Prayer.five.map { PrayerTime(it.id, time(settings, it)) }
}
