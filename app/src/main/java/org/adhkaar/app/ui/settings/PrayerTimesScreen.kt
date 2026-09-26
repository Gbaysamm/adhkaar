package org.adhkaar.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.schedule.PrayerClock
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.GlassSwitch
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pushed screen: the one time the app uses for each prayer, a reminder to get ready for salah
 * rather than the moment the time begins. The user can put in their masjid's times, which replace
 * the defaults; everything (sessions, adhkaar windows, reminders) follows them. Every change is
 * saved at once.
 */
@Composable
fun PrayerTimesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val today = LocalDate.now()
    // Only shown for comparison; nothing is scheduled from it.
    val calculated = remember(settings, today) { PrayerClock.calculated(settings, today) }
    var picking by remember { mutableStateOf<Prayer?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.salah_reminders_title), style = Type.displayL)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.salah_reminders_note), style = Type.bodyL)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.prayer_times_edit_hint), style = Type.bodyM.copy(color = Nur.textSecondary))
            Spacer(Modifier.height(Space.xl))

            SettingsGroup(Modifier.testTag("salah_reminders")) {
                Prayer.entries.forEachIndexed { i, prayer ->
                    if (i > 0) RowDivider()
                    PrayerRow(
                        prayer,
                        time = PrayerClock.time(settings, prayer),
                        calculated = calculated?.get(prayer),
                        // Sunrise isn't a prayer, so it has no reminder; its time still ends the morning's best time.
                        reminder = if (prayer in Prayer.five) settings.salahReminderOn(prayer) else null,
                        onPickTime = { picking = prayer },
                        onReminder = { on ->
                            updateSettings(context) {
                                it.copy(salahRemindersOff = if (on) it.salahRemindersOff - prayer else it.salahRemindersOff + prayer)
                            }
                        },
                    )
                }
            }

            val canReset = settings.prayerTimesSet
            Box(
                Modifier
                    .padding(top = Space.s)
                    .heightIn(min = 48.dp)
                    .pressable(enabled = canReset, onClick = { updateSettings(context) { it.copy(prayerMinutes = emptyMap()) } }),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringResource(R.string.prayer_times_reset),
                    style = Type.label, color = if (canReset) LocalAura.current.accent else Nur.textDisabled,
                )
            }

            GroupTitle(stringResource(R.string.prayer_times_group_calculation))
            SettingsGroup {
                LocationRow()
                RowDivider()
                // The method is chosen in Settings, where it has always lived; this row leads back there.
                SettingsRow(stringResource(R.string.settings_calc_method), stringResource(settings.calcMethod.label), Icons.Rounded.Calculate, onClick = onBack)
            }
            Text(stringResource(R.string.prayer_times_begin_note), style = Type.caption, modifier = Modifier.padding(top = Space.s))
            Spacer(Modifier.height(Space.xxl))
        }
    }

    picking?.let { prayer ->
        TimeDialog(settings.prayerMinute(prayer), onDismiss = { picking = null }) { minute ->
            picking = null
            // The user's time replaces the default entirely.
            updateSettings(context) { it.copy(prayerMinutes = it.prayerMinutes + (prayer to minute)) }
        }
    }
}

/**
 * One prayer: tap the row to change its time, the switch to turn its reminder off and on
 * ([reminder] is null for sunrise, which has none). With a location, the calculated start sits
 * quietly underneath for comparison.
 */
@Composable
private fun PrayerRow(
    prayer: Prayer,
    time: LocalTime,
    calculated: LocalTime?,
    reminder: Boolean?,
    onPickTime: () -> Unit,
    onReminder: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val tint = if (reminder != false) LocalAura.current.accent else Nur.textTertiary
    SettingsRow(
        stringResource(prayer.label),
        calculated?.let { stringResource(R.string.prayer_times_calculated, clock(context, it)) },
        icon = prayer.icon(),
        iconTint = tint,
        onClick = onPickTime,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPill(
                    clock(context, time),
                    Modifier.pressable(onClick = onPickTime),
                    color = tint,
                    icon = Icons.Rounded.Edit,
                )
                if (reminder != null) {
                    Spacer(Modifier.width(Space.m))
                    GlassSwitch(reminder, onReminder)
                }
            }
        },
    )
}

private fun clock(context: android.content.Context, time: LocalTime) = formatTime(context, ZonedDateTime.now().with(time))

// The same icons as the Today prayer card.
private fun Prayer.icon(): ImageVector = when (this) {
    Prayer.FAJR -> Icons.Rounded.WbTwilight
    Prayer.SUNRISE -> Icons.Rounded.WbSunny
    Prayer.DHUHR -> Icons.Rounded.LightMode
    Prayer.ASR -> Icons.Rounded.WbCloudy
    Prayer.MAGHRIB -> Icons.Rounded.Brightness4
    Prayer.ISHA -> Icons.Rounded.NightsStay
}
