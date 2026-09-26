package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IslamicCalendarTest {
    private val wednesday = LocalDate.of(2026, 9, 23)
    private val friday = LocalDate.of(2026, 9, 25)
    private fun kinds(date: LocalDate, h: HijriDay) = IslamicCalendar.eventsFor(date, h).map { it.kind }

    @Test
    fun `every Friday is Jumuah`() {
        assertTrue(EventKind.JUMUAH in kinds(friday, HijriDay(2, 4, 1448)))
        assertFalse(EventKind.JUMUAH in kinds(wednesday, HijriDay(2, 4, 1448)))
    }

    @Test
    fun `white days are the 13th to 15th`() {
        assertTrue(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(13, 4, 1448)))
        assertTrue(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(15, 4, 1448)))
        assertFalse(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(16, 4, 1448)))
    }

    @Test
    fun `no white-day fast in Ramadan or on 13 Dhul Hijjah`() {
        assertFalse(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(14, 9, 1448)))
        assertFalse(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(13, 12, 1448)))
        assertTrue(EventKind.WHITE_DAYS in kinds(wednesday, HijriDay(14, 12, 1448)))
    }

    @Test
    fun `Dhul Hijjah days`() {
        assertEquals(listOf(EventKind.DHUL_HIJJAH_TEN), kinds(wednesday, HijriDay(5, 12, 1448)))
        assertEquals(listOf(EventKind.ARAFAH), kinds(wednesday, HijriDay(9, 12, 1448)))
        assertEquals(listOf(EventKind.EID_AL_ADHA), kinds(wednesday, HijriDay(10, 12, 1448)))
    }

    @Test
    fun `Ramadan and its last ten nights`() {
        assertEquals(listOf(EventKind.RAMADAN), kinds(wednesday, HijriDay(20, 9, 1448)))
        assertEquals(listOf(EventKind.LAST_TEN_NIGHTS), kinds(wednesday, HijriDay(21, 9, 1448)))
        assertEquals(listOf(EventKind.EID_AL_FITR), kinds(wednesday, HijriDay(1, 10, 1448)))
    }

    @Test
    fun `Tasua and Ashura`() {
        assertEquals(listOf(EventKind.TASUA), kinds(wednesday, HijriDay(9, 1, 1448)))
        assertEquals(listOf(EventKind.ASHURA), kinds(wednesday, HijriDay(10, 1, 1448)))
    }

    @Test
    fun `eve reminders are for fasts and Eid, not ordinary days`() {
        assertEquals(EventKind.ARAFAH, IslamicCalendar.eveReminder(wednesday, HijriDay(9, 12, 1448))?.kind)
        assertEquals(EventKind.WHITE_DAYS, IslamicCalendar.eveReminder(wednesday, HijriDay(13, 4, 1448))?.kind)
        assertNull(IslamicCalendar.eveReminder(wednesday, HijriDay(14, 4, 1448)))
        assertNull(IslamicCalendar.eveReminder(wednesday, HijriDay(5, 4, 1448)))
    }

    @Test
    fun `offset shifts the Hijri date`() {
        val base = IslamicCalendar.hijri(friday, 0)
        val shifted = IslamicCalendar.hijri(friday, -1)
        assertEquals(IslamicCalendar.hijri(friday.minusDays(1), 0), shifted)
        assertTrue(base != shifted)
    }
}
