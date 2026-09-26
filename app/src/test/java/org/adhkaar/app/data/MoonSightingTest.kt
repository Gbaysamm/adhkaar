package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MoonSightingTest {
    private val file = MoonSighting.parse(
        """
        {"authority": "NSCIA", "months": [
          {"hijri": "1448-03", "start": "2026-08-14"},
          {"hijri": "1448-04", "start": "2026-09-13", "source": "x.com"}
        ]}
        """,
    )

    @Test
    fun countsFromTheAnnouncedFirstDay() {
        assertEquals(HijriDay(1, 4, 1448), MoonSighting.hijri(file, LocalDate.of(2026, 9, 13)))
        // The committee's own post: Friday 25 September 2026 is 13 Rabi' al-Thani.
        assertEquals(HijriDay(13, 4, 1448), MoonSighting.hijri(file, LocalDate.of(2026, 9, 25)))
        // The previous month ran 30 days, from 14 August.
        assertEquals(HijriDay(30, 3, 1448), MoonSighting.hijri(file, LocalDate.of(2026, 9, 12)))
    }

    @Test
    fun notCoveredFallsBackToCalculation() {
        assertNull(MoonSighting.hijri(file, LocalDate.of(2026, 8, 13)))
        // Day 30 of the last month is still covered; after that, the next announcement isn't in yet.
        assertEquals(HijriDay(30, 4, 1448), MoonSighting.hijri(file, LocalDate.of(2026, 10, 12)))
        assertNull(MoonSighting.hijri(file, LocalDate.of(2026, 10, 13)))
    }

    /** The list shipped with the app parses, and each month runs 29 or 30 days. */
    @Test
    fun bundledListIsConsistent() {
        val bundled = MoonSighting.parse(File("src/main/assets/calendar/ng.json").readText())
        val starts = bundled.months.map { LocalDate.parse(it.start) }
        starts.zipWithNext().forEachIndexed { i, (a, b) ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(a, b)
            assert(days == 29L || days == 30L) { "${bundled.months[i].hijri} has $days days" }
        }
        bundled.months.map { it.hijri }.zipWithNext().forEach { (a, b) ->
            val (y1, m1) = a.split("-").map(String::toInt)
            val (y2, m2) = b.split("-").map(String::toInt)
            assertEquals("months follow each other", if (m1 == 12) y1 + 1 to 1 else y1 to m1 + 1, y2 to m2)
        }
    }
}
