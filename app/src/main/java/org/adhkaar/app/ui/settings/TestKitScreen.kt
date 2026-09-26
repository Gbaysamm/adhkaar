package org.adhkaar.app.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import org.adhkaar.app.R
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.enforce.ForegroundAppDetector
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/**
 * For testers who can't wait for Fajr: every alert the app can give, on demand, a few seconds
 * later, and a check of the phone settings that most often stop them. Test sessions are marked
 * as tests, so nothing here touches the real record.
 */
@Composable
fun TestKitScreen(onBack: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val mode = stringResource(
        when (settings.strictness) {
            Strictness.GENTLE -> R.string.mode_gentle
            Strictness.FULL_SCREEN -> R.string.mode_full_screen
            Strictness.LOCKDOWN -> R.string.mode_lockdown
        },
    )
    // Re-read every couple of seconds, so fixing a permission and coming back shows at once.
    val checks by produceState(phoneChecks(context)) {
        while (true) {
            delay(2_000)
            value = phoneChecks(context)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding()
                .testTag("test_kit"),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.kit_title), style = Type.displayL)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.kit_intro), style = Type.bodyL)

            GroupTitle(stringResource(R.string.kit_group_phone))
            GlassCard(Modifier.fillMaxWidth()) {
                checks.forEachIndexed { i, (label, ok) ->
                    if (i > 0) Spacer(Modifier.height(Space.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, Modifier.size(20.dp),
                            tint = if (ok) LocalAura.current.accent else Nur.textTertiary,
                        )
                        Spacer(Modifier.width(Space.m))
                        Text(stringResource(label), style = Type.bodyM.copy(color = if (ok) Nur.textPrimary else Nur.textSecondary))
                    }
                }
                if (checks.any { !it.second }) {
                    Spacer(Modifier.height(Space.l))
                    SecondaryButton(stringResource(R.string.kit_fix), Modifier.fillMaxWidth(), onClick = onOpenSetup)
                }
            }

            GroupTitle(stringResource(R.string.kit_group_sessions))
            Text(stringResource(R.string.kit_sessions_note, mode), style = Type.bodyM.copy(color = Nur.textSecondary))
            Spacer(Modifier.height(Space.m))
            TryCard(Icons.Rounded.WbTwilight, R.string.settings_test_morning, R.string.kit_session_expect) {
                AlarmScheduler.scheduleTest(context, SessionType.MORNING, DELAY_MS)
            }
            TryCard(Icons.Rounded.DarkMode, R.string.settings_test_evening, R.string.kit_session_expect) {
                AlarmScheduler.scheduleTest(context, SessionType.EVENING, DELAY_MS)
            }

            GroupTitle(stringResource(R.string.kit_group_reminders))
            TryCard(Icons.Rounded.Mosque, R.string.kit_salah, R.string.kit_salah_expect) {
                later(context) { Notifications.showSalahReminder(it, Prayer.MAGHRIB) }
            }
            TryCard(Icons.Rounded.Mosque, R.string.moment_title_after_salah, R.string.kit_collection_expect) {
                later(context) { Notifications.showCollection(it, "after_salah") }
            }
            TryCard(Icons.Rounded.Bedtime, R.string.moment_title_before_sleep, R.string.kit_collection_expect) {
                later(context) { Notifications.showCollection(it, "before_sleep") }
            }
            TryCard(Icons.Rounded.NotificationsActive, R.string.kit_heads_up, R.string.kit_heads_up_expect) {
                later(context) { Notifications.showPreReminder(it, SessionType.MORNING, 10) }
            }
            TryCard(Icons.Rounded.CalendarMonth, R.string.kit_calendar, R.string.kit_calendar_expect) {
                later(context) { Notifications.showCalendar(it, it.getString(R.string.kit_calendar_sample_title), it.getString(R.string.kit_calendar_sample_body)) }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

private const val DELAY_MS = 10_000L

@Composable
private fun TryCard(icon: ImageVector, title: Int, expect: Int, onTry: () -> Unit) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth().padding(bottom = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = LocalAura.current.accent)
            Spacer(Modifier.width(Space.m))
            Text(stringResource(title), style = Type.titleM)
        }
        Spacer(Modifier.height(Space.s))
        Text(stringResource(expect), style = Type.bodyM.copy(color = Nur.textSecondary))
        Spacer(Modifier.height(Space.m))
        SecondaryButton(stringResource(R.string.kit_try), Modifier.fillMaxWidth()) {
            onTry()
            Toast.makeText(context, context.getString(R.string.kit_toast), Toast.LENGTH_LONG).show()
        }
    }
}

/** Fires [show] a few seconds from now, so the tester can switch to another app or lock the screen. */
private fun later(context: Context, show: (Context) -> Unit) {
    val app = context.applicationContext
    Handler(Looper.getMainLooper()).postDelayed({ show(app) }, DELAY_MS)
}

/** What a reminder needs from the phone, each with whether it's allowed. */
private fun phoneChecks(context: Context): List<Pair<Int, Boolean>> = listOf(
    R.string.kit_check_notifications to NotificationManagerCompat.from(context).areNotificationsEnabled(),
    R.string.kit_check_alarms to (Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()),
    R.string.kit_check_overlay to Settings.canDrawOverlays(context),
    R.string.kit_check_battery to context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    R.string.kit_check_usage to ForegroundAppDetector.hasPermission(context),
)
