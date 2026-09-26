package org.adhkaar.app.session

import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.schedule.testSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MissedAdhkaarTest {
    private val lagos = ZoneId.of("Africa/Lagos")
    private val day = LocalDate.of(2026, 9, 27)
    private val settings = testSettings()
    private fun at(h: Int, m: Int, date: LocalDate = day) = ZonedDateTime.of(date, LocalTime.of(h, m), lagos)
    private val since = day.minusDays(30)

    @Test
    fun `nothing is missed while the adhkaar time is still open`() {
        assertNull(MissedAdhkaar.latest(settings, emptySet(), emptySet(), day, at(7, 0)))
    }

    @Test
    fun `the morning is missed once its time has ended unread`() {
        assertEquals(MissedAdhkaar.Missed(SessionType.MORNING, day), MissedAdhkaar.latest(settings, emptySet(), emptySet(), since, at(9, 0)))
    }

    @Test
    fun `a morning that was read is not missed`() {
        val history = setOf(Streaks.key(day, SessionType.MORNING))
        assertNull(MissedAdhkaar.latest(settings, history, emptySet(), since, at(9, 0)))
    }

    @Test
    fun `a missed session already shown isn't shown again, nor anything older`() {
        val seen = setOf(MissedAdhkaar.Missed(SessionType.MORNING, day).key)
        assertNull(MissedAdhkaar.latest(settings, emptySet(), seen, since, at(9, 0)))
    }

    @Test
    fun `before the morning ends, last night's evening is the one that counts`() {
        assertEquals(MissedAdhkaar.Missed(SessionType.EVENING, day.minusDays(1)), MissedAdhkaar.latest(settings, emptySet(), emptySet(), since, at(5, 0)))
    }

    @Test
    fun `nothing from before the app was first opened`() {
        assertNull(MissedAdhkaar.latest(settings, emptySet(), emptySet(), day, at(5, 0)))
    }
}
