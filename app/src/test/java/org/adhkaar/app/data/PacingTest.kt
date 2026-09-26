package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacingTest {
    private val ikhlas = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\n" +
        "قُلْ هُوَ اللَّهُ أَحَدٌ ۝١ اللَّهُ الصَّمَدُ ۝٢ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝٣ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ ۝٤"

    private val ayatAlKursi = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ " +
        "لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ " +
        "يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ " +
        "وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ"

    @Test
    fun `a short tasbih takes about a second`() {
        val ms = Pacing.minMillisPerCount("سُبْحَانَ اللَّهِ", "Glory be to Allah")
        assertTrue(ms in 700L..1_200L)
    }

    @Test
    fun `a single word still takes the minimum`() {
        assertEquals(Pacing.MIN_ARABIC_MS, Pacing.minMillisPerCount("غُفْرَانَكَ", "I seek Your forgiveness"))
    }

    @Test
    fun `pause marks and verse numbers are not words`() {
        assertEquals(19, Pacing.words(ikhlas))
        assertEquals(50, Pacing.words(ayatAlKursi))
    }

    @Test
    fun `one of the Quls takes several seconds`() {
        assertTrue(Pacing.minMillisPerCount(ikhlas, "") in 5_000L..8_000L)
    }

    @Test
    fun `Ayat al-Kursi takes twenty seconds or more, within the cap`() {
        assertTrue(Pacing.minMillisPerCount(ayatAlKursi, "") in 20_000L..Pacing.MAX_MS)
    }

    @Test
    fun `a very long passage is capped`() {
        val long = List(200) { "اللَّهُ" }.joinToString(" ")
        assertEquals(Pacing.MAX_MS, Pacing.minMillisPerCount(long, ""))
    }

    @Test
    fun `a dua only in English is paced by its translation`() {
        val words = "O Allah forgive my parents and have mercy on them as they raised me when I was small"
        assertEquals(18 * Pacing.MS_PER_TRANSLATION_WORD, Pacing.minMillisPerCount("  ", words))
        assertEquals(Pacing.MIN_TRANSLATION_MS, Pacing.minMillisPerCount("", "Ameen"))
        assertEquals(Pacing.MIN_TRANSLATION_MS, Pacing.minMillisPerCount("", ""))
    }

    @Test
    fun `a count comes only after the wait since the page was shown`() {
        val clock = PaceClock(mapOf("a" to 1_000L))
        clock.show("a", now = 0L)
        assertFalse(clock.count("a", now = 400L))
        assertEquals(0.5f, clock.readiness("a", now = 500L), 0.001f)
        assertTrue(clock.count("a", now = 1_000L))
    }

    @Test
    fun `each count starts the wait again`() {
        val clock = PaceClock(mapOf("a" to 1_000L))
        clock.show("a", now = 0L)
        assertTrue(clock.count("a", now = 1_000L))
        assertFalse(clock.count("a", now = 1_500L))
        assertEquals(500L, clock.remaining("a", now = 1_500L))
        assertTrue(clock.count("a", now = 2_000L))
    }

    @Test
    fun `time away from the page does not count, and coming back does not reset`() {
        val clock = PaceClock(mapOf("a" to 1_000L, "b" to 1_000L))
        clock.show("a", now = 0L)
        clock.show("b", now = 600L)
        // Ten seconds on another page: "a" still has its remaining 400 ms to wait.
        clock.show("a", now = 10_600L)
        assertEquals(400L, clock.remaining("a", now = 10_600L))
        assertFalse(clock.count("a", now = 10_900L))
        assertTrue(clock.count("a", now = 11_000L))
    }

    @Test
    fun `showing the same page again changes nothing`() {
        val clock = PaceClock(mapOf("a" to 1_000L))
        clock.show("a", now = 0L)
        clock.show("a", now = 700L)
        assertEquals(300L, clock.remaining("a", now = 700L))
    }

    @Test
    fun `an unknown dhikr is never held back`() {
        val clock = PaceClock(emptyMap())
        assertEquals(1f, clock.readiness("x", now = 0L), 0f)
        assertTrue(clock.count("x", now = 0L))
    }
}
