package org.adhkaar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {
    private val latest = AppUpdate.Latest(8, "0.1.7", "https://example.org/Adhkaar-0.1.7-test.apk")
    private val now = 1_000_000_000L

    @Test fun `a newer build is shown`() =
        assertEquals(latest, AppUpdate.toShow(latest, installedCode = 7, snoozedCode = 0, snoozedAt = 0, now = now))

    @Test fun `the same or an older build is not`() {
        assertNull(AppUpdate.toShow(latest, installedCode = 8, snoozedCode = 0, snoozedAt = 0, now = now))
        assertNull(AppUpdate.toShow(latest, installedCode = 9, snoozedCode = 0, snoozedAt = 0, now = now))
    }

    @Test fun `later waits a day for that version only`() {
        assertNull(AppUpdate.toShow(latest, 7, snoozedCode = 8, snoozedAt = now - 1000, now = now))
        assertEquals(latest, AppUpdate.toShow(latest, 7, snoozedCode = 8, snoozedAt = now - AppUpdate.SNOOZE_MS, now = now))
        val newer = latest.copy(versionCode = 9)
        assertEquals(newer, AppUpdate.toShow(newer, 7, snoozedCode = 8, snoozedAt = now - 1000, now = now))
    }

    @Test fun `only https links are followed`() =
        assertNull(AppUpdate.toShow(latest.copy(url = "http://example.org/a.apk"), 7, 0, 0, now))

    @Test fun `the file parses with fields it doesn't know`() =
        assertEquals(latest, AppUpdate.parse("""{"versionCode":8,"versionName":"0.1.7","url":"https://example.org/Adhkaar-0.1.7-test.apk","extra":1}"""))
}
