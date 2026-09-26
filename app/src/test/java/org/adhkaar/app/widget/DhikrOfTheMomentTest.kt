package org.adhkaar.app.widget

import org.adhkaar.app.data.SessionDhikr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DhikrOfTheMomentTest {
    private fun dhikr(id: String, arabic: String = "سُبْحَانَ اللَّهِ $id", translation: String = "Glory be to Allah") =
        SessionDhikr(id, id, 1, arabic, "", translation, "", null)

    private val pool = listOf("a", "b", "c", "d", "e").map { dhikr(it) }
    private val morning = LocalDateTime.of(2026, 9, 26, 7, 10)

    @Test
    fun `the same moment gives the same dhikr`() {
        assertEquals(DhikrOfTheMoment.pick(pool, morning), DhikrOfTheMoment.pick(pool, morning.withMinute(55).withHour(8)))
    }

    @Test
    fun `every short dhikr comes round before any repeats`() {
        val turns = pool.indices.map { DhikrOfTheMoment.pick(pool, morning.plusHours(DhikrOfTheMoment.HOURS_PER_TURN * it.toLong()))!!.id }
        assertEquals(pool.map { it.id }.toSet(), turns.toSet())
    }

    @Test
    fun `turns continue across midnight`() {
        val lateTurns = (0 until pool.size).map { DhikrOfTheMoment.pick(pool, LocalDateTime.of(2026, 9, 26, 21, 0).plusHours(3L * it))!!.id }
        assertEquals(pool.size, lateTurns.toSet().size)
    }

    @Test
    fun `long adhkaar are left out`() {
        val long = dhikr("long", arabic = "ا".repeat(DhikrOfTheMoment.MAX_ARABIC + 1))
        val wordy = dhikr("wordy", translation = "x".repeat(DhikrOfTheMoment.MAX_MEANING + 1))
        val picks = (0 until 24).map { DhikrOfTheMoment.pick(pool + long + wordy, morning.plusHours(3L * it))!!.id }
        assertTrue(picks.none { it == "long" || it == "wordy" })
    }

    @Test
    fun `nothing short enough gives nothing`() {
        assertNull(DhikrOfTheMoment.pick(listOf(dhikr("long", arabic = "ا".repeat(500))), morning))
    }
}
