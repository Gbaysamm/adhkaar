package org.adhkaar.app.schedule

import org.adhkaar.app.data.SessionSchedule
import org.adhkaar.app.data.TimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SessionTimeCalculatorTest {
    private val zone = ZoneId.of("Africa/Lagos")
    private val fajr: (LocalDate) -> LocalTime? = { LocalTime.of(5, 20) }
    private val noLocation: (LocalDate) -> LocalTime? = { null }

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone)
    private fun prayer(offset: Int = 15) = SessionSchedule(true, TimeMode.PRAYER, fixedMinuteOfDay = 6 * 60, offsetMinutes = offset)
    private fun fixed(minute: Int) = SessionSchedule(true, TimeMode.FIXED, fixedMinuteOfDay = minute, offsetMinutes = 15)

    @Test
    fun `prayer mode adds the offset to the prayer time`() {
        assertEquals(at(25, 5, 35), SessionTimeCalculator.next(prayer(), at(25, 4, 0), null, fajr))
    }

    @Test
    fun `falls back to the fixed time without a location`() {
        assertEquals(at(25, 6, 0), SessionTimeCalculator.next(prayer(), at(25, 4, 0), null, noLocation))
    }

    @Test
    fun `after today's time the next one is tomorrow`() {
        assertEquals(at(26, 5, 35), SessionTimeCalculator.next(prayer(), at(25, 5, 35), null, fajr))
    }

    @Test
    fun `skips today when already completed`() {
        val completedToday = LocalDate.of(2026, 9, 25)
        assertEquals(at(26, 17, 0), SessionTimeCalculator.next(fixed(17 * 60), at(25, 9, 0), completedToday, noLocation))
    }

    @Test
    fun `disabled schedule has no next time`() {
        assertNull(SessionTimeCalculator.next(prayer().copy(enabled = false), at(25, 4, 0), null, fajr))
    }

    @Test
    fun `missed session inside the grace window is caught up`() {
        assertTrue(SessionTimeCalculator.missedToday(fixed(6 * 60), at(25, 7, 0), null, graceMinutes = 180, prayerTime = noLocation))
    }

    @Test
    fun `missed session outside the grace window is not caught up`() {
        assertFalse(SessionTimeCalculator.missedToday(fixed(6 * 60), at(25, 10, 0), null, graceMinutes = 180, prayerTime = noLocation))
    }

    @Test
    fun `completed session is never caught up`() {
        val today = LocalDate.of(2026, 9, 25)
        assertFalse(SessionTimeCalculator.missedToday(fixed(6 * 60), at(25, 7, 0), today, graceMinutes = 180, prayerTime = noLocation))
    }

    @Test
    fun `future session is not missed`() {
        assertFalse(SessionTimeCalculator.missedToday(fixed(6 * 60), at(25, 5, 0), null, graceMinutes = 180, prayerTime = noLocation))
    }
}
