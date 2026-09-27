package org.adhkaar.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.adhkaar.app.ui.quran.CurlState
import org.adhkaar.app.ui.quran.rememberCurlState
import kotlinx.coroutines.runBlocking
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.ui.quran.CurlPager
import org.adhkaar.app.ui.quran.PageColors
import org.adhkaar.app.ui.quran.PageLayouts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mushaf pages, for checking against the print (saved in build/mushaf). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class MushafScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: CurlState

    @Test
    fun pages() {
        compose.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val mushaf = remember { Mushaf.get(context) }
            val layouts = remember { PageLayouts(context, density) }
            state = rememberCurlState(0)
            CurlPager(state, mushaf, layouts, PageColors.sepia, null, {}, {}, Modifier.fillMaxSize().padding(top = 40.dp, bottom = 30.dp))
        }
        for (p in listOf(1, 2, 10, 50, 590, 604)) {
            compose.runOnIdle { state.snapTo(p - 1) }
            repeat(6) {
                Thread.sleep(250)
                compose.waitForIdle()
            }
            compose.onRoot().captureRoboImage("build/mushaf/page-$p.png")
        }
    }

    /** Half-way through a drag each way: forward (finger to the right) and back (finger to the left). */
    @Test
    fun drags() {
        compose.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val mushaf = remember { Mushaf.get(context) }
            val layouts = remember { PageLayouts(context, density) }
            state = rememberCurlState(9)
            CurlPager(state, mushaf, layouts, PageColors.sepia, null, {}, {}, Modifier.fillMaxSize().padding(top = 40.dp, bottom = 30.dp))
        }
        repeat(6) { Thread.sleep(250); compose.waitForIdle() }
        for ((name, from, by) in listOf(Triple("forward", 0.1f, 0.45f), Triple("back15", 0.95f, -0.15f), Triple("back45", 0.95f, -0.45f), Triple("back75", 0.95f, -0.75f))) {
            compose.onRoot().performTouchInput {
                down(androidx.compose.ui.geometry.Offset(width * from, height * 0.8f))
                repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(width * by / 10, -height * 0.01f)) }
            }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/mushaf/drag-$name.png")
            compose.onRoot().performTouchInput { cancel() }
            compose.waitForIdle()
        }
    }
}
