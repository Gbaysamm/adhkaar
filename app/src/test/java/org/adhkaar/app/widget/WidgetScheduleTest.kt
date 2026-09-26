package org.adhkaar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetScheduleTest {
    private val zone = ZoneId.of("Africa/Lagos")
    private fun at(hour: Int, minute: Int, day: Int = 26) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone)

    @Test
    fun `half an hour when nothing changes sooner`() {
        assertEquals(at(10, 30), WidgetSchedule.nextTick(at(10, 0), emptyList()))
    }

    @Test
    fun `a window opening sooner comes first`() {
        assertEquals(at(10, 12), WidgetSchedule.nextTick(at(10, 0), listOf(at(16, 5), at(10, 12))))
    }

    @Test
    fun `moments already past or exactly now are ignored`() {
        assertEquals(at(10, 30), WidgetSchedule.nextTick(at(10, 0), listOf(at(9, 45), at(10, 0))))
    }

    @Test
    fun `midnight turns the date`() {
        assertEquals(at(0, 0, day = 27), WidgetSchedule.nextTick(at(23, 50), emptyList()))
    }
}
