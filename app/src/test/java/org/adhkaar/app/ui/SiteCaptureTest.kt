package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.test.isRoot
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.session.PopupCard
import org.adhkaar.app.session.ReminderPopup
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.session.SessionScreen
import org.adhkaar.app.ui.settings.ContactScreen
import org.adhkaar.app.ui.settings.GuideScreen
import org.adhkaar.app.ui.settings.PrayerTimesScreen
import org.adhkaar.app.ui.settings.TestKitScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The screens of the website's simulator (site/try.html), rendered from the app itself, each with
 * a JSON list of where its tappable things are, so the page can make the real buttons work.
 * Run: ./gradlew testDebugUnitTest --tests "*SiteCaptureTest*" -Proborazzi.test.record=true
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SiteCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("../site/sim/raw").apply { mkdirs() }

    /**
     * Sets the phone's local time to [hour]:[minute] by moving its time zone, since the app reads
     * the clock directly: renders then show the morning as it looks at that moment.
     */
    private fun at(hour: Int, minute: Int) {
        val now = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        var diff = (hour * 60 + minute) - (now.hour * 60 + now.minute)
        if (diff > 14 * 60) diff -= 24 * 60
        if (diff < -12 * 60) diff += 24 * 60
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneOffset.ofTotalSeconds(diff * 60)))
    }

    @Before
    fun setUp() {
        at(6, 5)
        // A phone set up as the app asks, so no "Finish phone setup" card in the renders.
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        SettingsStore.get(context).update {
            it.copy(onboarded = true, strictness = Strictness.FULL_SCREEN, showTransliteration = true, showTranslation = true)
        }
        val state = SessionState.get(context)
        state.clearPending()
        val today = LocalDate.now()
        (1L..22L).forEach { ago ->
            state.complete(SessionType.MORNING, today.minusDays(ago))
            if (ago % 5 != 3L) state.complete(SessionType.EVENING, today.minusDays(ago))
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    /** Saves the screen and the bounds (in image pixels) of everything on it that can be tapped. */
    private fun capture(name: String) {
        settle()
        // A dialog is a window of its own: then capture the whole screen and read every window.
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        if (roots.size > 1) captureScreenRoboImage("${out.path}/$name.png") else compose.onRoot().captureRoboImage("${out.path}/$name.png")
        val root = roots.first()
        val rows = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            val c = n.config
            if (c.contains(SemanticsActions.OnClick)) {
                val label = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                    ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                    ?: c.getOrNull(SemanticsProperties.TestTag)
                    ?: ""
                val b = n.boundsInWindow
                rows += "{\"label\":${quote(label)},\"x\":${b.left.toInt()},\"y\":${b.top.toInt()},\"w\":${b.width.toInt()},\"h\":${b.height.toInt()}}"
            }
            n.children.forEach(::walk)
        }
        roots.forEach(::walk)
        val size = root.size
        File(out, "$name.json").writeText("{\"w\":${size.width},\"h\":${size.height},\"hot\":[\n${rows.joinToString(",\n")}\n]}\n")
    }

    /**
     * A page that scrolls, as the phone shows it: the first screen, then again after each scroll
     * of about half a screen, until the page stops moving. Frames are [name]-1, [name]-2, ...
     */
    private fun captureScrolling(name: String, maxFrames: Int = 8) {
        capture("$name-1")
        for (i in 2..maxFrames) {
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.72f, endY = height * 0.22f, durationMillis = 1_500) }
            capture("$name-$i")
            val now = File(out, "$name-$i.png").readBytes()
            val before = File(out, "$name-${i - 1}.png").readBytes()
            if (now.contentEquals(before)) {
                File(out, "$name-$i.png").delete(); File(out, "$name-$i.json").delete()
                break
            }
        }
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /** Taps the centre of the last tappable thing whose label starts with [label], as the website will. */
    private fun tap(label: String) {
        settle()
        val node = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { n ->
                val c = n.config
                val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                    ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
                text.startsWith(label) && n.boundsInRoot.width > 0
            }
            .last()
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher("id ${node.id}") { it.id == node.id }).performClick()
    }

    private fun tab(label: String) = tap(label)

    @Composable
    private fun Screen(type: SessionType = SessionType.MORNING, content: @Composable () -> Unit) {
        AdhkaarTheme(Auras.of(type)) { AuraBackground(Modifier.fillMaxSize()) { content() } }
    }

    /**
     * Gentle here: the one permission a test phone can't grant is only needed for Full screen,
     * and its "Finish phone setup" card would otherwise show.
     */
    @Test
    fun today() {
        SettingsStore.get(context).update { it.copy(strictness = Strictness.GENTLE) }
        // What the 6:00 alarm leaves behind: the morning session, waiting.
        SessionState.get(context).start(SessionType.MORNING, LocalDate.now(), System.currentTimeMillis() - 5 * 60_000L, enforced = true)
        compose.setContent { AppRoot() }
        captureScrolling("today")
    }

    @Test
    fun adhkaar() { compose.setContent { AppRoot() }; settle(); tab("Adhkaar"); captureScrolling("adhkaar") }

    @Test
    fun morningShelf() {
        compose.setContent { AppRoot() }; settle(); tab("Adhkaar"); settle()
        tap("Morning")
        captureScrolling("shelf-morning")
    }

    @Test
    fun insights() { compose.setContent { AppRoot() }; settle(); tab("Insights"); captureScrolling("insights") }

    @Test
    fun settings() { compose.setContent { AppRoot() }; settle(); tab("Settings"); captureScrolling("settings", maxFrames = 12) }

    @Test
    fun salahReminders() { compose.setContent { Screen { PrayerTimesScreen(onBack = {}) } }; captureScrolling("salah-reminders") }

    @Test
    fun guide() { compose.setContent { Screen { GuideScreen(onBack = {}) } }; captureScrolling("guide", maxFrames = 10) }

    @Test
    fun testKit() { compose.setContent { Screen { TestKitScreen(onBack = {}, onOpenSetup = {}) } }; captureScrolling("test-kit") }

    @Test
    fun contact() { compose.setContent { Screen { ContactScreen(onBack = {}) } }; captureScrolling("contact") }

    /** A session the alarm started: its first adhkaar, counted one tap at a time. */
    @Test
    fun session() {
        at(6, 12)
        SessionState.get(context).start(SessionType.MORNING, LocalDate.now(), System.currentTimeMillis(), enforced = true)
        compose.setContent { AdhkaarTheme(Auras.dawn) { SessionScreen(SessionType.MORNING, onFinish = {}) } }
        capture("session-0")
        // The counter: Ayat al-Kursi (once), then al-Ikhlas three times.
        repeat(4) { i ->
            // Past the minimum reading time (the app paces counting to the length of the text).
            android.os.SystemClock.sleep(120_000)
            tap(if (i == 0) "0 of 1" else "${i - 1} of 3")
            capture("session-${i + 1}")
        }
        tap("Take a break")
        capture("break-1")
        tap("20")
        capture("break-2")
        tap("Pause for 20 minutes")
        capture("break-3")
    }

    @Test
    fun complete() {
        compose.setContent {
            AdhkaarTheme(Auras.dawn) {
                org.adhkaar.app.ui.session.CompletionView(SessionType.MORNING, org.adhkaar.app.ui.session.Summary(21, 7, 23), onDone = {})
            }
        }
        capture("complete")
    }

    @Test
    fun lockdown() {
        compose.setContent {
            AdhkaarTheme(Auras.dawn) {
                org.adhkaar.app.enforce.BlockScreen(SessionType.MORNING, remaining = 17, started = true, onReturn = {}, onCall = {})
            }
        }
        capture("lockdown")
    }

    @Test
    fun salahPopup() {
        at(18, 35)
        SessionState.get(context).start(SessionType.EVENING, LocalDate.now(), System.currentTimeMillis() - 90 * 60_000L, enforced = true)
        compose.setContent { Box { AppRoot(); AdhkaarTheme(Auras.dawn) { PopupCard(ReminderPopup.Kind.Salah(Prayer.MAGHRIB), {}, {}) } } }
        capture("popup-salah")
    }

    @Test
    fun afterSalahPopup() {
        at(13, 22)
        compose.setContent { Box { AppRoot(); AdhkaarTheme(Auras.dawn) { PopupCard(ReminderPopup.Kind.Collection("after_salah"), {}, {}) } } }
        capture("popup-after")
    }
}
