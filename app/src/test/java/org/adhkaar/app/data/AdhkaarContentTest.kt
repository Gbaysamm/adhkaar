package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the content file against mistakes when contributors edit or translate it. */
class AdhkaarContentTest {
    private val all = AdhkaarRepository.parse(File("src/main/assets/adhkaar.json").readText())

    @Test
    fun `ids are unique`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `every dhikr is complete`() {
        all.forEach { d ->
            assertTrue("${d.id}: count", d.count > 0)
            assertTrue("${d.id}: sessions", d.sessions.isNotEmpty() && d.sessions.all { SessionType.fromKey(it) != null })
            assertFalse("${d.id}: arabic", d.arabic.isBlank())
            assertFalse("${d.id}: transliteration", d.transliteration.isBlank())
            assertFalse("${d.id}: translation", d.translation.isBlank())
            assertFalse("${d.id}: reference", d.reference.isBlank())
        }
    }

    @Test
    fun `both sessions have adhkaar`() {
        assertTrue(AdhkaarRepository.resolve(all, SessionType.MORNING).size >= 10)
        assertTrue(AdhkaarRepository.resolve(all, SessionType.EVENING).size >= 10)
    }

    @Test
    fun `evening wording replaces morning wording`() {
        val morning = AdhkaarRepository.resolve(all, SessionType.MORNING).first { it.id == "asbahna_al_mulk" }
        val evening = AdhkaarRepository.resolve(all, SessionType.EVENING).first { it.id == "asbahna_al_mulk" }
        assertNotEquals(morning.arabic, evening.arabic)
        assertTrue(evening.arabic.startsWith("أَمْسَيْنَا"))
    }

    @Test
    fun `morning-only and evening-only adhkaar stay in their session`() {
        val evening = AdhkaarRepository.resolve(all, SessionType.EVENING).map { it.id }
        val morning = AdhkaarRepository.resolve(all, SessionType.MORNING).map { it.id }
        assertFalse("adada_khalqihi" in evening)
        assertFalse("kalimat_at_tammat" in morning)
    }

    @Test
    fun `evening sessions play the evening-worded recording`() {
        val evening = AdhkaarRepository.resolve(all, SessionType.EVENING)
        val morning = AdhkaarRepository.resolve(all, SessionType.MORNING)
        assertEquals("asbahna_al_mulk_evening", evening.first { it.id == "asbahna_al_mulk" }.audioId)
        assertEquals("asbahna_al_mulk", morning.first { it.id == "asbahna_al_mulk" }.audioId)
        // Same words morning and evening: one recording.
        assertEquals("ayat_al_kursi", evening.first { it.id == "ayat_al_kursi" }.audioId)
    }
}
