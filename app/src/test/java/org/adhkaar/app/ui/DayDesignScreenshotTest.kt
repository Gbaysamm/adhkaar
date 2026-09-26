package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.adhkaar.app.data.DayReminder
import org.adhkaar.app.data.DayReminders
import org.adhkaar.app.data.HijriMonths
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.ReminderKind
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.label
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.DualCalendar
import org.adhkaar.app.ui.home.DayReminderCard
import org.adhkaar.app.ui.reminder.DayReminderSheet
import org.adhkaar.app.ui.share.DayCardFrame
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the day's reminder card, the calendar, the day sheet and the day card image to
 * docs/screenshots for design review.
 * Run: ./gradlew :app:testDebugUnitTest --tests "*DayDesignScreenshotTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class DayDesignScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        SettingsStore.get(context).update { it.copy(onboarded = true, latitude = 6.5244, longitude = 3.3792) }
        // Three weeks of believable history: mornings most days, evenings less often.
        val state = SessionState.get(context)
        val random = java.util.Random(11)
        for (ago in 0L..21L) {
            val d = today.minusDays(ago)
            if (random.nextFloat() < 0.75f) state.complete(SessionType.MORNING, d, 5 * 60 + 50)
            if (ago > 0 && random.nextFloat() < 0.55f) state.complete(SessionType.EVENING, d, 16 * 60 + 20)
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        AdhkaarTheme(Auras.evening) { AuraBackground(Modifier.fillMaxSize()) { content() } }
    }

    private fun hijri(date: LocalDate) = MoonSighting.hijriFor(context, date, SettingsStore.get(context).flow.value)

    private fun todaysReminder() = DayReminders.forDate(DayReminders.all(context), today, hijri(today))

    /** A verse with short Arabic, so the Arabic line is shown whatever the day's reminder is. */
    private fun arabicReminder() = DayReminder(
        id = "screenshot_mercy",
        kind = ReminderKind.VERSE,
        arabic = "وَرَحْمَتِي وَسِعَتْ كُلَّ شَيْءٍ",
        meaning = "And My mercy reaches everything.",
        reference = "Surah al-A'raf 7:156 (part)",
    )

    /** A long meaning with no Arabic, to check the card fits it without cutting it off. */
    private fun longReminder() = DayReminder(
        id = "screenshot_long",
        kind = ReminderKind.HADITH,
        meaning = "Whoever relieves a believer of a hardship of this world, Allah will relieve him of a hardship of the Day of Resurrection. Whoever makes things easy for one in difficulty, Allah will make things easy for him in this world and the next.",
        reference = "Sahih Muslim 2699",
    )

    @Test
    fun reminderCard() {
        val cases = listOf("day-reminder-card" to todaysReminder(), "day-reminder-card-arabic" to arabicReminder())
        // One composition per test: switch the case through state.
        var shown by mutableStateOf(cases.first().second)
        compose.setContent {
            Screen {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 40.dp)) {
                    DayReminderCard(shown, IslamicCalendar.label(context, hijri(today)), Modifier) {}
                }
            }
        }
        cases.forEach { (name, reminder) ->
            shown = reminder
            settle()
            compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
        }
    }

    private fun calendar(name: String) {
        val state = SessionState.get(context)
        compose.setContent {
            Screen {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 40.dp)) {
                    DualCalendar(state.historyFlow.value, state.completionTimes(), SettingsStore.get(context).flow.value, today)
                }
            }
        }
        settle()
        // The leading calendar is saved app-wide, so each one is chosen explicitly.
        compose.onNodeWithText("Hijri").performClick()
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name-hijri.png")
        compose.onNodeWithText("Gregorian").performClick()
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name-gregorian.png")
        // Back a month, in each calendar: the arrows must work however little history there is.
        compose.onNodeWithContentDescription("Previous month").performClick()
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name-gregorian-previous.png")
        compose.onNodeWithText("Hijri").performClick()
        compose.onNodeWithContentDescription("Previous month").performClick()
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name-hijri-previous.png")
    }

    @Test
    fun calendar() = calendar("day-calendar")

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun calendarNarrow() = calendar("day-calendar-360")

    /** Today's top: the greeting with the salam, and the hero card with its best-time line. */
    @Test
    @Config(qualifiers = "w393dp-h1300dp-xhdpi")
    fun todayTop() {
        compose.setContent { AppRoot() }
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/day-today-top.png")
    }

    /** The sheet is a dialog, so the whole screen is captured. */
    @Test
    fun daySheet() {
        compose.setContent { Screen { DayReminderSheet(today) {} } }
        settle()
        captureScreenRoboImage("../docs/screenshots/day-sheet.png")
    }

    /** The exported image at its real size: 540dp at xhdpi is 1080 × 1350 px. */
    @Test
    @Config(qualifiers = "w540dp-h675dp-xhdpi")
    fun dayCard() {
        val settings = SettingsStore.get(context).flow.value
        val h = hijri(today)
        val span = HijriMonths.span(today, h, null, settings.hijriOffset)
        val cases = listOf("day-card" to todaysReminder(), "day-card-arabic" to arabicReminder(), "day-card-long" to longReminder())
        var shown by mutableStateOf(cases.first().second)
        compose.setContent {
            AdhkaarTheme(Auras.evening) {
                DayCardFrame(today, h, span, shown, { hijri(it) }, rememberGraphicsLayer(), Modifier.fillMaxWidth())
            }
        }
        cases.forEach { (name, reminder) ->
            shown = reminder
            settle()
            compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
        }
    }
}
