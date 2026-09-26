package org.adhkaar.app.widget

import androidx.glance.layout.Alignment
import androidx.glance.text.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Test

class DirTest {
    private val items = listOf("morning", "spacer", "evening")

    @Test
    fun `rows keep their order when app and launcher agree`() {
        assertEquals(items, Dir(rtl = false, hostRtl = false).inOrder(items))
        assertEquals(items, Dir(rtl = true, hostRtl = true).inOrder(items))
    }

    @Test
    fun `rows reverse when the app's direction differs from the launcher's`() {
        assertEquals(items.reversed(), Dir(rtl = true, hostRtl = false).inOrder(items))
        assertEquals(items.reversed(), Dir(rtl = false, hostRtl = true).inOrder(items))
        assertEquals(Alignment.Horizontal.End, Dir(rtl = true, hostRtl = false).start)
    }

    @Test
    fun `text aligns to the app's start side whatever the launcher`() {
        assertEquals(TextAlign.Right, Dir(rtl = true, hostRtl = false).textStart)
        assertEquals(TextAlign.Right, Dir(rtl = true, hostRtl = true).textStart)
        assertEquals(TextAlign.Left, Dir(rtl = false, hostRtl = true).textStart)
    }
}
