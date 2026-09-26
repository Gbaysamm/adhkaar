package org.adhkaar.app.data

/**
 * How quickly a count may follow the last one. A dhikr is said, not tapped: each count asks for
 * at least the time the words take at a calm, ordinary pace, so the orb can't be tapped through.
 * It is a floor, set a little under a steady recitation, so someone reciting well is never held back.
 */
object Pacing {
    const val MS_PER_ARABIC_WORD = 400L
    const val MIN_ARABIC_MS = 700L

    /** A dua written only in the user's language: read a little faster, but never in a blink. */
    const val MS_PER_TRANSLATION_WORD = 300L
    const val MIN_TRANSLATION_MS = 2_000L

    /** Even the longest passage never asks for more than this per count. */
    const val MAX_MS = 30_000L

    fun minMillisPerCount(arabic: String, translation: String): Long {
        val arabicWords = words(arabic)
        return if (arabicWords > 0) {
            (arabicWords * MS_PER_ARABIC_WORD).coerceIn(MIN_ARABIC_MS, MAX_MS)
        } else {
            (words(translation) * MS_PER_TRANSLATION_WORD).coerceIn(MIN_TRANSLATION_MS, MAX_MS)
        }
    }

    /**
     * Words are runs of text holding at least one letter. That leaves out the Qur'anic pause
     * marks (ۚ ۖ) and verse-end numbers (۝١) that stand on their own between the words.
     */
    internal fun words(text: String): Int = text.split(Regex("\\s+")).count { token -> token.any { it.isLetter() } }
}

/**
 * Keeps each dhikr's wait. Only time spent on a dhikr's page counts towards it, so swiping away and
 * back neither resets the wait nor runs it down while the dhikr isn't on screen.
 * Times are from a monotonic clock, so changing the phone's time doesn't move them.
 */
class PaceClock(private val minimums: Map<String, Long>) {
    private val waited = HashMap<String, Long>()
    private var shown: String? = null
    private var shownAt = 0L

    /** The dhikr now on screen. Calling it again for the same one changes nothing. */
    fun show(id: String, now: Long) {
        shown?.let { waited[it] = waitedFor(it, now) }
        shown = id
        shownAt = now
    }

    fun remaining(id: String, now: Long): Long = ((minimums[id] ?: 0L) - waitedFor(id, now)).coerceAtLeast(0L)

    /** 0 just after a count, 1 once the next count is allowed. */
    fun readiness(id: String, now: Long): Float {
        val minimum = minimums[id] ?: 0L
        return if (minimum <= 0L) 1f else (waitedFor(id, now).toFloat() / minimum).coerceIn(0f, 1f)
    }

    /** Takes a count if it's time, and starts the wait for the next one; false if it came too soon. */
    fun count(id: String, now: Long): Boolean {
        if (remaining(id, now) > 0L) return false
        waited[id] = 0L
        if (id == shown) shownAt = now
        return true
    }

    private fun waitedFor(id: String, now: Long): Long =
        (waited[id] ?: 0L) + if (id == shown) (now - shownAt).coerceAtLeast(0L) else 0L
}
