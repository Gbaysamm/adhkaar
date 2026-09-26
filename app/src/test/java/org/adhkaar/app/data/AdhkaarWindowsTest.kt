package org.adhkaar.app.data

import org.adhkaar.app.schedule.PrayerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class AdhkaarWindowsTest {
    private val lagos = ZoneId.of("Africa/Lagos")
    private val day = LocalDate.of(2026, 9, 25)

    private fun prayers(
        fajr: String = "05:10", sunrise: String = "06:25", dhuhr: String = "12:30",
        asr: String = "15:50", maghrib: String = "18:35", isha: String = "19:45",
    ) = listOf(
        PrayerTime("Fajr", LocalTime.parse(fajr)), PrayerTime("Sunrise", LocalTime.parse(sunrise)),
        PrayerTime("Dhuhr", LocalTime.parse(dhuhr)), PrayerTime("Asr", LocalTime.parse(asr)),
        PrayerTime("Maghrib", LocalTime.parse(maghrib)), PrayerTime("Isha", LocalTime.parse(isha)),
    )

    private fun at(time: String, date: LocalDate = day) = ZonedDateTime.of(date, LocalTime.parse(time), lagos)

    private fun status(time: String, type: SessionType, prayers: List<PrayerTime> = prayers()) =
        AdhkaarWindows.status(at(time), type) { AdhkaarWindows.window(prayers, type, it, lagos) }

    @Test
    fun `morning runs from Fajr to Dhuhr, best before sunrise`() {
        val w = AdhkaarWindows.window(prayers(), SessionType.MORNING, day, lagos)
        assertEquals(at("05:10"), w.opens)
        assertEquals(at("06:25"), w.idealEnd)
        assertEquals(at("12:30"), w.closes)
        assertTrue(w.byPrayer)
    }

    @Test
    fun `evening runs from Asr to Isha, best before Maghrib`() {
        val w = AdhkaarWindows.window(prayers(), SessionType.EVENING, day, lagos)
        assertEquals(at("15:50"), w.opens)
        assertEquals(at("18:35"), w.idealEnd)
        assertEquals(at("19:45"), w.closes)
    }

    @Test
    fun `evening cannot start before noon`() {
        val s = status("11:38", SessionType.EVENING)
        assertTrue(s is AdhkaarWindows.Status.NotYet)
        assertFalse(s.canStart)
        assertEquals(at("15:50"), (s as AdhkaarWindows.Status.NotYet).window.opens)
    }

    @Test
    fun `ideal, late and closed follow the prayer times`() {
        assertTrue(status("05:30", SessionType.MORNING) is AdhkaarWindows.Status.Ideal)
        assertTrue(status("06:25", SessionType.MORNING) is AdhkaarWindows.Status.Late)
        assertTrue(status("12:29", SessionType.MORNING).canStart)
        assertTrue(status("16:00", SessionType.EVENING) is AdhkaarWindows.Status.Ideal)
        assertTrue(status("19:00", SessionType.EVENING) is AdhkaarWindows.Status.Late)
    }

    @Test
    fun `after closing, the next opening is tomorrow`() {
        val s = status("13:00", SessionType.MORNING)
        assertTrue(s is AdhkaarWindows.Status.Closed)
        assertFalse(s.canStart)
        assertEquals(at("05:10", day.plusDays(1)), (s as AdhkaarWindows.Status.Closed).next.opens)
    }

    @Test
    fun `before Fajr the morning opens later the same day`() {
        val s = status("02:00", SessionType.MORNING)
        assertEquals(at("05:10"), (s as AdhkaarWindows.Status.NotYet).window.opens)
    }

    @Test
    fun `times out of order fall back to the defaults`() {
        // Asr set after Maghrib.
        val w = AdhkaarWindows.window(prayers(asr = "19:00"), SessionType.EVENING, day, lagos)
        assertFalse(w.byPrayer)
        assertEquals(at("15:55"), w.opens)
        assertEquals(at("18:35"), w.idealEnd)
        assertEquals(at("19:50"), w.closes)
        assertTrue(status("11:38", SessionType.EVENING, prayers(asr = "19:00")) is AdhkaarWindows.Status.NotYet)
    }

    @Test
    fun `an Isha after midnight falls back to the defaults`() {
        val w = AdhkaarWindows.window(prayers(maghrib = "22:10", isha = "00:40"), SessionType.EVENING, day, lagos)
        assertFalse(w.byPrayer)
    }

    @Test
    fun `on time and late split at the ideal end`() {
        val w = AdhkaarWindows.window(prayers(), SessionType.MORNING, day, lagos)
        assertTrue(AdhkaarWindows.onTime(w, 5 * 60 + 40))
        assertFalse(AdhkaarWindows.late(w, 5 * 60 + 40))
        assertTrue(AdhkaarWindows.late(w, 6 * 60 + 25))
        assertFalse(AdhkaarWindows.onTime(w, 6 * 60 + 25))
        // Before the window opens is neither on time nor late.
        assertFalse(AdhkaarWindows.onTime(w, 4 * 60))
        assertFalse(AdhkaarWindows.late(w, 4 * 60))
    }

    @Test
    fun `punctuality counts the first completion of each session`() {
        val month = YearMonth.of(2026, 9)
        val times = setOf(
            "2026-09-24|morning|340", // 5:40, on time
            "2026-09-24|evening|1150", // 19:10, late
            "2026-09-25|morning|420", // 7:00, late
            "2026-09-25|morning|600", // said again; the first one counts
            "2026-09-25|evening|1000", // 16:40, on time
            "2026-08-31|evening|1000", // another month
        )
        val window = { d: LocalDate, t: SessionType -> AdhkaarWindows.window(prayers(), t, d, lagos) }
        assertEquals(Punctuality(onTime = 2, total = 4), Insights.punctuality(times, month, window))
        assertEquals(
            mapOf(day.minusDays(1) to setOf(SessionType.EVENING), day to setOf(SessionType.MORNING)),
            Insights.lateDays(times, month, window),
        )
    }

    @Test
    fun `after salah follows each of the five prayers`() {
        // Asr at 15:50: its after-salah adhkaar are suggested for 15 minutes, until 16:05.
        val m = CollectionMoments.at(LocalTime.of(16, 0), prayers(), prayers(), bedtimeMinute = -1)
        assertEquals(CollectionMoments.Moment(CollectionMoments.AFTER_SALAH, "Asr"), m)
        assertEquals("Fajr", CollectionMoments.at(LocalTime.of(5, 20), prayers(), prayers(), -1)?.afterPrayer)
        assertNull(CollectionMoments.at(LocalTime.of(16, 10), prayers(), prayers(), -1))
        assertNull(CollectionMoments.at(LocalTime.of(14, 0), prayers(), prayers(), -1))
    }

    @Test
    fun `on waking in the early morning, before sleep at night`() {
        assertEquals(CollectionMoments.WAKING, CollectionMoments.at(LocalTime.of(4, 30), prayers(), prayers(), -1)?.collectionId)
        assertEquals(CollectionMoments.WAKING, CollectionMoments.at(LocalTime.of(7, 0), prayers(), prayers(), -1)?.collectionId)
        // No bedtime: from the end of Isha's after-salah span, through midnight.
        assertEquals(CollectionMoments.BEFORE_SLEEP, CollectionMoments.at(LocalTime.of(21, 0), prayers(), prayers(), -1)?.collectionId)
        assertEquals(CollectionMoments.BEFORE_SLEEP, CollectionMoments.at(LocalTime.of(1, 0), prayers(), prayers(), -1)?.collectionId)
    }

    @Test
    fun `a bedtime moves the before sleep suggestion`() {
        val bedtime = 23 * 60
        assertNull(CollectionMoments.at(LocalTime.of(21, 0), prayers(), prayers(), bedtime))
        assertEquals(CollectionMoments.BEFORE_SLEEP, CollectionMoments.at(LocalTime.of(22, 40), prayers(), prayers(), bedtime)?.collectionId)
    }

    @Test
    fun `after salah follows the congregation, not the prayer time`() {
        // The Dhuhr time is 12:30 but the masjid prays at 13:20.
        val congregation = prayers(dhuhr = "13:20")
        assertNull(CollectionMoments.at(LocalTime.of(12, 40), prayers(), congregation, -1))
        assertEquals("Dhuhr", CollectionMoments.at(LocalTime.of(13, 30), prayers(), congregation, -1)?.afterPrayer)
        assertEquals(
            CollectionMoments.Moment(CollectionMoments.AFTER_SALAH, "Dhuhr") to LocalTime.of(13, 20),
            CollectionMoments.next(LocalTime.of(12, 40), congregation, -1),
        )
    }

    @Test
    fun `with no bedtime, before sleep starts after the Isha congregation`() {
        val congregation = prayers(isha = "20:10")
        assertEquals("Isha", CollectionMoments.at(LocalTime.of(20, 20), prayers(), congregation, -1)?.afterPrayer)
        assertEquals(CollectionMoments.BEFORE_SLEEP, CollectionMoments.at(LocalTime.of(20, 25), prayers(), congregation, -1)?.collectionId)
    }
}
