package org.adhkaar.app.session

import org.adhkaar.app.data.PendingSession
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertPolicyTest {
    private val started = 1_000_000L
    private val enforced = PendingSession(SessionType.MORNING, LocalDate.of(2026, 9, 26), started, enforced = true)
    private val oneHour = 60 * 60_000L

    @Test
    fun `the window is open only for an alarm-started session within its minutes`() {
        assertTrue(AlertPolicy.isWindowOpen(enforced, 180, started + oneHour))
        assertFalse(AlertPolicy.isWindowOpen(enforced, 180, started + 3 * oneHour))
        assertFalse(AlertPolicy.isWindowOpen(enforced.copy(enforced = false), 180, started + oneHour))
        assertFalse(AlertPolicy.isWindowOpen(null, 180, started))
    }

    @Test
    fun `rings again only if unanswered and the window is still open`() {
        val reRingAt = started + AlertPolicy.RERING_DELAY_MS
        assertTrue(AlertPolicy.shouldReRing(enforced, acknowledged = false, windowMinutes = 180, nowMillis = reRingAt))
        assertFalse(AlertPolicy.shouldReRing(enforced, acknowledged = true, windowMinutes = 180, nowMillis = reRingAt))
        // A window shorter than the delay has already closed: stay silent.
        assertFalse(AlertPolicy.shouldReRing(enforced, acknowledged = false, windowMinutes = 5, nowMillis = reRingAt))
        assertFalse(AlertPolicy.shouldReRing(null, acknowledged = false, windowMinutes = 180, nowMillis = reRingAt))
    }

    @Test
    fun `lockdown applies only to enforced sessions within the window`() {
        assertTrue(AlertPolicy.isLockdownActive(enforced, Strictness.LOCKDOWN, 180, started + oneHour))
        assertFalse(AlertPolicy.isLockdownActive(enforced, Strictness.LOCKDOWN, 180, started + 3 * oneHour))
        assertFalse(AlertPolicy.isLockdownActive(enforced.copy(enforced = false), Strictness.LOCKDOWN, 180, started + oneHour))
        assertFalse(AlertPolicy.isLockdownActive(enforced, Strictness.FULL_SCREEN, 180, started + oneHour))
        assertFalse(AlertPolicy.isLockdownActive(null, Strictness.LOCKDOWN, 180, started))
    }

    @Test
    fun `only breaks that end before the window closes are offered`() {
        val windowEnd = AlertPolicy.windowEndMillis(enforced, 180)
        assertEquals(started + 3 * oneHour, windowEnd)
        val minute = 60_000L
        assertEquals(listOf(10, 20, 30, 60), AlertPolicy.breakOptions(started + oneHour, windowEnd))
        // 45 minutes left: the hour no longer fits.
        assertEquals(listOf(10, 20, 30), AlertPolicy.breakOptions(windowEnd - 45 * minute, windowEnd))
        // A break ending exactly at the close would leave no time to say them.
        assertEquals(listOf(10, 20), AlertPolicy.breakOptions(windowEnd - 30 * minute, windowEnd))
        assertEquals(emptyList<Int>(), AlertPolicy.breakOptions(windowEnd - 10 * minute, windowEnd))
        assertEquals(emptyList<Int>(), AlertPolicy.breakOptions(windowEnd + minute, windowEnd))
    }

    @Test
    fun `a break suppresses ringing and lockdown until it ends`() {
        val breakEnd = started + oneHour
        val onBreak = enforced.copy(pausedUntilMillis = breakEnd)
        val during = breakEnd - 60_000L

        assertTrue(AlertPolicy.isPaused(onBreak, during))
        assertFalse(AlertPolicy.isLockdownActive(onBreak, Strictness.LOCKDOWN, 180, during))
        assertFalse(AlertPolicy.shouldReRing(onBreak, acknowledged = false, windowMinutes = 180, nowMillis = during))
        // The window itself stays open: the adhkaar are still due.
        assertTrue(AlertPolicy.isWindowOpen(onBreak, 180, during))

        assertFalse(AlertPolicy.isPaused(onBreak, breakEnd))
        assertTrue(AlertPolicy.isLockdownActive(onBreak, Strictness.LOCKDOWN, 180, breakEnd))
        assertTrue(AlertPolicy.shouldReRing(onBreak, acknowledged = false, windowMinutes = 180, nowMillis = breakEnd))
        assertFalse(AlertPolicy.isPaused(enforced, during))
        assertFalse(AlertPolicy.isPaused(null, during))
    }

    @Test
    fun `only the stricter modes open the session on unlock`() {
        assertFalse(AlertPolicy.opensOnUnlock(Strictness.GENTLE))
        assertTrue(AlertPolicy.opensOnUnlock(Strictness.FULL_SCREEN))
        assertTrue(AlertPolicy.opensOnUnlock(Strictness.LOCKDOWN))
    }

    @Test
    fun `remaining counts adhkaar whose count isn't reached`() {
        val targets = listOf("a" to 1, "b" to 3, "c" to 33)
        assertEquals(3, AlertPolicy.remaining(targets, emptyMap()))
        assertEquals(2, AlertPolicy.remaining(targets, mapOf("a" to 1, "b" to 2)))
        assertEquals(0, AlertPolicy.remaining(targets, mapOf("a" to 1, "b" to 3, "c" to 33)))
    }

    @Test
    fun `the lock ends when the adhkaar window closes, at most the maximum`() {
        val six = 6 * 3_600_000L
        // Morning from 6:00, window closing at 7:30: 90 minutes, under a 3-hour maximum.
        assertEquals(90, AlertPolicy.windowMinutes(six, six + 90 * 60_000L, 180))
        // A window longer than the maximum is capped by it.
        assertEquals(180, AlertPolicy.windowMinutes(six, six + 300 * 60_000L, 180))
        // Started after the window closed (a test at night): the maximum applies.
        assertEquals(180, AlertPolicy.windowMinutes(six, six - 60_000L, 180))
    }
}
