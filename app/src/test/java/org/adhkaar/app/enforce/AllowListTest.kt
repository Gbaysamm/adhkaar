package org.adhkaar.app.enforce

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowListTest {
    private val own = "org.adhkaar.app"
    private val phoneApps = setOf("com.transsion.dialer", "com.transsion.messaging")

    @Test
    fun `own app, system UI and calls are allowed`() {
        assertTrue(AllowList.isAllowed(own, own, phoneApps))
        assertTrue(AllowList.isAllowed("com.android.systemui", own, phoneApps))
        assertTrue(AllowList.isAllowed("com.google.android.dialer", own, phoneApps))
        assertTrue(AllowList.isAllowed("com.samsung.android.incallui", own, phoneApps))
    }

    @Test
    fun `the phone's default dialer and SMS apps are allowed`() {
        assertTrue(AllowList.isAllowed("com.transsion.dialer", own, phoneApps))
        assertTrue(AllowList.isAllowed("com.transsion.messaging", own, phoneApps))
    }

    @Test
    fun `unknown foreground app is never blocked`() {
        assertTrue(AllowList.isAllowed(null, own, phoneApps))
    }

    @Test
    fun `other apps and the launcher are blocked`() {
        assertFalse(AllowList.isAllowed("com.instagram.android", own, phoneApps))
        assertFalse(AllowList.isAllowed("com.google.android.apps.nexuslauncher", own, phoneApps))
        assertFalse(AllowList.isAllowed("com.android.settings", own, phoneApps))
    }
}
