package org.adhkaar.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDay(val day: Int, val month: Int, val year: Int)

enum class EventKind { JUMUAH, WHITE_DAYS, RAMADAN, LAST_TEN_NIGHTS, DHUL_HIJJAH_TEN, ARAFAH, EID_AL_FITR, EID_AL_ADHA, TASUA, ASHURA }

/** Title and body text live in string resources, see IslamicCalendarText.kt; [reference] is a source citation and is not translated. */
data class DayEvent(val kind: EventKind, val reference: String)

/**
 * Hijri dates and the days that carry special worship. Pure Kotlin, unit tested.
 * The calculated (Umm al-Qura) date can differ from local moon sighting, so every date
 * goes through the user's offset.
 */
object IslamicCalendar {
    fun hijri(date: LocalDate, offsetDays: Int = 0): HijriDay {
        val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
        return HijriDay(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR))
    }

    /** What is special about [date]. */
    fun eventsFor(date: LocalDate, h: HijriDay): List<DayEvent> = buildList {
        if (date.dayOfWeek == DayOfWeek.FRIDAY) {
            add(DayEvent(EventKind.JUMUAH, "Ṣaḥīḥ al-Jāmiʿ 6470; Abu Dawud 1047"))
        }
        when (h.month) {
            9 -> if (h.day >= 21) {
                add(DayEvent(EventKind.LAST_TEN_NIGHTS, "at-Tirmidhi 3513"))
            } else {
                add(DayEvent(EventKind.RAMADAN, "al-Baqarah 2:185"))
            }
            10 -> if (h.day == 1) add(DayEvent(EventKind.EID_AL_FITR, "al-Bukhari 1509"))
            12 -> when (h.day) {
                in 1..8 -> add(DayEvent(EventKind.DHUL_HIJJAH_TEN, "Ahmad 5446"))
                9 -> add(DayEvent(EventKind.ARAFAH, "Muslim 1162"))
                10 -> add(DayEvent(EventKind.EID_AL_ADHA, "al-Baqarah 2:203"))
            }
            1 -> when (h.day) {
                9 -> add(DayEvent(EventKind.TASUA, "Muslim 1134"))
                10 -> add(DayEvent(EventKind.ASHURA, "Muslim 1162"))
            }
        }
        // White days: sunnah fasting on the 13th–15th, except in Ramadan and on the day of tashriq (13 Dhu al-Hijjah).
        if (h.day in 13..15 && h.month != 9 && !(h.month == 12 && h.day == 13)) {
            add(DayEvent(EventKind.WHITE_DAYS, "at-Tirmidhi 761"))
        }
    }

    /** Events worth a reminder the evening before (fasting needs the intention and suhur). */
    fun eveReminder(tomorrow: LocalDate, h: HijriDay): DayEvent? =
        eventsFor(tomorrow, h).firstOrNull {
            it.kind in setOf(EventKind.ARAFAH, EventKind.TASUA, EventKind.ASHURA, EventKind.EID_AL_FITR, EventKind.EID_AL_ADHA) ||
                (it.kind == EventKind.WHITE_DAYS && h.day == 13)
        }
}
