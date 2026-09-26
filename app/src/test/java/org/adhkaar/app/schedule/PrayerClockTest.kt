package org.adhkaar.app.schedule

import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.CalcMethod
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionSchedule
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.data.TimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Builds settings for the schedule tests; no location unless given. */
internal fun testSettings(
    lat: Double? = null,
    lng: Double? = null,
    hanafi: Boolean = false,
    prayerMinutes: Map<Prayer, Int> = emptyMap(),
    remindersOff: Set<Prayer> = emptySet(),
): AppSettings {
    val schedule = SessionSchedule(true, TimeMode.PRAYER, 360, 15)
    return AppSettings(
        morning = schedule, evening = schedule, strictness = Strictness.FULL_SCREEN,
        latitude = lat, longitude = lng, calcMethod = CalcMethod.MUSLIM_WORLD_LEAGUE, hanafiAsr = hanafi,
        lockdownMaxMinutes = 180, onboarded = true,
        showTransliteration = true, showTranslation = true, oemAutostartDone = false,
        prayerMinutes = prayerMinutes, salahRemindersOff = remindersOff,
    )
}

class PrayerClockTest {
    private val lagos = ZoneId.of("Africa/Lagos")
    private val day = LocalDate.of(2026, 9, 25)

    @Test
    fun `the defaults are the local times to get ready for salah`() {
        assertEquals(
            listOf(
                PrayerTime("Fajr", LocalTime.of(5, 30)), PrayerTime("Sunrise", LocalTime.of(6, 30)),
                PrayerTime("Dhuhr", LocalTime.of(13, 0)), PrayerTime("Asr", LocalTime.of(15, 55)),
                PrayerTime("Maghrib", LocalTime.of(18, 35)), PrayerTime("Isha", LocalTime.of(19, 50)),
            ),
            PrayerClock.day(testSettings()),
        )
    }

    @Test
    fun `a location doesn't change the times`() {
        assertEquals(PrayerClock.day(testSettings()), PrayerClock.day(testSettings(7.4824, 4.5603)))
    }

    @Test
    fun `the user's time replaces the default everywhere`() {
        val s = testSettings(prayerMinutes = mapOf(Prayer.ASR to 16 * 60 + 15))
        assertEquals(LocalTime.of(16, 15), PrayerClock.time(s, Prayer.ASR))
        assertEquals(LocalTime.of(16, 15), PrayerClock.day(s).first { it.name == "Asr" }.time)
        assertEquals(LocalTime.of(16, 15), PrayerClock.window(s, SessionType.EVENING).start)
        // The others keep their defaults.
        assertEquals(LocalTime.of(13, 0), PrayerClock.time(s, Prayer.DHUHR))
    }

    @Test
    fun `the windows come from the times`() {
        assertEquals(AdhkaarWindow(LocalTime.of(5, 30), LocalTime.of(6, 30)), PrayerClock.window(testSettings(), SessionType.MORNING))
        assertEquals(AdhkaarWindow(LocalTime.of(15, 55), LocalTime.of(18, 35)), PrayerClock.window(testSettings(), SessionType.EVENING))

        // The adhkaar open once the prayer has been prayed (+20) and the session's minutes have passed.
        val settings = testSettings()
        val morning = AdhkaarWindows.window(settings, SessionType.MORNING, day, lagos)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(5, 50).plusMinutes(settings.morning.offsetMinutes.toLong()), lagos), morning.opens)
        // The morning adhkaar are done by 7:30: that's both their best time and when the lock lifts.
        assertEquals(ZonedDateTime.of(day, LocalTime.of(7, 30), lagos), morning.idealEnd)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(7, 30), lagos), morning.closes)
        val evening = AdhkaarWindows.window(settings, SessionType.EVENING, day, lagos)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(16, 15).plusMinutes(settings.evening.offsetMinutes.toLong()), lagos), evening.opens)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(18, 35), lagos), evening.idealEnd)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(19, 50), lagos), evening.closes)
    }

    @Test
    fun `sessions count from the congregation`() {
        val s = testSettings()
        assertEquals(Congregation.time(s, Prayer.FAJR), PrayerClock.provider(s, SessionType.MORNING)(day))
        assertEquals(Congregation.time(s, Prayer.ASR), PrayerClock.provider(s, SessionType.EVENING)(day))
        // With the defaults and 15 minutes after the prayer: 5:30 + 20 + 15 and 15:55 + 20 + 15.
        assertEquals(LocalTime.of(6, 5), SessionTimeCalculator.sessionTime(s.morning, day, PrayerClock.provider(s, SessionType.MORNING)))
        assertEquals(LocalTime.of(16, 30), SessionTimeCalculator.sessionTime(s.evening, day, PrayerClock.provider(s, SessionType.EVENING)))
    }

    @Test
    fun `without a location everything still has a time`() {
        val s = testSettings()
        assertNull(PrayerClock.calculated(s, day, lagos))
        assertEquals(6, PrayerClock.day(s).size)
        assertEquals(LocalTime.of(6, 5), SessionTimeCalculator.sessionTime(s.morning, day, PrayerClock.provider(s, SessionType.MORNING)))
    }

    @Test
    fun `the calculation is only information, at plausible times for Lagos`() {
        val calculated = PrayerClock.calculated(testSettings(6.5244, 3.3792), day, lagos)
        assertNotNull(calculated)
        val fajr = calculated!!.getValue(Prayer.FAJR)
        val asr = calculated.getValue(Prayer.ASR)
        assertTrue("fajr=$fajr", fajr > LocalTime.of(4, 45) && fajr < LocalTime.of(5, 45))
        assertTrue("asr=$asr", asr > LocalTime.of(15, 15) && asr < LocalTime.of(16, 30))
    }

    @Test
    fun `hanafi asr is calculated later than standard`() {
        val standard = PrayerClock.calculated(testSettings(6.5244, 3.3792), day, lagos)!!.getValue(Prayer.ASR)
        val hanafi = PrayerClock.calculated(testSettings(6.5244, 3.3792, hanafi = true), day, lagos)!!.getValue(Prayer.ASR)
        assertTrue(hanafi > standard)
    }
}
