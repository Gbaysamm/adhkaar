package org.adhkaar.app.ui

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.adhkaar.app.R
import org.adhkaar.app.data.CollectionMode
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.ui.settings.ContactScreen
import org.adhkaar.app.ui.settings.GuideScreen
import org.adhkaar.app.ui.settings.PrayerTimesScreen
import org.adhkaar.app.ui.components.LocalEntrances
import org.adhkaar.app.ui.components.Entrances
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import org.adhkaar.app.data.ResumeState
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.GlassTabBar
import org.adhkaar.app.ui.components.Tab
import org.adhkaar.app.ui.home.TodayScreen
import org.adhkaar.app.ui.insights.InsightsScreen
import org.adhkaar.app.ui.home.heroSession
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.library.LibraryScreen
import org.adhkaar.app.ui.session.CollectionSessionScreen
import org.adhkaar.app.ui.settings.SettingsScreen
import org.adhkaar.app.ui.settings.TestKitScreen
import org.adhkaar.app.ui.setup.OnboardingScreen
import org.adhkaar.app.ui.setup.SetupScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Space
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.ZonedDateTime

/**
 * The app. Everything on screen is saved with the activity, so it reopens where the user left
 * it after Android kills the process in the background; see [ResumeState] for how to test that.
 */
class MainActivity : ComponentActivity() {
    /** A collection a reminder notification asked us to open. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(org.adhkaar.app.data.Languages.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ResumeState.actsOnLaunchIntent(intent.flags, restored = savedInstanceState != null)) {
            openRequest.value = intent.getStringExtra(Notifications.EXTRA_OPEN_COLLECTION)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { AppRoot(openRequest.value, onRequestHandled = { openRequest.value = null }) }
        // The moon-sighting list, at most twice a day; a plain download of a public file.
        Thread { org.adhkaar.app.data.MoonSighting.refreshIfDue(applicationContext) }.start()
    }

    // A notification tapped while the app is alive, or while it's being restored after the process
    // was killed, arrives here and wins over whatever was restored.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_OPEN_COLLECTION)?.let { openRequest.value = it }
    }

    // The process can outlive a service the phone stopped (Full screen has no watchdog), so
    // coming back to the app also restores a pending session's alarms and enforcement.
    override fun onResume() {
        super.onResume()
        SessionLauncher.resumePending(this)
    }
}

@Composable
fun AppRoot(openCollectionRequest: String? = null, onRequestHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val tabs = listOf(
        Tab(stringResource(R.string.tab_today), Icons.Rounded.WbTwilight),
        Tab(stringResource(R.string.tab_adhkaar), Icons.AutoMirrored.Rounded.MenuBook),
        Tab(stringResource(R.string.tab_insights), Icons.Rounded.Insights),
        Tab(stringResource(R.string.tab_settings), Icons.Rounded.Settings),
    )
    val settings by SettingsStore.get(context).flow.collectAsState()
    val pending by SessionState.get(context).pendingFlow.collectAsState()
    // The whole app takes the colours of the session it is currently about.
    val aura = Auras.of(remember(pending, settings) { heroSession(context, pending, ZonedDateTime.now()) })

    AdhkaarTheme(aura) {
        if (!settings.onboarded) {
            AuraBackground(Modifier.fillMaxSize(), aura = Auras.evening) {
                AdhkaarTheme(Auras.evening) { OnboardingScreen(onFinished = {}) }
            }
            return@AdhkaarTheme
        }

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showSetup by rememberSaveable { mutableStateOf(false) }
        var showPrayerTimes by rememberSaveable { mutableStateOf(false) }
        var showGuide by rememberSaveable { mutableStateOf(false) }
        var showTestKit by rememberSaveable { mutableStateOf(false) }
        var showContact by rememberSaveable { mutableStateOf(false) }
        var openCollection by rememberSaveable { mutableStateOf<String?>(null) }
        // A shelf Today asked the Adhkaar tab to open (Everyday duas is a list, not a session).
        var shelfRequest by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(openCollectionRequest) {
            if (openCollectionRequest != null) {
                // A reference list (Everyday duas) opens in the Adhkaar tab; the rest are sessions.
                val list = CollectionsRepository.all(context).firstOrNull { it.id == openCollectionRequest }?.mode == CollectionMode.REFERENCE
                if (list) {
                    shelfRequest = openCollectionRequest
                    tab = 1
                } else {
                    openCollection = openCollectionRequest
                }
                onRequestHandled()
            }
        }
        val haze = remember { HazeState() }
        BackHandler(enabled = showSetup || showPrayerTimes || showGuide || showTestKit || showContact || tab != 0 || openCollection != null) {
            when {
                openCollection != null -> openCollection = null
                showPrayerTimes -> showPrayerTimes = false
                showGuide -> showGuide = false
                showTestKit -> showTestKit = false
                showContact -> showContact = false
                showSetup -> showSetup = false
                else -> tab = 0
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Everything under the tab bar is a haze source, so the bar blurs the aura and the content.
            AuraBackground(Modifier.fillMaxSize().hazeSource(haze)) {
                // Each tab keeps its own state (its scroll position) while another is showing.
                val saved = rememberSaveableStateHolder()
                // Right-to-left languages mirror the direction of travel, like the tab bar.
                val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                AnimatedContent(
                    targetState = tab,
                    // The page follows the tab bar: choosing a tab to the right brings its page in
                    // from the right. A short travel with a fade, so it reads as a shift, not a push;
                    // the old page is nearly gone before the new one appears, so they never overlap.
                    transitionSpec = {
                        val direction = if (targetState > initialState) mirror else -mirror
                        (slideInHorizontally(tween(620, easing = Motion.emphasized)) { direction * it / 10 } + fadeIn(tween(420, delayMillis = 140))) togetherWith
                            (slideOutHorizontally(tween(320, easing = Motion.emphasized)) { -direction * it / 14 } + fadeOut(tween(200)))
                    },
                    label = "tabs",
                ) { current ->
                    saved.SaveableStateProvider(current) {
                        // A fresh line for the cards each time a page opens.
                        CompositionLocalProvider(LocalEntrances provides remember { Entrances() }) {
                            when (current) {
                                0 -> TodayScreen(
                                    onOpenSetup = { showSetup = true },
                                    onOpenCollection = { openCollection = it },
                                    onOpenShelf = { shelfRequest = it; tab = 1 },
                                    onOpenPrayerTimes = { showPrayerTimes = true },
                                )
                                1 -> LibraryScreen(
                                    onOpenCollection = { openCollection = it },
                                    openShelf = shelfRequest,
                                    onShelfOpened = { shelfRequest = null },
                                )
                                2 -> InsightsScreen()
                                else -> SettingsScreen(onOpenSetup = { showSetup = true }, onOpenPrayerTimes = { showPrayerTimes = true }, onOpenGuide = { showGuide = true }, onOpenContact = { showContact = true }, onOpenTestKit = { showTestKit = true })
                            }
                        }
                    }
                }
            }
            GlassTabBar(
                tabs, tab, haze,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Same side margins as the page content, so the bar lines up with the cards.
                    .padding(start = Space.gutter, end = Space.gutter, bottom = Space.gutter),
            ) { tab = it }

            // Pushed screen: slides over everything, including the tab bar.
            AnimatedVisibility(
                visible = showSetup,
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()),
                exit = slideOutHorizontally(Motion.enter()) { it } + fadeOut(Motion.exit()),
            ) {
                AuraBackground(Modifier.fillMaxSize()) { SetupScreen(onBack = { showSetup = false }) }
            }

            // The prayer times editor, pushed like Setup.
            AnimatedVisibility(
                visible = showPrayerTimes,
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()),
                exit = slideOutHorizontally(Motion.enter()) { it } + fadeOut(Motion.exit()),
            ) {
                AuraBackground(Modifier.fillMaxSize()) { PrayerTimesScreen(onBack = { showPrayerTimes = false }) }
            }

            AnimatedVisibility(
                visible = showGuide,
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()),
                exit = slideOutHorizontally(Motion.enter()) { it } + fadeOut(Motion.exit()),
            ) {
                AuraBackground(Modifier.fillMaxSize()) { GuideScreen(onBack = { showGuide = false }) }
            }

            AnimatedVisibility(
                visible = showContact,
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()),
                exit = slideOutHorizontally(Motion.enter()) { it } + fadeOut(Motion.exit()),
            ) {
                AuraBackground(Modifier.fillMaxSize()) { ContactScreen(onBack = { showContact = false }) }
            }

            AnimatedVisibility(
                visible = showTestKit,
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()),
                exit = slideOutHorizontally(Motion.enter()) { it } + fadeOut(Motion.exit()),
            ) {
                AuraBackground(Modifier.fillMaxSize()) {
                    TestKitScreen(onBack = { showTestKit = false }, onOpenSetup = { showSetup = true })
                }
            }

            // A collection session rises over everything, like a sheet.
            var lastCollection by remember { mutableStateOf<String?>(null) }
            if (openCollection != null) lastCollection = openCollection
            AnimatedVisibility(
                visible = openCollection != null,
                enter = slideInVertically(Motion.enter()) { it / 3 } + fadeIn(Motion.enter()),
                exit = slideOutVertically(Motion.enter()) { it / 3 } + fadeOut(Motion.exit()),
            ) {
                // Keep showing the last collection while the exit animation runs. Keyed, so another
                // collection starts with its own saved state (its page) rather than this one's.
                lastCollection?.let { key(it) { CollectionSessionScreen(it, onFinish = { openCollection = null }) } }
            }
        }
    }
}

