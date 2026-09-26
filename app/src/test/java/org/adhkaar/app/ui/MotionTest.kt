package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.adhkaar.app.data.SettingsStore
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.CounterOrb
import org.adhkaar.app.ui.library.LibraryScreen
import org.adhkaar.app.ui.settings.SettingsScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Motion, frame by frame: plays an interaction on a fixed clock and saves every other frame
 * (30 per second) to build/motion/<name>/. tools/motion_gif.py turns them into the board's GIFs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class MotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val frames = mutableMapOf<String, Int>()

    /** Plays [action], then saves a frame every 33ms for [millis]. [screen] also captures dialogs. */
    private fun record(name: String, millis: Long, screen: Boolean = false, action: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        action()
        var t = 0L
        while (t <= millis) {
            val path = "build/motion/$name/%03d.png".format(frames.merge(name, 1, Int::plus)!! - 1)
            if (screen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
            compose.mainClock.advanceTimeBy(33)
            t += 33
        }
    }

    private fun onboarded() {
        val context: Context = ApplicationProvider.getApplicationContext()
        SettingsStore.get(context).update { it.copy(onboarded = true, latitude = 6.5244, longitude = 3.3792) }
    }

    @Test
    fun tabSwitch() {
        onboarded()
        compose.setContent { AppRoot() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        // Today → Insights (two tabs to the right), then back to Adhkaar (one to the left).
        record("tab-right", 1400) { compose.onNodeWithContentDescription("Insights").performClick() }
        compose.mainClock.advanceTimeBy(1_500)
        record("tab-left", 1400) { compose.onNodeWithContentDescription("Adhkaar").performClick() }
    }

    /** A finger presses a card, slides across it, and lets go: the light follows, then fades. */
    @Test
    fun press() {
        onboarded()
        compose.setContent { Screen { LibraryScreen(onOpenCollection = {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        val card = compose.onNodeWithText("After salah")
        record("press", 300) { card.performTouchInput { down(Offset(width * 0.3f, height * 0.5f)) } }
        record("press", 400) { card.performTouchInput { moveBy(Offset(width * 0.5f, 0f)) } }
        record("press", 700) { card.performTouchInput { up() } }
    }

    /** Three taps on a count of three: a band of light each time, a gold one on the last. */
    @Test
    fun counter() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            Screen {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CounterOrb(count, 3, Modifier.testTag("orb")) { count++ }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        repeat(3) { i -> record("counter", if (i < 2) 500 else 1_400) { compose.onNodeWithTag("orb").performClick() } }
    }

    /** The language picker opening: it rises, and a light blooms across the glass. */
    @Test
    fun dialog() {
        onboarded()
        compose.setContent { Screen { SettingsScreen(onOpenSetup = {}, onOpenPrayerTimes = {}, onOpenGuide = {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        record("dialog", 1_100, screen = true) {
            compose.onNodeWithTag("language").performScrollTo().onChildren().onFirst().performClick()
        }
    }

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        AdhkaarTheme(Auras.evening) { AuraBackground(Modifier.fillMaxSize()) { content() } }
    }
}
