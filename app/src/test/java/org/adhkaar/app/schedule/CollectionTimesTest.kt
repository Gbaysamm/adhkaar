package org.adhkaar.app.schedule

import org.adhkaar.app.data.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CollectionTimesTest {
    private val zone = ZoneId.of("Africa/Lagos")
    private val day = listOf(
        PrayerTime("Fajr", LocalTime.of(5, 26)),
        PrayerTime("Sunrise", LocalTime.of(6, 35)),
        PrayerTime("Dhuhr", LocalTime.of(12, 39)),
        PrayerTime("Asr", LocalTime.of(15, 50)),
        PrayerTime("Maghrib", LocalTime.of(18, 41)),
        PrayerTime("Isha", LocalTime.of(19, 46)),
    )
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, zone)

    @Test
    fun `next reminder follows the next prayer, never sunrise`() {
        assertEquals(at(25, 12, 59), CollectionTimes.nextAfterSalah(at(25, 5, 50), 20, day))
    }

    @Test
    fun `after Isha it rolls to tomorrow's Fajr`() {
        assertEquals(at(26, 5, 46), CollectionTimes.nextAfterSalah(at(25, 21, 0), 20, day))
    }

    @Test
    fun `the earliest time wins, even when the list is out of order`() {
        val shuffled = listOf(PrayerTime("Asr", LocalTime.of(16, 20)), PrayerTime("Dhuhr", LocalTime.of(13, 20)))
        assertEquals(at(25, 13, 40), CollectionTimes.nextAfterSalah(at(25, 9, 0), 20, shuffled))
    }

    @Test
    fun `salah reminders ring at the prayer times`() {
        val reminders = testSettings().salahReminderMinutes
        assertEquals(Prayer.DHUHR to at(25, 13, 0), CollectionTimes.nextSalahReminder(at(25, 9, 0), reminders))
        // Exactly at a reminder's time it has fired; the next one follows.
        assertEquals(Prayer.ASR to at(25, 15, 55), CollectionTimes.nextSalahReminder(at(25, 13, 0), reminders))
    }

    @Test
    fun `a salah reminder follows the user's time and can be off`() {
        val s = testSettings(prayerMinutes = mapOf(Prayer.ASR to 16 * 60 + 10), remindersOff = setOf(Prayer.DHUHR))
        assertEquals(Prayer.ASR to at(25, 16, 10), CollectionTimes.nextSalahReminder(at(25, 9, 0), s.salahReminderMinutes))
    }

    @Test
    fun `after Isha the next salah reminder is tomorrow's Fajr`() {
        assertEquals(Prayer.FAJR to at(26, 5, 30), CollectionTimes.nextSalahReminder(at(25, 21, 0), testSettings().salahReminderMinutes))
    }

    @Test
    fun `sunrise has no reminder, and all off means none scheduled`() {
        assertEquals(Prayer.five.toSet(), testSettings().salahReminderMinutes.keys)
        assertNull(CollectionTimes.nextSalahReminder(at(25, 9, 0), testSettings(remindersOff = Prayer.five.toSet()).salahReminderMinutes))
    }

    @Test
    fun `daily time rolls over at midnight`() {
        assertEquals(at(25, 22, 0), CollectionTimes.nextDaily(at(25, 21, 0), 22 * 60))
        assertEquals(at(26, 22, 0), CollectionTimes.nextDaily(at(25, 22, 30), 22 * 60))
    }
}
