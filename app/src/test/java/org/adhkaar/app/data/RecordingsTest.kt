package org.adhkaar.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.adhkaar.app.session.Recordings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecordingsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val id = "user_test"

    @Test
    fun `a new take replaces the saved one only on save`() {
        Recordings.saved(context, id).writeText("old")
        Recordings.pending(context, id).writeText("new")
        assertEquals("new", Recordings.current(context, id)!!.readText())
        Recordings.commit(context, id)
        assertEquals("new", Recordings.saved(context, id).readText())
        assertFalse(Recordings.pending(context, id).exists())
    }

    @Test
    fun `leaving without saving keeps the old recording`() {
        Recordings.saved(context, id).writeText("old")
        Recordings.pending(context, id).writeText("new")
        Recordings.discardPending(context, id)
        assertEquals("old", Recordings.current(context, id)!!.readText())
    }

    @Test
    fun `deleting removes both`() {
        Recordings.saved(context, id).writeText("old")
        Recordings.pending(context, id).writeText("new")
        Recordings.delete(context, id)
        assertNull(Recordings.current(context, id))
        assertTrue(!Recordings.saved(context, id).exists())
    }
}
