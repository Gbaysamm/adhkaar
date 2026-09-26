package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards collections.json: every item complete, every ref valid, counts sensible. */
class CollectionsContentTest {
    private val base = AdhkaarRepository.parse(File("src/main/assets/adhkaar.json").readText())
    private val collections = CollectionsRepository.parse(File("src/main/assets/collections.json").readText(), base)

    @Test
    fun `the expected collections exist`() {
        assertEquals(listOf("after_salah", "before_sleep", "waking", "daily"), collections.map { it.id })
    }

    @Test
    fun `every item is complete and unique within its collection`() {
        collections.forEach { c ->
            assertEquals(c.id, c.items.size, c.items.map { it.id }.toSet().size)
            c.items.forEach { d ->
                assertTrue("${c.id}/${d.id}: count", d.count > 0)
                assertFalse("${c.id}/${d.id}: arabic", d.arabic.isBlank())
                assertFalse("${c.id}/${d.id}: translation", d.translation.isBlank())
                assertFalse("${c.id}/${d.id}: reference", d.reference.isBlank())
            }
        }
    }

    @Test
    fun `sessions have completion words, reference lists don't need them`() {
        collections.filter { it.mode == CollectionMode.SESSION }.forEach { assertNotNull(it.id, it.completion) }
        assertEquals(CollectionMode.REFERENCE, collections.first { it.id == "daily" }.mode)
    }

    @Test
    fun `reused items take their own count and source`() {
        val sleep = collections.first { it.id == "before_sleep" }
        val ikhlas = sleep.items.first { it.id == "al_ikhlas" }
        assertEquals(3, ikhlas.count)
        assertEquals("al-Bukhari 5017", ikhlas.reference)
        val salah = collections.first { it.id == "after_salah" }
        assertEquals(1, salah.items.first { it.id == "al_ikhlas" }.count)
    }

    @Test
    fun `tasbih after salah makes a hundred, before sleep too`() {
        val salah = collections.first { it.id == "after_salah" }.items
        assertEquals(100, listOf("salah_subhanallah", "salah_alhamdulillah", "salah_allahu_akbar", "salah_completing_hundred")
            .sumOf { id -> salah.first { it.id == id }.count })
        val sleep = collections.first { it.id == "before_sleep" }.items
        assertEquals(100, listOf("sleep_subhanallah", "sleep_alhamdulillah", "sleep_allahu_akbar")
            .sumOf { id -> sleep.first { it.id == id }.count })
    }
}
