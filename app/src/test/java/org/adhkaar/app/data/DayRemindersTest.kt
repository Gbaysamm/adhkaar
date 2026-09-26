package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DayRemindersTest {
    private val assets = File("src/main/assets")
    private fun load(): ReminderLibrary = DayReminders.library(
        special = File(assets, "reminders.json").readText(),
        index = File(assets, "reminders/library.json").readText(),
    ) { File(assets, DayReminders.chunkPath(it)).readText() }
    private val library: ReminderLibrary = load()
    private val special: List<DayReminder> = library.special
    private val everyday: List<DayReminder> = (0 until library.everydayCount).map { library.everyday(it) }

    // A Saturday, paired with an ordinary Hijri day (the 8th of Rabiʿ al-Thani): no events.
    private val plainDay = LocalDate.of(2026, 9, 26)
    private val plainHijri = HijriDay(8, 4, 1448)
    private val friday = LocalDate.of(2026, 9, 25)

    @Test
    fun `special-day content is complete`() {
        assertEquals(special.size, special.map { it.id }.toSet().size)
        special.forEach { r ->
            assertNotNull("${r.id}: only special-day items belong in reminders.json", r.event)
            assertFalse("${r.id}: meaning", r.meaning.isBlank())
            assertFalse("${r.id}: reference", r.reference.isBlank())
            assertTrue("${r.id}: tags", r.tags.isNotEmpty())
            if (r.kind == ReminderKind.VERSE) assertFalse("${r.id}: verse needs its Arabic", r.arabic.isNullOrBlank())
        }
    }

    @Test
    fun `everyday library is complete and credits its sources`() {
        // Years of days without a repeat.
        assertTrue(everyday.size >= 3 * 365)
        assertEquals(everyday.size, everyday.map { it.id }.toSet().size)
        assertTrue("an everyday id repeats a special one", everyday.none { r -> special.any { it.id == r.id } })
        assertTrue(everyday.any { it.kind == ReminderKind.VERSE } && everyday.any { it.kind == ReminderKind.HADITH })
        everyday.forEach { r ->
            assertNull(r.id, r.event)
            assertFalse("${r.id}: meaning", r.meaning.isBlank())
            assertFalse("${r.id}: Arabic", r.arabic.isNullOrBlank())
            when (r.kind) {
                ReminderKind.VERSE -> assertTrue(r.id, r.reference.matches(Regex("""Surah .+ \d+:\d+(–\d+)? · QuranEnc\.com""")))
                // Sahih al-Bukhari and Sahih Muslim only, numbered.
                ReminderKind.HADITH -> assertTrue(r.id, r.reference.matches(Regex("""Sahih (al-Bukhari|Muslim) \d+.* · HadeethEnc\.com""")))
                ReminderKind.FACT -> fail("${r.id}: facts belong to special days")
            }
        }
    }

    @Test
    fun `every asset file carries the licence notices`() {
        val files = File(assets, "reminders").listFiles()!!.filter { it.extension == "json" }
        assertTrue(files.size > 1)
        files.forEach { file ->
            val text = file.readText()
            listOf("Tanzil Project", "Creative Commons Attribution 3.0", "QuranEnc.com", "HadeethEnc.com").forEach {
                assertTrue("${file.name} lacks $it", it in text)
            }
        }
    }

    @Test
    fun `the attribution strings name every source`() {
        val strings = File("src/main/res/values/strings_reminder_sources.xml").readText()
        listOf("tanzil.net", "Creative Commons Attribution 3.0", "QuranEnc.com", "Rowwad Translation Center", "HadeethEnc.com")
            .forEach { assertTrue("strings lack $it", it in strings) }
    }

    @Test
    fun `every event has items and a place in the precedence`() {
        EventKind.entries.forEach { kind ->
            assertTrue("$kind has no items", special.any { it.event == kind })
            assertTrue("$kind has no precedence", kind in DayReminders.precedence)
        }
    }

    @Test
    fun `the same date always gives the same reminder`() {
        val first = DayReminders.forDate(library, plainDay, plainHijri)
        repeat(3) { assertEquals(first, DayReminders.forDate(library, plainDay, plainHijri)) }
        // A fresh library (as after a restart) agrees.
        assertEquals(first, DayReminders.forDate(load(), plainDay, plainHijri))
    }

    @Test
    fun `the everyday rotation shows every item once before repeating`() {
        // The everyday item of each of that many consecutive days. A special day shows its own
        // item instead, but still counts in the rotation, so what it skips comes back next time.
        val start = plainDay.toEpochDay()
        val shown = (0 until library.everydayCount).map {
            library.everyday(Math.floorMod(start + it, library.everydayCount.toLong()).toInt()).id
        }
        assertEquals(library.everydayCount, shown.toSet().size)
        assertEquals(everyday.map { it.id }.toSet(), shown.toSet())
        // Then it starts again. Seven rounds on is the same weekday too, so no Friday gets in the way.
        val later = plainDay.plusDays(7L * library.everydayCount)
        assertEquals(DayReminders.forDate(library, plainDay, plainHijri), DayReminders.forDate(library, later, plainHijri))
    }

    @Test
    fun `verses and hadith take turns`() {
        // The build deals the two kinds out evenly, so neither runs for more than two days.
        assertTrue(everyday.map { it.kind }.windowed(3).none { it.toSet().size == 1 })
    }

    @Test
    fun `a plain day shows an everyday item`() {
        assertNull(DayReminders.forDate(library, plainDay, plainHijri).event)
    }

    @Test
    fun `the library is also the list of every item`() {
        assertEquals(special.size + everyday.size, library.size)
        assertEquals(special + everyday, library.toList())
    }

    @Test
    fun `special days prefer their own items`() {
        assertEquals(EventKind.JUMUAH, DayReminders.forDate(library, friday, HijriDay(12, 4, 1448)).event)
        assertEquals(EventKind.WHITE_DAYS, DayReminders.forDate(library, plainDay, HijriDay(14, 4, 1448)).event)
        assertEquals(EventKind.ARAFAH, DayReminders.forDate(library, plainDay, HijriDay(9, 12, 1448)).event)
        assertEquals(EventKind.RAMADAN, DayReminders.forDate(library, plainDay, HijriDay(3, 9, 1448)).event)
    }

    @Test
    fun `the rarer event wins`() {
        assertEquals(EventKind.ASHURA, DayReminders.forDate(library, friday, HijriDay(10, 1, 1449)).event)
        assertEquals(EventKind.LAST_TEN_NIGHTS, DayReminders.forDate(library, friday, HijriDay(27, 9, 1448)).event)
        // The 13th is both a white day and a Friday.
        assertEquals(EventKind.WHITE_DAYS, DayReminders.forDate(library, friday, HijriDay(13, 4, 1448)).event)
    }

    @Test
    fun `consecutive Fridays walk through the Friday items`() {
        val fridays = (0 until 8).map { friday.plusWeeks(it.toLong()) }
        val shown = fridays.map { DayReminders.forDate(library, it, HijriDay(2, 4, 1448)).id }
        assertEquals(special.filter { it.event == EventKind.JUMUAH }.map { it.id }.toSet(), shown.toSet())
    }

    @Test
    fun `calculated month length is known`() {
        val date = LocalDate.of(2026, 9, 26)
        val h = IslamicCalendar.hijri(date)
        val span = HijriMonths.span(date, h, null, 0)
        assertEquals(date.minusDays(h.day - 1L), span.start)
        assertTrue(span.length in 29..30)
    }

    @Test
    fun `announced month length needs the next announcement`() {
        val file = MoonSighting.parse(
            """{"months":[{"hijri":"1448-03","start":"2026-08-14"},{"hijri":"1448-04","start":"2026-09-13"}]}""",
        )
        val inAwwal = LocalDate.of(2026, 8, 20)
        assertEquals(HijriMonthSpan(LocalDate.of(2026, 8, 14), 30), HijriMonths.span(inAwwal, MoonSighting.hijri(file, inAwwal)!!, file, 0))
        val inThani = LocalDate.of(2026, 9, 26)
        assertEquals(HijriMonthSpan(LocalDate.of(2026, 9, 13), null), HijriMonths.span(inThani, MoonSighting.hijri(file, inThani)!!, file, 0))
    }
}
