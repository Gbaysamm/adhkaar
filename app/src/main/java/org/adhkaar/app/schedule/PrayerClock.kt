package org.adhkaar.app.schedule

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/** The best time for a session's adhkaar: Fajr → sunrise, or Asr → Maghrib. */
data class AdhkaarWindow(val start: LocalTime, val end: LocalTime)

data class PrayerTime(val name: String, val time: LocalTime)

/**
 * The day's prayer times: one time per prayer, the user's own or the default
 * ([org.adhkaar.app.data.Prayer.defaultMinute]). They are times to get ready for salah, the same
 * every day and known without a location. Everything reads them through here, so one time reaches
 * sessions, adhkaar windows, reminders and the Today card alike.
 */
object PrayerClock {
    fun time(settings: AppSettings, prayer: Prayer): LocalTime = timeOf(settings.prayerMinute(prayer))

    fun times(settings: AppSettings): Map<Prayer, LocalTime> = Prayer.entries.associateWith { time(settings, it) }

    /** All six times (with sunrise), in the order of the day. */
    fun day(settings: AppSettings): List<PrayerTime> = Prayer.entries.map { PrayerTime(it.id, time(settings, it)) }

    fun window(settings: AppSettings, type: SessionType): AdhkaarWindow =
        if (type == SessionType.MORNING) AdhkaarWindow(time(settings, Prayer.FAJR), time(settings, Prayer.SUNRISE))
        else AdhkaarWindow(time(settings, Prayer.ASR), time(settings, Prayer.MAGHRIB))

    /**
     * What a session's "minutes after Fajr / Asr" count from: the prayer as it is actually prayed
     * (its congregation time, see [Congregation]), not the reminder to get ready for it. Counting
     * from the reminder rang the adhkaar before people had even prayed.
     */
    fun provider(settings: AppSettings, type: SessionType): (LocalDate) -> LocalTime {
        val prayer = if (type == SessionType.MORNING) Prayer.FAJR else Prayer.ASR
        val congregation = Congregation.time(settings, prayer)
        return { congregation }
    }

    /**
     * When each prayer begins by calculation for the user's location, shown only for comparison
     * next to their times; nothing is scheduled from it. Null without a location.
     */
    fun calculated(settings: AppSettings, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<Prayer, LocalTime>? {
        val lat = settings.latitude ?: return null
        val lng = settings.longitude ?: return null
        return runCatching {
            val params = CalculationMethod.valueOf(settings.calcMethod.name).parameters.apply {
                madhab = if (settings.hanafiAsr) Madhab.HANAFI else Madhab.SHAFI
            }
            val t = PrayerTimes(Coordinates(lat, lng), DateComponents(day.year, day.monthValue, day.dayOfMonth), params)
            // adhan rounds to the minute but keeps the milliseconds of "now"; drop everything below the minute.
            fun local(d: Date): LocalTime = d.toInstant().atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
            mapOf(
                Prayer.FAJR to local(t.fajr),
                Prayer.SUNRISE to local(t.sunrise),
                Prayer.DHUHR to local(t.dhuhr),
                Prayer.ASR to local(t.asr),
                Prayer.MAGHRIB to local(t.maghrib),
                Prayer.ISHA to local(t.isha),
            )
        }.getOrNull()
    }

    private fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}
