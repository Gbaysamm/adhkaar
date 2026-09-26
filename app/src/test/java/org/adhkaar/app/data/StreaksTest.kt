package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 9, 25)
    private fun h(vararg daysAgo: Long) = daysAgo.map { Streaks.key(today.minusDays(it), SessionType.MORNING) }.toSet()

    @Test
    fun `streak counts back from today when today is done`() {
        assertEquals(3, Streaks.current(h(0, 1, 2, 4), today))
    }

    @Test
    fun `streak still counts from yesterday before today's session`() {
        assertEquals(2, Streaks.current(h(1, 2, 4), today))
    }

    @Test
    fun `a missed yesterday resets the streak`() {
        assertEquals(0, Streaks.current(h(2, 3), today))
    }

    @Test
    fun `best streak finds the longest run`() {
        assertEquals(4, Streaks.best(h(0, 1, 5, 6, 7, 8, 10), today))
    }

    @Test
    fun `evening alone counts as a day`() {
        val history = setOf(Streaks.key(today, SessionType.EVENING))
        assertEquals(1, Streaks.current(history, today))
    }
}
