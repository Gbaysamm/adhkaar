package org.adhkaar.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DayProgressTest {
    private val fajr = LocalTime.of(5, 26)
    private val maghrib = LocalTime.of(18, 35)

    @Test
    fun fromFajrToMaghrib() {
        assertEquals(0f, dayProgress(fajr, fajr, maghrib), 0.001f)
        assertEquals(1f, dayProgress(maghrib, fajr, maghrib), 0.001f)
        assertEquals(0.5f, dayProgress(LocalTime.of(12, 0, 30), fajr, maghrib), 0.01f)
    }

    @Test
    fun theHoursAfterMidnightAreStillNight() {
        assertEquals(1f, dayProgress(LocalTime.of(1, 10), fajr, maghrib), 0f)
        assertEquals(1f, dayProgress(LocalTime.of(22, 0), fajr, maghrib), 0f)
    }
}
