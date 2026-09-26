package org.adhkaar.app.session

import org.adhkaar.app.data.PendingSession
import org.adhkaar.app.data.Strictness

/**
 * When the session alert rings, and what happens while the user is away from the phone.
 * Pure Kotlin so it can be unit tested.
 *
 * The session time rings for [RING_MS]. If the session still hasn't been answered
 * [RERING_DELAY_MS] later it rings once more, then stays silent: the next unlock brings the
 * session up instead (Full screen and Lockdown). Everything ends when the adhkaar window
 * ("ends after" in Settings) closes.
 *
 * A busy user can take a break instead of skipping: nothing rings or blocks until it ends, then
 * the session rings again. A break must end inside the window, so the adhkaar are still said.
 */
object AlertPolicy {
    const val RING_MS = 60_000L
    const val RERING_DELAY_MS = 10 * 60_000L
    /** Silence between repeats: the chime already fades out; a spoken alert needs a breath. */
    const val CHIME_GAP_MS = 1_000L
    const val RECORDING_GAP_MS = 2_000L
    /** The breaks on offer, in minutes. */
    val BREAK_MINUTES = listOf(10, 20, 30, 60)

    /** An alarm-started session stays open for the user-set window, whatever the mode. */
    /**
     * Minutes a session stays enforced: until its adhkaar window closes ([closesAtMillis], e.g.
     * 7:30 in the morning, Isha in the evening), but never longer than the user's [maxMinutes].
     */
    fun windowMinutes(startedAtMillis: Long, closesAtMillis: Long, maxMinutes: Int): Int {
        // A session started after its window (a test from Settings at night) keeps the maximum.
        if (closesAtMillis <= startedAtMillis) return maxMinutes
        return minOf(maxMinutes.toLong(), (closesAtMillis - startedAtMillis) / 60_000L).coerceAtLeast(1L).toInt()
    }

    fun isWindowOpen(pending: PendingSession?, windowMinutes: Int, nowMillis: Long): Boolean =
        pending != null && pending.enforced && nowMillis - pending.startedAtMillis < windowMinutes * 60_000L

    /** When the adhkaar window of [pending] closes: the time by which the session must be done. */
    fun windowEndMillis(pending: PendingSession, windowMinutes: Int): Long =
        pending.startedAtMillis + windowMinutes * 60_000L

    /** The breaks (minutes) that end before the window closes; empty when there's no time left for one. */
    fun breakOptions(nowMillis: Long, windowEndMillis: Long): List<Int> =
        BREAK_MINUTES.filter { nowMillis + it * 60_000L < windowEndMillis }

    /** On a break: no ringing, no bringing the session up, no blocking. */
    fun isPaused(pending: PendingSession?, nowMillis: Long): Boolean =
        pending != null && nowMillis < pending.pausedUntilMillis

    fun shouldReRing(pending: PendingSession?, acknowledged: Boolean, windowMinutes: Int, nowMillis: Long): Boolean =
        !acknowledged && isWindowOpen(pending, windowMinutes, nowMillis) && !isPaused(pending, nowMillis)

    /** Lockdown covers other apps only for an alarm-started session, inside its window, and not on a break. */
    fun isLockdownActive(pending: PendingSession?, strictness: Strictness, windowMinutes: Int, nowMillis: Long): Boolean =
        strictness == Strictness.LOCKDOWN && isWindowOpen(pending, windowMinutes, nowMillis) && !isPaused(pending, nowMillis)

    /** Gentle mode only ever notifies; the stricter modes bring the session up when the user is back. */
    fun opensOnUnlock(strictness: Strictness): Boolean = strictness != Strictness.GENTLE

    /**
     * How many adhkaar are not yet finished. [targets] maps each dhikr id to its count;
     * [progress] is what the session has saved so far.
     */
    fun remaining(targets: List<Pair<String, Int>>, progress: Map<String, Int>): Int =
        targets.count { (id, count) -> (progress[id] ?: 0) < count }
}
