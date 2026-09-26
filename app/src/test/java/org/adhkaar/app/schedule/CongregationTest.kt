package org.adhkaar.app.schedule

import org.adhkaar.app.data.Prayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class CongregationTest {
    @Test
    fun `the congregation is twenty minutes after each default time`() {
        assertEquals(
            listOf(
                PrayerTime("Fajr", LocalTime.of(5, 50)), PrayerTime("Dhuhr", LocalTime.of(13, 20)),
                PrayerTime("Asr", LocalTime.of(16, 15)), PrayerTime("Maghrib", LocalTime.of(18, 55)),
                PrayerTime("Isha", LocalTime.of(20, 10)),
            ),
            Congregation.day(testSettings()),
        )
    }

    @Test
    fun `the congregation follows the user's time`() {
        val s = testSettings(prayerMinutes = mapOf(Prayer.DHUHR to 13 * 60 + 30))
        assertEquals(LocalTime.of(13, 50), Congregation.time(s, Prayer.DHUHR))
    }

    @Test
    fun `a reminder turned off doesn't move the congregation`() {
        val s = testSettings(remindersOff = setOf(Prayer.ASR))
        assertEquals(LocalTime.of(16, 15), Congregation.time(s, Prayer.ASR))
    }

    @Test
    fun `sunrise is never a congregation`() {
        assertEquals(listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"), Congregation.day(testSettings()).map { it.name })
    }
}
