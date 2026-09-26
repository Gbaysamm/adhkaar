package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.library.LibraryScreen
import org.adhkaar.app.ui.session.CollectionSessionScreen
import org.adhkaar.app.ui.session.CompletionView
import org.adhkaar.app.ui.session.SessionScreen
import org.adhkaar.app.ui.session.Summary
import org.adhkaar.app.ui.settings.SettingsScreen
import org.adhkaar.app.ui.setup.SetupScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.home.DaySky
import org.adhkaar.app.ui.home.SkyEnd
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the main screens to docs/screenshots so the UI can be reviewed without a device.
 * Run: ./gradlew testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SettingsStore.get(context).update {
            it.copy(
                onboarded = true,
                strictness = Strictness.LOCKDOWN,
                latitude = 6.5244,
                longitude = 3.3792,
                showTransliteration = true,
                showTranslation = true,
            )
        }
        val state = SessionState.get(context)
        state.clearPending()
        // A believable week: most days done, a couple of gaps.
        val today = LocalDate.now()
        listOf(1L to true, 2L to true, 3L to false, 4L to true).forEach { (ago, both) ->
            state.complete(SessionType.MORNING, today.minusDays(ago))
            if (both) state.complete(SessionType.EVENING, today.minusDays(ago))
        }
        state.complete(SessionType.MORNING, today)
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { content() }
        // Let entry animations finish.
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Composable
    private fun Screen(type: SessionType = SessionType.EVENING, content: @Composable () -> Unit) {
        AdhkaarTheme(Auras.of(type)) { AuraBackground(Modifier.fillMaxSize()) { content() } }
    }

    @Test
    fun today() = shoot("today") { AppRoot() }

    @Test
    @Config(qualifiers = "w393dp-h3600dp-xhdpi")
    fun todayFull() {
        shoot("today-full") { AppRoot() }
        // The parts that are new, captured on their own for the preview board.
        compose.onNodeWithTag("events").captureRoboImage("../docs/screenshots/new-events.png")
        compose.onNodeWithTag("prayers").captureRoboImage("../docs/screenshots/new-prayers.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h1300dp-xhdpi")
    fun library() = shoot("library") { Screen { LibraryScreen(onOpenCollection = {}) } }

    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun collectionDetail() {
        compose.setContent { Screen { LibraryScreen(onOpenCollection = {}) } }
        compose.onNodeWithText("After salah").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/new-collection-after-salah.png")
    }

    @Test
    fun collectionSession() = shoot("new-collection-session") {
        AdhkaarTheme(Auras.evening) { CollectionSessionScreen("before_sleep", onFinish = {}) }
    }

    @Test
    fun collectionComplete() = shoot("new-collection-complete") {
        AdhkaarTheme(Auras.evening) {
            CompletionView(
                headline = "Rest now,\nin His keeping",
                quote = "When you go to your bed, recite Ayat al-Kursi, and a guardian from Allah will remain with you, and no devil will come near you until morning.",
                reference = "al-Bukhari 5010",
                summary = Summary(12, 6, 1, "Time today"),
                onDone = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w393dp-h3000dp-xhdpi")
    fun guide() {
        shoot("guide") { Screen { org.adhkaar.app.ui.settings.GuideScreen(onBack = {}) } }
    }

    @Test
    @Config(qualifiers = "w393dp-h3600dp-xhdpi")
    fun settings() {
        shoot("settings") { Screen { SettingsScreen(onOpenSetup = {}, onOpenPrayerTimes = {}, onOpenGuide = {}) } }
        listOf("reminders", "hijri", "language", "reading").forEach {
            compose.onNodeWithTag(it).captureRoboImage("../docs/screenshots/new-settings-$it.png")
        }
    }

    /** The language picker, opened. It's a dialog, so capture the whole screen. */
    @Test
    fun languagePicker() {
        compose.setContent { Screen { SettingsScreen(onOpenSetup = {}, onOpenPrayerTimes = {}, onOpenGuide = {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("language").performScrollTo().onChildren().onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/new-language-picker.png")
    }

    /** The sky at the top of Today, at three points in the day. */
    @Test
    @Config(qualifiers = "w393dp-h852dp-xhdpi")
    fun daySky() {
        var progress by mutableStateOf(0.08f)
        compose.setContent {
            Screen {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    DaySky(
                        progress,
                        SkyEnd("Morning", "Done", done = true),
                        SkyEnd("Evening", "4:30 PM", done = false),
                    )
                }
            }
        }
        listOf("morning" to 0.08f, "noon" to 0.5f, "afternoon" to 0.72f, "evening" to 1f).forEach { (name, p) ->
            progress = p
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../docs/screenshots/new-sky-$name.png")
        }
    }

    @Test
    @Config(qualifiers = "w393dp-h1100dp-xhdpi")
    fun setup() {
        SettingsStore.get(context).update { it.copy(strictness = Strictness.LOCKDOWN) }
        shoot("setup") { Screen { SetupScreen(onBack = {}) } }
    }

    private fun startSessionWithProgress(type: SessionType) {
        val state = SessionState.get(context)
        state.start(type, LocalDate.now(), System.currentTimeMillis(), enforced = true)
        val items = AdhkaarRepository.forSession(context, type)
        state.saveProgress(mapOf(items[0].id to items[0].count, items[1].id to items[1].count, items[2].id to 1))
    }

    @Test
    fun sessionMorning() {
        startSessionWithProgress(SessionType.MORNING)
        shoot("session-morning") { AdhkaarTheme(Auras.dawn) { SessionScreen(SessionType.MORNING, onFinish = {}) } }
    }

    @Test
    fun sessionEvening() {
        startSessionWithProgress(SessionType.EVENING)
        shoot("session-evening") { AdhkaarTheme(Auras.evening) { SessionScreen(SessionType.EVENING, onFinish = {}) } }
    }

    /** The reading menu, opened. It's a popup, so capture the whole screen rather than the root. */
    @Test
    fun sessionReadingMenu() {
        startSessionWithProgress(SessionType.EVENING)
        compose.setContent { AdhkaarTheme(Auras.evening) { SessionScreen(SessionType.EVENING, onFinish = {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithContentDescription("Reading options").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/new-reading-menu.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h1100dp-xhdpi")
    fun libraryFavourites() {
        val state = SessionState.get(context)
        listOf("sayyid_al_istighfar", "ayat_al_kursi", "hasbiyallah")
            .filter { it !in state.favoritesFlow.value }
            .forEach { state.toggleFavorite(it) }
        compose.setContent { Screen { LibraryScreen(onOpenCollection = {}) } }
        compose.onNodeWithText("Favourites").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/new-favourites.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h2300dp-xhdpi")
    fun insights() {
        // Six weeks of believable history: mornings most days, evenings less often, Fridays strongest.
        val state = SessionState.get(context)
        val today = LocalDate.now()
        val random = java.util.Random(7)
        for (ago in 1L..42L) {
            val d = today.minusDays(ago)
            val friday = d.dayOfWeek == java.time.DayOfWeek.FRIDAY
            if (friday || random.nextFloat() < 0.75f) state.complete(SessionType.MORNING, d, 5 * 60 + 40 + random.nextInt(30))
            if (friday || random.nextFloat() < 0.5f) state.complete(SessionType.EVENING, d, 16 * 60 + random.nextInt(50))
            if (random.nextFloat() < 0.6f) state.logCollection("after_salah", d)
            if (random.nextFloat() < 0.35f) state.logCollection("before_sleep", d)
        }
        shoot("new-insights") { Screen { org.adhkaar.app.ui.insights.InsightsScreen() } }
    }

    @Test
    @Config(qualifiers = "w393dp-h1300dp-xhdpi")
    fun myDuas() {
        val store = org.adhkaar.app.data.UserDuaStore.get(context)
        store.flow.value.forEach { store.delete(it.id) }
        store.save(org.adhkaar.app.data.UserDua(
            id = "user_parents", title = "For my parents",
            arabic = "رَبِّ ارْحَمْهُمَا كَمَا رَبَّيَانِي صَغِيرًا",
            translation = "My Lord, have mercy on them as they raised me when I was small.",
            count = 3, inMorning = true, inEvening = true,
        ))
        store.save(org.adhkaar.app.data.UserDua(
            id = "user_exam", title = "Before my exam",
            translation = "O Allah, make it easy for me, and let me remember what I have learnt.",
            inMorning = true,
        ))
        compose.setContent { Screen { LibraryScreen(onOpenCollection = {}) } }
        compose.onNodeWithText("My duas").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/new-my-duas.png")
        // A saved recording, so the editor shows its "Your recording" state.
        org.adhkaar.app.session.Recordings.saved(context, "user_parents").writeText("test")
        compose.onNodeWithText("For my parents").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/new-dua-editor.png")
    }

    @Test
    fun shareCards() {
        val morning = AdhkaarRepository.forSession(context, SessionType.MORNING)
        val medium = morning.first { it.id == "sayyid_al_istighfar" }
        val long = morning.first { it.id == "ayat_al_kursi" }
        val short = morning.first { it.id == "subhanallahi_wa_bihamdihi" }
        val cases = listOf(
            "midnight" to (medium to org.adhkaar.app.ui.share.CardStyle.MIDNIGHT),
            "dawn" to (short to org.adhkaar.app.ui.share.CardStyle.DAWN),
            "parchment" to (medium to org.adhkaar.app.ui.share.CardStyle.PARCHMENT),
            "long" to (long to org.adhkaar.app.ui.share.CardStyle.MIDNIGHT),
        )
        // One composition per test: switch the case through state.
        var shown by androidx.compose.runtime.mutableStateOf(cases.first().second)
        compose.setContent {
            Screen {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    org.adhkaar.app.ui.share.ShareCard(shown.first, shown.second, Modifier.padding(20.dp).clip(RoundedCornerShape(24.dp)))
                }
            }
        }
        cases.forEach { (name, case) ->
            shown = case
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../docs/screenshots/new-share-card-$name.png")
        }
    }

    @Test
    fun sessionWithAudio() {
        org.adhkaar.app.session.AudioLibrary.testOverride = setOf("ayat_al_kursi")
        SessionState.get(context).start(SessionType.MORNING, LocalDate.now(), System.currentTimeMillis(), enforced = false)
        shoot("new-session-audio") { AdhkaarTheme(Auras.dawn) { SessionScreen(SessionType.MORNING, onFinish = {}) } }
        org.adhkaar.app.session.AudioLibrary.testOverride = null
    }

    @Test
    @Config(qualifiers = "w393dp-h1400dp-xhdpi")
    fun salahReminders() = shoot("salah-reminders") { Screen { org.adhkaar.app.ui.settings.PrayerTimesScreen(onBack = {}) } }

    @Test
    fun bugReport() = shoot("contact") { Screen { org.adhkaar.app.ui.settings.ContactScreen(onBack = {}) } }

    @Test
    fun bugReportSent() = shoot("contact-sent") { Screen { org.adhkaar.app.ui.settings.ContactSentView(onBack = {}) } }

    /** What covers other apps in Lockdown until the adhkaar are done. */
    @Test
    fun lockdownBlock() = shoot("lockdown") {
        AdhkaarTheme(Auras.of(SessionType.MORNING)) {
            org.adhkaar.app.enforce.BlockScreen(SessionType.MORNING, remaining = 14, started = true, onReturn = {}, onCall = {})
        }
    }

    @Test
    fun leaveDialog() = shoot("session-leave") {
        Screen(SessionType.MORNING) { org.adhkaar.app.ui.session.LeaveDialog("Morning adhkaar", remaining = 9, total = 24, onStay = {}, onLeave = {}) }
    }

    @Test
    fun breakChoice() {
        val end = System.currentTimeMillis() + 75 * 60_000L
        compose.setContent { Screen(SessionType.MORNING) { org.adhkaar.app.ui.session.BreakControl(end, remaining = { 9 }, streak = { 22 }) {} } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Take a break", substring = true, ignoreCase = true).performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/session-break.png")
        // The gentle second ask, after choosing a time.
        compose.onNodeWithText("20").performClick()
        compose.onNodeWithText("Pause for 20 minutes").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/session-break-confirm.png")
    }

    /** The morning shelf once today's morning adhkaar are done (see setUp). */
    @Test
    @Config(qualifiers = "w393dp-h1400dp-xhdpi")
    fun morningShelfDone() = shoot("library-morning-done") {
        Screen(SessionType.MORNING) { LibraryScreen(onOpenCollection = {}, openShelf = SessionType.MORNING.key) }
    }

    @Test
    fun onboardingSteps() {
        SettingsStore.get(context).update { it.copy(onboarded = false) }
        compose.setContent { AppRoot() }
        compose.onNodeWithText("Get started").performClick()
        repeat(2) { step ->
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Continue").performClick()
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../docs/screenshots/onboarding-step-${step + 2}.png")
        }
    }

    @Test
    fun salahPopup() = shoot("salah-popup") {
        Screen(SessionType.MORNING) {
            org.adhkaar.app.session.PopupCard(org.adhkaar.app.session.ReminderPopup.Kind.Salah(org.adhkaar.app.data.Prayer.MAGHRIB), {}, {})
        }
    }

    @Test
    @Config(qualifiers = "w393dp-h2400dp-xhdpi")
    fun testKit() = shoot("test-kit") { Screen { org.adhkaar.app.ui.settings.TestKitScreen(onBack = {}, onOpenSetup = {}) } }

    @Test
    fun missed() {
        compose.setContent { Screen(SessionType.MORNING) { org.adhkaar.app.ui.home.MissedSheet(org.adhkaar.app.session.MissedAdhkaar.Missed(SessionType.MORNING, LocalDate.now()), {}, {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/missed.png")
    }

    @Test
    fun completion() = shoot("complete") {
        AdhkaarTheme(Auras.dawn) { CompletionView(SessionType.MORNING, Summary(19, 8, 5), onDone = {}) }
    }

    @Test
    fun onboardingWelcome() {
        SettingsStore.get(context).update { it.copy(onboarded = false) }
        shoot("onboarding-welcome") { AppRoot() }
    }

    /** The welcome scene's day (played once on opening), held at three moments. */
    @Test
    fun onboardingWelcomeDay() {
        SettingsStore.get(context).update { it.copy(onboarded = false) }
        // Held before the first frame, so the opening animation never runs.
        org.adhkaar.app.ui.setup.welcomeScenePhase = 0.06f
        var phase by mutableStateOf(0.06f)
        compose.setContent { androidx.compose.runtime.key(phase) { AppRoot() } }
        listOf("dawn" to 0.06f, "noon" to 0.25f, "night" to 0.6f).forEach { (name, p) ->
            org.adhkaar.app.ui.setup.welcomeScenePhase = p
            phase = p
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../docs/screenshots/new-welcome-$name.png")
        }
        org.adhkaar.app.ui.setup.welcomeScenePhase = null
    }

    @Test
    fun onboardingMode() {
        SettingsStore.get(context).update { it.copy(onboarded = false) }
        compose.setContent { AppRoot() }
        compose.onNodeWithText("Get started").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/onboarding-mode.png")
    }

    @Test
    @Config(qualifiers = "w200dp-h200dp-xhdpi")
    fun appIcon() = shoot("icon") {
        Box(Modifier.fillMaxSize().background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(144.dp).clip(RoundedCornerShape(36.dp))) {
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to Color(0xFF2B4FD8), 0.5f to Color(0xFF101A4A), 1f to Color(0xFF05070F),
                            center = androidx.compose.ui.geometry.Offset(130f, 110f), radius = 560f,
                        ),
                    ),
                )
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
        }
    }
}
