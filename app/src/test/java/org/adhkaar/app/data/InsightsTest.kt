package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class InsightsTest {
    private val today = LocalDate.of(2026, 9, 25) // Friday
    private fun m(d: LocalDate) = Streaks.key(d, SessionType.MORNING)
    private fun e(d: LocalDate) = Streaks.key(d, SessionType.EVENING)

    @Test
    fun `month counts sessions and full days`() {
        val history = setOf(m(today), e(today), m(today.minusDays(1)), e(LocalDate.of(2026, 8, 31)))
        val sept = YearMonth.of(2026, 9)
        assertEquals(30, Insights.month(history, sept).size)
        assertEquals(3, Insights.sessions(history, sept))
        assertEquals(1, Insights.fullDays(history, sept))
    }

    @Test
    fun `weekday rates start on Sunday and ignore the future`() {
        // Done on the last two Fridays only.
        val history = setOf(m(today), m(today.minusDays(7)))
        val rates = Insights.weekdayRates(history, today, weeks = 4)
        assertEquals(DayOfWeek.SUNDAY, rates.first().day)
        val friday = rates.first { it.day == DayOfWeek.FRIDAY }
        assertEquals(2, friday.done)
        assertEquals(4, friday.total)
        assertEquals(0.5f, friday.rate)
        assertEquals(0, rates.first { it.day == DayOfWeek.SATURDAY }.done)
    }

    @Test
    fun `usual time is the median`() {
        val times = setOf("2026-09-23|morning|350", "2026-09-24|morning|340", "2026-09-25|morning|600", "2026-09-25|evening|970")
        assertEquals(350, Insights.usualMinute(times, SessionType.MORNING))
        assertEquals(970, Insights.usualMinute(times, SessionType.EVENING))
        assertNull(Insights.usualMinute(emptySet(), SessionType.MORNING))
    }

    @Test
    fun `collection counts stay within the month`() {
        val log = setOf(
            "2026-09-25|after_salah|1", "2026-09-25|after_salah|2", "2026-09-24|before_sleep|3", "2026-08-30|after_salah|4",
        )
        assertEquals(mapOf("after_salah" to 2, "before_sleep" to 1), Insights.collectionCounts(log, YearMonth.of(2026, 9)))
    }
}
