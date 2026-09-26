package org.adhkaar.app.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeStateTest {
    @Test
    fun `a fresh launch acts on its intent`() {
        assertTrue(ResumeState.actsOnLaunchIntent(Intent.FLAG_ACTIVITY_NEW_TASK, restored = false))
    }

    @Test
    fun `a restored activity ignores the intent it was first started with`() {
        assertFalse(ResumeState.actsOnLaunchIntent(Intent.FLAG_ACTIVITY_NEW_TASK, restored = true))
    }

    @Test
    fun `a relaunch from recents ignores the intent that started the task`() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        assertFalse(ResumeState.actsOnLaunchIntent(flags, restored = false))
    }

    @Test
    fun `a session reopens on the page it was left on`() {
        assertEquals(4, ResumeState.startPage(saved = 4, pageCount = 10, firstIncomplete = 2))
        assertEquals(0, ResumeState.startPage(saved = 0, pageCount = 10, firstIncomplete = 2))
    }

    @Test
    fun `without a usable saved page it opens on the first dhikr not yet said`() {
        assertEquals(2, ResumeState.startPage(saved = -1, pageCount = 10, firstIncomplete = 2))
        // The session's adhkaar changed (a dua of the user's removed) and the page is gone.
        assertEquals(2, ResumeState.startPage(saved = 10, pageCount = 10, firstIncomplete = 2))
        assertEquals(0, ResumeState.startPage(saved = -1, pageCount = 10, firstIncomplete = null))
    }
}
