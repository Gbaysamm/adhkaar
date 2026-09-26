package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.data.UserDuaStore
import org.adhkaar.app.ui.session.SessionScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The screens of the website's simulator (site/try.html). Each is reached from a clean app by a
 * list of taps, then captured at phone size with where its buttons are. Long pages are captured a
 * screen at a time (name-1, name-2, ...). site/sim/build.py then links the buttons to the screens.
 *
 * Resumable: a screen already captured is skipped. Delete site/sim/shots to capture everything again.
 * Run: ./gradlew testDebugUnitTest --tests "*SiteCrawlTest*" -Proborazzi.test.record=true
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SiteCrawlTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("../site/sim/shots").apply { mkdirs() }

    private var run by mutableIntStateOf(0)
    private var start: @Composable () -> Unit = {}
    private var setup: () -> Unit = {}

    // ---- a clean phone before every screen ----
    private fun reset() {
        listOf("session", "settings", "user_duas").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        listOf(SessionState::class.java, SettingsStore::class.java, UserDuaStore::class.java).forEach { c ->
            c.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        }
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        SettingsStore.get(context).update {
            it.copy(onboarded = true, strictness = Strictness.GENTLE, showTransliteration = true, showTranslation = true)
        }
        val state = SessionState.get(context)
        val today = LocalDate.now()
        (1L..22L).forEach { ago ->
            state.complete(SessionType.MORNING, today.minusDays(ago))
            if (ago % 5 != 3L) state.complete(SessionType.EVENING, today.minusDays(ago))
        }
        setup()
    }

    /** The phone's local time, set by moving its time zone (the app reads the clock directly). */
    private fun at(hour: Int, minute: Int) {
        val now = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        var diff = (hour * 60 + minute) - (now.hour * 60 + now.minute)
        if (diff > 14 * 60) diff -= 24 * 60
        if (diff < -12 * 60) diff += 24 * 60
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneOffset.ofTotalSeconds(diff * 60)))
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    private fun label(n: SemanticsNode): String {
        val c = n.config
        return c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: c.getOrNull(SemanticsProperties.TestTag)
            ?: ""
    }

    /** What can be tapped now, on the topmost window (a dialog covers what's behind it). */
    private fun tappable(): List<Pair<SemanticsNode, String>> {
        val top = compose.onAllNodes(isRoot()).fetchSemanticsNodes().last()
        val found = mutableListOf<Pair<SemanticsNode, String>>()
        fun walk(n: SemanticsNode) {
            if (n.config.contains(SemanticsActions.OnClick)) {
                val b = n.boundsInWindow
                if (b.width > 1 && b.height > 1 && b.bottom > 0 && b.top < top.size.height) found += n to label(n)
            }
            n.children.forEach(::walk)
        }
        walk(top)
        return found
    }

    /** A step: "↓" scrolls; "Label" taps the first thing whose label starts with it; "Label#2" the second. */
    private fun perform(step: String) {
        if (step == "↓") {
            compose.onRoot(useUnmergedTree = true).performTouchInput { swipeUp(startY = height * 0.72f, endY = height * 0.22f, durationMillis = 1_500) }
            settle()
            return
        }
        android.os.SystemClock.sleep(60_000) // past the reading pace, so counting taps count
        val (text, nth) = step.split("#").let { it[0] to (it.getOrNull(1)?.toInt() ?: 1) }
        val matches = tappable().filter { it.second.replace(Regex("[⁦-⁩]"), "").startsWith(text) }
        val node = matches.getOrNull(nth - 1)?.first ?: error("No \"$text\" to tap. Visible: ${tappable().map { it.second.take(30) }}")
        compose.onNode(SemanticsMatcher("id ${node.id}") { it.id == node.id }).performClick()
        settle()
    }

    private fun write(name: String) {
        val file = File(out, "$name.png")
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        if (roots.size > 1) captureScreenRoboImage(file.path) else compose.onRoot().captureRoboImage(file.path)
        val hot = tappable().joinToString(",\n") { (n, l) ->
            val b = n.boundsInWindow
            "{\"l\":${q(l)},\"x\":${b.left.toInt()},\"y\":${b.top.toInt()},\"w\":${b.width.toInt()},\"h\":${b.height.toInt()}}"
        }
        File(out, "$name.json").writeText("[\n$hot\n]\n")
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /** Captures [name] after [steps] from a clean start; with [frames] > 1, also each screen further down. */
    private fun shot(name: String, steps: List<String> = emptyList(), frames: Int = 1) {
        val first = if (frames > 1) "$name-1" else name
        if (File(out, "$first.png").exists()) return
        val attempt = runCatching {
            reset()
            run++
            settle()
            steps.forEach(::perform)
            write(first)
            for (i in 2..frames) {
                perform("↓")
                val file = "$name-$i"
                write(file)
                if (File(out, "$file.png").readBytes().contentEquals(File(out, "$name-${i - 1}.png").readBytes())) {
                    File(out, "$file.png").delete(); File(out, "$file.json").delete(); break
                }
            }
        }
        // One screen that can't be reached shouldn't stop the rest; its reason is kept to fix it.
        attempt.exceptionOrNull()?.let { File(out, "$name.error.txt").writeText(it.toString()) }
    }

    private fun begin(content: @Composable () -> Unit, prepare: () -> Unit) {
        start = content
        setup = prepare
        compose.setContent { key(run) { start() } }
    }

    private fun appAt(hour: Int, minute: Int) = begin({ AppRoot() }) {
        at(hour, minute)
        // What the 6:00 alarm leaves: the morning session, waiting.
        SessionState.get(context).start(SessionType.MORNING, LocalDate.now(), System.currentTimeMillis() - 5 * 60_000L, enforced = true)
    }

    @Test
    fun today() {
        appAt(6, 5)
        shot("today", frames = 7)
        shot("streak", listOf("22"))
        shot("day-reminder", listOf("↓", "REMINDER FOR TODAY"))
        shot("share-card", listOf("↓", "Share"))
        shot("waking", listOf("FOR THIS MOMENT"))
    }

    @Test
    fun adhkaar() {
        appAt(6, 5)
        shot("adhkaar", listOf("Adhkaar"), frames = 3)
        shot("shelf-morning", listOf("Adhkaar", "Morning"), frames = 5)
        shot("shelf-evening", listOf("Adhkaar", "Evening"), frames = 5)
        shot("shelf-morning-open", listOf("Adhkaar", "Morning", "1 Ayat al-Kursi"))
        shot("shelf-morning-fav", listOf("Adhkaar", "Morning", "Add to favourites"))
        shot("after-salah", listOf("Adhkaar", "After salah"))
        shot("before-sleep", listOf("Adhkaar", "Before sleep"))
        shot("on-waking", listOf("Adhkaar", "On waking"))
        shot("everyday", listOf("Adhkaar", "Everyday duas"), frames = 4)
        shot("favourites", listOf("Adhkaar", "↓", "Favourites"))
        shot("my-duas", listOf("Adhkaar", "↓", "My duas"))
        shot("dua-editor", listOf("Adhkaar", "↓", "My duas", "Write a dua"), frames = 2)
    }

    @Test
    fun insights() {
        appAt(6, 5)
        shot("insights", listOf("Insights"), frames = 5)
    }

    @Test
    fun settings() {
        appAt(6, 5)
        shot("settings", listOf("Settings"), frames = 10)
        shot("settings-full", listOf("Settings", "Full screen"))
        shot("settings-lockdown", listOf("Settings", "Lockdown"))
        shot("guide", listOf("Settings", "Guide"), frames = 8)
        shot("test-kit", listOf("Settings", "Test kit"), frames = 5)
        shot("contact", listOf("Settings", "Contact us"), frames = 2)
        shot("salah-reminders", listOf("Settings", "↓", "↓", "Salah reminders"), frames = 2)
        shot("salah-edit", listOf("Settings", "↓", "↓", "Salah reminders", "6:35"))
    }

    /** A session the alarm started, in Full screen. */
    @Test
    fun session() {
        begin({ AdhkaarTheme(Auras.dawn) { SessionScreen(SessionType.MORNING, onFinish = {}) } }) {
            at(6, 12)
            SettingsStore.get(context).update { it.copy(strictness = Strictness.FULL_SCREEN) }
            SessionState.get(context).start(SessionType.MORNING, LocalDate.now(), System.currentTimeMillis(), enforced = true)
        }
        shot("s0")
        shot("s1", listOf("0 of 1"))
        shot("s2", listOf("0 of 1", "0 of 3"))
        shot("s3", listOf("0 of 1", "0 of 3", "1 of 3"))
        shot("s4", listOf("0 of 1", "0 of 3", "1 of 3", "2 of 3"))
        shot("s-fav", listOf("Add to favourites"))
        shot("s-read", listOf("Reading options"))
        shot("s-share", listOf("Share"))
        shot("b1", listOf("Take a break"))
        shot("b2", listOf("Take a break", "20"))
        shot("b3", listOf("Take a break", "20", "Pause for 20"))
    }
}
