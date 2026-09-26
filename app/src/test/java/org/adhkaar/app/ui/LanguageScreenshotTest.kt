package org.adhkaar.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.ui.session.SessionScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Today screen and a session in each supported language, to check right-to-left layout
 * (Arabic, Urdu) and West African letters in headings (Hausa, Yoruba, Igbo).
 * Run: ./gradlew testDebugUnitTest --tests "*LanguageScreenshotTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class LanguageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @org.junit.After
    fun resetLocale() {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
    }

    @Before
    fun setUp() {
        SettingsStore.get(context).update {
            it.copy(onboarded = true, strictness = Strictness.LOCKDOWN, latitude = 6.5244, longitude = 3.3792)
        }
        val state = SessionState.get(context)
        state.clearPending()
        (1L..4L).forEach { state.complete(SessionType.MORNING, LocalDate.now().minusDays(it)) }
    }

    private fun shoot(language: String) {
        RuntimeEnvironment.setQualifiers("$language-w393dp-h852dp-xhdpi")
        // On a phone, choosing a language also sets the default locale (dates follow it); do the same here.
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(language))
        compose.setContent { AppRoot() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/lang-$language-today.png")
    }

    @Test fun arabic() = shoot("ar")
    @Test fun urdu() = shoot("ur")
    @Test fun hausa() = shoot("ha")
    @Test fun yoruba() = shoot("yo")
    @Test fun igbo() = shoot("ig")

    @Test
    fun arabicSession() {
        RuntimeEnvironment.setQualifiers("ar-w393dp-h852dp-xhdpi")
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        // With a recording, so the controls around the counter are checked right to left too.
        org.adhkaar.app.session.AudioLibrary.testOverride = setOf("ayat_al_kursi")
        SessionState.get(context).start(SessionType.EVENING, LocalDate.now(), System.currentTimeMillis(), enforced = true)
        compose.setContent { AdhkaarTheme(Auras.evening) { SessionScreen(SessionType.EVENING, onFinish = {}) } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/lang-ar-session.png")
        org.adhkaar.app.session.AudioLibrary.testOverride = null
    }
}
