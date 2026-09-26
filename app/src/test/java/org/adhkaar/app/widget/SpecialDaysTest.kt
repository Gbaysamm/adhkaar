package org.adhkaar.app.widget

import org.adhkaar.app.data.EventKind
import org.adhkaar.app.data.HijriDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SpecialDaysTest {
    private val wednesday = LocalDate.of(2026, 9, 23)
    private val friday = LocalDate.of(2026, 9, 25)

    /** A Hijri [month] whose 1st falls on [start]; the day before is the 30th of the month before. */
    private fun month(month: Int, start: LocalDate): (LocalDate) -> HijriDay = { date ->
        if (date.isBefore(start)) {
            HijriDay(30, month - 1, 1447)
        } else {
            HijriDay(ChronoUnit.DAYS.between(start, date).toInt() + 1, month, 1447)
        }
    }

    @Test
    fun `counts down to Arafah through the ten days already under way`() {
        // Wednesday is 6 Dhul Hijjah: the ten days began on the 1st, Friday comes first, Arafah is Saturday.
        val next = SpecialDays.next(wednesday, month(12, wednesday.minusDays(5)))
        assertEquals(SpecialDays.Upcoming(EventKind.ARAFAH, 3), next)
    }

    @Test
    fun `Jumuah counts on the day itself`() {
        assertEquals(SpecialDays.Upcoming(EventKind.JUMUAH, 0), SpecialDays.next(friday, month(4, friday.minusDays(1))))
    }

    @Test
    fun `a coming Friday is skipped for the white days after it`() {
        // Wednesday is the 10th; Friday the 12th; the white days begin Saturday the 13th.
        val next = SpecialDays.next(wednesday, month(4, wednesday.minusDays(9)))
        assertEquals(SpecialDays.Upcoming(EventKind.WHITE_DAYS, 3), next)
    }

    @Test
    fun `the first of Ramadan is news, its fifth day is not`() {
        assertEquals(SpecialDays.Upcoming(EventKind.RAMADAN, 0), SpecialDays.next(wednesday, month(9, wednesday)))
        // On the 5th, the next beginning is the last ten nights on the 21st (no white days in Ramadan).
        assertEquals(SpecialDays.Upcoming(EventKind.LAST_TEN_NIGHTS, 16), SpecialDays.next(wednesday, month(9, wednesday.minusDays(4))))
    }
}
