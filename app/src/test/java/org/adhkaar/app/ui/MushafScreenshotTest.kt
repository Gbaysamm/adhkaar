package org.adhkaar.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.ui.quran.FlipPager
import org.adhkaar.app.ui.quran.PageColors
import org.adhkaar.app.ui.quran.PageLayouts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mushaf pages and the page turn, for checking against the print (saved in build/mushaf). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class MushafScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: PagerState

    private fun show(page: Int) {
        compose.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val mushaf = remember { Mushaf.get(context) }
            val layouts = remember { PageLayouts(context, density) }
            state = rememberPagerState(initialPage = page - 1) { Quran.PAGES }
            FlipPager(state, mushaf, layouts, PageColors.sepia, null, {}, {}, Modifier.fillMaxSize().padding(top = 40.dp, bottom = 30.dp))
        }
        compose.waitUntil(10_000) { true }
        Thread.sleep(300)
        compose.waitForIdle()
    }

    @Test
    fun pages() {
        show(1)
        compose.onRoot().captureRoboImage("build/mushaf/page-1.png")
        for (p in listOf(2, 10, 50, 590, 604)) {
            compose.runOnIdle { runBlocking { state.scrollToPage(p - 1) } }
            Thread.sleep(300)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/mushaf/page-$p.png")
        }
    }

    @Test
    fun turn() {
        show(10)
        for ((page, f) in listOf(9 to 0.25f, 9 to 0.5f, 10 to -0.25f)) {
            compose.runOnIdle { runBlocking { state.scrollToPage(page, f) } }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/mushaf/turn-$page-${(f * 100).toInt()}.png")
        }
    }
}
