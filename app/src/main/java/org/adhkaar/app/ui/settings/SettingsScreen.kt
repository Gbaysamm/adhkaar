package org.adhkaar.app.ui.settings

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.adhkaar.app.data.AlertSound
import org.adhkaar.app.session.AlertPlayer
import org.adhkaar.app.session.AlertPolicy
import org.adhkaar.app.session.RecitationPlayer
import org.adhkaar.app.session.Recordings
import org.adhkaar.app.session.VoiceRecorder
import org.adhkaar.app.ui.components.glass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adhkaar.app.BuildConfig
import org.adhkaar.app.R
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.components.GlassCard
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import org.adhkaar.app.ui.components.pressable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.adhkaar.app.ui.theme.NotoSansArabic
import org.adhkaar.app.data.CalcMethod
import org.adhkaar.app.data.Languages
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.label
import org.adhkaar.app.data.SessionSchedule
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.data.TimeMode
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.ui.components.GlassSegmented
import org.adhkaar.app.ui.components.GlassSwitch
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onOpenSetup: () -> Unit, onOpenPrayerTimes: () -> Unit, onOpenGuide: () -> Unit, onOpenContact: () -> Unit = {}, onOpenTestKit: () -> Unit = {}) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    // A break pauses Lockdown without ending it, so the mode stays locked until the window closes.
    val lockdownActive = settings.strictness == Strictness.LOCKDOWN &&
        SessionState.get(context).pending.let { AlertPolicy.isWindowOpen(it, SessionLauncher.windowMinutes(context, it), System.currentTimeMillis()) }
    var methodDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var fridayPicker by remember { mutableStateOf(false) }
    var bedtimePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        Spacer(Modifier.height(Space.xl))
        Text(stringResource(R.string.settings_title), style = Type.displayL)

        GroupTitle(stringResource(R.string.settings_group_help))
        SettingsGroup {
            SettingsRow(stringResource(R.string.settings_guide), stringResource(R.string.settings_guide_hint), Icons.AutoMirrored.Rounded.MenuBook, onClick = onOpenGuide)
            RowDivider()
            SettingsRow(stringResource(R.string.kit_title), stringResource(R.string.kit_row_hint), Icons.Rounded.Science, onClick = onOpenTestKit)
            RowDivider()
            SettingsRow(stringResource(R.string.settings_report_bug), stringResource(R.string.settings_report_bug_hint), Icons.Rounded.ChatBubbleOutline, onClick = onOpenContact)
        }

        GroupTitle(stringResource(R.string.settings_group_mode))
        StrictnessOptions(settings.strictness, enabled = !lockdownActive) { s -> updateSettings(context) { it.copy(strictness = s) } }
        if (lockdownActive) {
            Text(
                stringResource(R.string.settings_mode_locked), style = Type.caption,
                modifier = Modifier.padding(top = Space.s),
            )
        }

        SessionType.entries.forEach { type ->
            GroupTitle(stringResource(if (type == SessionType.MORNING) R.string.settings_group_morning else R.string.settings_group_evening))
            ScheduleGroup(type, settings.schedule(type)) { updated ->
                updateSettings(context) { if (type == SessionType.MORNING) it.copy(morning = updated) else it.copy(evening = updated) }
            }
        }

        GroupTitle(stringResource(R.string.settings_group_prayer_times))
        SettingsGroup {
            LocationRow()
            RowDivider()
            SettingsRow(stringResource(R.string.settings_calc_method), stringResource(settings.calcMethod.label), Icons.Rounded.Calculate, onClick = { methodDialog = true })
            RowDivider()
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.m)) {
                Text(stringResource(R.string.settings_asr_time), style = Type.label)
                Spacer(Modifier.height(Space.m))
                GlassSegmented(listOf(stringResource(R.string.settings_asr_standard), stringResource(R.string.settings_asr_hanafi)), if (settings.hanafiAsr) 1 else 0) { i ->
                    updateSettings(context) { it.copy(hanafiAsr = i == 1) }
                }
            }
            RowDivider()
            SettingsRow(
                stringResource(R.string.salah_reminders_title),
                stringResource(if (settings.prayerTimesSet) R.string.settings_prayer_times_adjusted else R.string.settings_prayer_times_default),
                Icons.Rounded.Schedule,
                onClick = onOpenPrayerTimes,
            )
        }

        GroupTitle(stringResource(R.string.settings_group_reminders))
        SettingsGroup(Modifier.testTag("reminders")) {
            AlertSoundSetting(settings.alertSound)
            RowDivider()
            SettingsRow(stringResource(R.string.settings_heads_up), stringResource(R.string.settings_heads_up_hint), Icons.Rounded.NotificationsActive, trailing = {
                Stepper(
                    if (settings.preReminderMinutes == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_minutes_short, settings.preReminderMinutes),
                    onMinus = { updateSettings(context) { it.copy(preReminderMinutes = it.preReminderMinutes - 5) } },
                    onPlus = { updateSettings(context) { it.copy(preReminderMinutes = it.preReminderMinutes + 5) } },
                    canMinus = settings.preReminderMinutes > 0, canPlus = settings.preReminderMinutes < 30,
                )
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_after_salah), stringResource(R.string.settings_after_salah_hint, AlarmScheduler.AFTER_SALAH_DELAY_MINUTES), Icons.Rounded.Mosque, trailing = {
                GlassSwitch(settings.afterSalahReminder, { v -> updateSettings(context) { it.copy(afterSalahReminder = v) } })
            if (settings.afterSalahReminder) {
                CollectionModeRow("after_salah", listOf(Strictness.GENTLE, Strictness.FULL_SCREEN), settings.collectionMode("after_salah"))
            }
            })
            RowDivider()
            SettingsRow(
                stringResource(R.string.settings_before_sleep),
                if (settings.bedtimeMinute < 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_before_sleep_hint),
                Icons.Rounded.Bedtime,
                onClick = if (settings.bedtimeMinute >= 0) ({ bedtimePicker = true }) else null,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (settings.bedtimeMinute >= 0) {
                            ValueText(formatTime(context, ZonedDateTime.now().withHour(settings.bedtimeMinute / 60).withMinute(settings.bedtimeMinute % 60)))
                            Spacer(Modifier.width(Space.m))
                        }
                        GlassSwitch(settings.bedtimeMinute >= 0, { on ->
                            updateSettings(context) { it.copy(bedtimeMinute = if (on) 22 * 60 else -1) }
                        })
                    }
                },
            )
            if (settings.bedtimeMinute >= 0) {
                CollectionModeRow("before_sleep", listOf(Strictness.GENTLE, Strictness.FULL_SCREEN, Strictness.LOCKDOWN), settings.collectionMode("before_sleep"))
            }
            RowDivider()
            SettingsRow(stringResource(R.string.settings_islamic_calendar), stringResource(R.string.settings_islamic_calendar_hint), Icons.Rounded.CalendarMonth, trailing = {
                GlassSwitch(settings.calendarReminders, { v -> updateSettings(context) { it.copy(calendarReminders = v) } })
            })
            if (settings.calendarReminders) {
                RowDivider()
                SettingsRow(stringResource(R.string.settings_friday_reminder), stringResource(R.string.settings_friday_reminder_hint), Icons.Rounded.Mosque, onClick = { fridayPicker = true }, trailing = {
                    ValueText(formatTime(context, ZonedDateTime.now().withHour(settings.fridayReminderMinute / 60).withMinute(settings.fridayReminderMinute % 60)))
                })
            }
        }

        GroupTitle(stringResource(R.string.settings_group_hijri))
        SettingsGroup(Modifier.testTag("hijri")) {
            SettingsRow(stringResource(R.string.settings_moon_sighting), stringResource(R.string.settings_moon_sighting_hint), Icons.Rounded.Brightness2, trailing = {
                GlassSwitch(settings.followMoonSighting, { v -> updateSettings(context) { it.copy(followMoonSighting = v) } })
            })
            RowDivider()
            val today = java.time.LocalDate.now()
            val announced = settings.followMoonSighting && MoonSighting.hijri(MoonSighting.current(context), today) != null
            SettingsRow(
                stringResource(R.string.settings_hijri_date),
                IslamicCalendar.label(context, MoonSighting.hijriFor(context, today, settings)),
                Icons.Rounded.CalendarMonth,
                // Announced dates need no adjusting; the offset is for the calculated fallback.
                trailing = if (announced) null else {
                    {
                        Stepper(
                            if (settings.hijriOffset > 0) "+${settings.hijriOffset}" else "${settings.hijriOffset}",
                            onMinus = { updateSettings(context) { it.copy(hijriOffset = it.hijriOffset - 1) } },
                            onPlus = { updateSettings(context) { it.copy(hijriOffset = it.hijriOffset + 1) } },
                            canMinus = settings.hijriOffset > -2, canPlus = settings.hijriOffset < 2,
                        )
                    }
                },
            )
        }
        Text(
            stringResource(R.string.settings_hijri_hint),
            style = Type.caption, modifier = Modifier.padding(top = Space.s),
        )

        GroupTitle(stringResource(R.string.settings_group_language))
        SettingsGroup(Modifier.testTag("language")) {
            val current = Languages.current(context)
            SettingsRow(
                stringResource(R.string.settings_language),
                Languages.all.firstOrNull { it.tag == current }?.nativeName ?: stringResource(R.string.settings_language_system),
                Icons.Rounded.Language,
                onClick = { languageDialog = true },
            )
        }

        GroupTitle(stringResource(R.string.settings_group_reading))
        SettingsGroup(Modifier.testTag("reading")) {
            SettingsRow(stringResource(R.string.settings_arabic_size), null, Icons.Rounded.FormatSize, trailing = {
                Stepper(
                    stringResource(R.string.settings_percent, (settings.arabicScale * 100).roundToInt()),
                    onMinus = { updateSettings(context) { it.copy(arabicScale = (it.arabicScale - 0.1f).coerceAtLeast(0.8f)) } },
                    onPlus = { updateSettings(context) { it.copy(arabicScale = (it.arabicScale + 0.1f).coerceAtMost(1.5f)) } },
                    canMinus = settings.arabicScale > 0.85f, canPlus = settings.arabicScale < 1.45f,
                )
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_auto_advance), stringResource(R.string.settings_auto_advance_hint), Icons.Rounded.SkipNext, trailing = {
                GlassSwitch(settings.autoAdvance, { v -> updateSettings(context) { it.copy(autoAdvance = v) } })
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_auto_play), stringResource(R.string.settings_auto_play_hint), Icons.Rounded.GraphicEq, trailing = {
                GlassSwitch(settings.autoPlayRecitation, { v -> updateSettings(context) { it.copy(autoPlayRecitation = v) } })
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_hint), Icons.Rounded.Vibration, trailing = {
                GlassSwitch(settings.haptics, { v -> updateSettings(context) { it.copy(haptics = v) } })
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_transliteration), null, Icons.Rounded.TextFields, trailing = {
                GlassSwitch(settings.showTransliteration, { v -> updateSettings(context) { it.copy(showTransliteration = v) } })
            })
            RowDivider()
            SettingsRow(stringResource(R.string.settings_translation), null, Icons.Rounded.Translate, trailing = {
                GlassSwitch(settings.showTranslation, { v -> updateSettings(context) { it.copy(showTranslation = v) } })
            })
        }

        GroupTitle(stringResource(R.string.settings_group_phone))
        SettingsGroup {
            SettingsRow(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_hint), Icons.Rounded.HealthAndSafety, onClick = onOpenSetup)
        }

        // The daily reminders' sources require this credit (docs/REMINDERS.md): all lines together, unchanged.
        GroupTitle(stringResource(R.string.reminder_sources_title))
        GlassCard(Modifier.fillMaxWidth()) {
            listOf(
                stringResource(R.string.reminder_sources_quran_arabic, stringResource(R.string.reminder_sources_tanzil_version)),
                stringResource(R.string.reminder_sources_quran_meaning, stringResource(R.string.reminder_sources_rowwad_version)),
                stringResource(R.string.reminder_sources_hadith, stringResource(R.string.reminder_sources_hadeethenc_version)),
                stringResource(R.string.reminder_sources_unchanged),
            ).forEachIndexed { i, line ->
                if (i > 0) Spacer(Modifier.height(Space.s))
                Text(line, style = Type.caption.copy(color = Nur.textSecondary))
            }
            Spacer(Modifier.height(Space.m))
            Text(
                "tanzil.net",
                style = Type.caption.copy(color = LocalAura.current.accent),
                modifier = Modifier.pressable { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://tanzil.net")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }

        Spacer(Modifier.height(Space.xxl))
        Text(
            "Adhkaar ${BuildConfig.VERSION_NAME}\n" + stringResource(R.string.settings_footer_tagline),
            style = Type.caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(112.dp))
    }

    if (bedtimePicker) {
        TimeDialog(settings.bedtimeMinute.coerceAtLeast(0), onDismiss = { bedtimePicker = false }) { minute ->
            bedtimePicker = false
            updateSettings(context) { it.copy(bedtimeMinute = minute) }
        }
    }

    if (fridayPicker) {
        TimeDialog(settings.fridayReminderMinute, onDismiss = { fridayPicker = false }) { minute ->
            fridayPicker = false
            updateSettings(context) { it.copy(fridayReminderMinute = minute) }
        }
    }

    if (languageDialog) {
        val current = Languages.current(context)
        GlassDialog(stringResource(R.string.settings_language), onDismiss = { languageDialog = false }) {
            // Each language in its own script, so anyone can find theirs; its English name beside it.
            val options = listOf(Languages.Language("", stringResource(R.string.settings_language_system), "")) + Languages.all
            options.forEachIndexed { i, language ->
                val tag = language.tag.ifEmpty { null }
                if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.07f))
                LanguageOption(language, selected = tag == current) {
                    languageDialog = false
                    if (tag != current) {
                        Languages.set(context, tag)
                        // Widgets draw in the app's language; nothing else tells them it changed.
                        org.adhkaar.app.widget.AdhkaarWidget.refresh(context)
                        // Android 13+ restarts the screen itself; older phones need a nudge.
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) (context as? android.app.Activity)?.recreate()
                    }
                }
            }
        }
    }

    if (methodDialog) {
        GlassDialog(stringResource(R.string.settings_calc_method), onDismiss = { methodDialog = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                CalcMethod.entries.forEach { method ->
                    SettingsRow(stringResource(method.label), onClick = {
                        updateSettings(context) { it.copy(calcMethod = method) }
                        methodDialog = false
                    }, trailing = { RadioDot(method == settings.calcMethod) })
                }
            }
        }
    }
}

@Composable
private fun ScheduleGroup(type: SessionType, schedule: SessionSchedule, onChange: (SessionSchedule) -> Unit) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    val prayer = stringResource(if (type == SessionType.MORNING) R.string.settings_prayer_fajr else R.string.settings_prayer_asr)
    val aura = Auras.of(type)
    SettingsGroup {
        SettingsRow(
            stringResource(if (type == SessionType.MORNING) R.string.settings_morning_adhkaar else R.string.settings_evening_adhkaar),
            AlarmScheduler.nextTime(context, type)?.let { stringResource(R.string.settings_next_at, formatTime(context, it)) } ?: stringResource(R.string.settings_off),
            if (type == SessionType.MORNING) Icons.Rounded.WbTwilight else Icons.Rounded.NightsStay,
            iconTint = aura.accent,
            trailing = { GlassSwitch(schedule.enabled, { onChange(schedule.copy(enabled = it)) }) },
        )
        if (!schedule.enabled) return@SettingsGroup
        RowDivider()
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.m)) {
            GlassSegmented(listOf(stringResource(R.string.settings_after_prayer, prayer), stringResource(R.string.settings_fixed_time)), if (schedule.mode == TimeMode.PRAYER) 0 else 1) { i ->
                onChange(schedule.copy(mode = if (i == 0) TimeMode.PRAYER else TimeMode.FIXED))
            }
        }
        if (schedule.mode == TimeMode.PRAYER) {
            SettingsRow(
                stringResource(R.string.settings_minutes_after_prayer, prayer),
                null,
                Icons.Rounded.MoreTime,
                iconTint = Nur.textTertiary,
                trailing = {
                    Stepper(
                        stringResource(R.string.settings_minutes_short, schedule.offsetMinutes),
                        onMinus = { onChange(schedule.copy(offsetMinutes = schedule.offsetMinutes - 5)) },
                        onPlus = { onChange(schedule.copy(offsetMinutes = schedule.offsetMinutes + 5)) },
                        canMinus = schedule.offsetMinutes > 0, canPlus = schedule.offsetMinutes < 180,
                    )
                },
            )
        } else {
            // After the prayer, the prayer times always give the time, so a fixed one only matters here.
            SettingsRow(
                stringResource(R.string.settings_time),
                null,
                Icons.Rounded.Schedule,
                iconTint = Nur.textTertiary,
                onClick = { picker = true },
                trailing = {
                    ValueText(
                        formatTime(context, ZonedDateTime.now().withHour(schedule.fixedMinuteOfDay / 60).withMinute(schedule.fixedMinuteOfDay % 60)),
                    )
                },
            )
        }
    }
    if (picker) {
        TimeDialog(schedule.fixedMinuteOfDay, onDismiss = { picker = false }) { minute ->
            picker = false
            onChange(schedule.copy(fixedMinuteOfDay = minute))
        }
    }
}

/**
 * What rings at the session time: the app's chime, or the user's own recorded alert
 * (record → stop → listen, re-record or delete), made with the same recorder as a dua's voice.
 * With "My recording" chosen but nothing recorded yet, the chime plays.
 */
/** How firmly a collection's reminder holds: the same three modes as the morning and evening, where allowed. */
@Composable
private fun CollectionModeRow(id: String, allowed: List<Strictness>, current: Strictness) {
    val context = LocalContext.current
    val labels = allowed.map {
        stringResource(
            when (it) {
                Strictness.GENTLE -> R.string.mode_gentle
                Strictness.FULL_SCREEN -> R.string.mode_full_screen
                Strictness.LOCKDOWN -> R.string.mode_lockdown
            },
        )
    }
    Column(Modifier.padding(start = 64.dp, end = Space.l, bottom = Space.m)) {
        GlassSegmented(labels, allowed.indexOf(current).coerceAtLeast(0)) { i ->
            updateSettings(context) { s -> s.copy(collectionModes = s.collectionModes + (id to allowed[i])) }
        }
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(if (id == "after_salah") R.string.collection_mode_hint_salah else R.string.collection_mode_hint_sleep), style = Type.caption)
    }
}

@Composable
private fun AlertSoundSetting(sound: AlertSound) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var take by remember { mutableStateOf(AlertPlayer.recording(context)) }
    var seconds by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    val playing by RecitationPlayer.playing.collectAsState()
    val accent = LocalAura.current.accent

    fun startRecording() {
        RecitationPlayer.stop()
        recording = recorder.start(Recordings.pending(context, AlertPlayer.RECORDING_ID))
        seconds = 0
    }

    fun stopRecording() {
        recorder.stop()
        recording = false
        // Saved at once: unlike a dua, there's no form to confirm.
        Recordings.commit(context, AlertPlayer.RECORDING_ID)
        take = AlertPlayer.recording(context)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) startRecording()
    }

    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            seconds++
            if (seconds >= ALERT_MAX_SECONDS) stopRecording()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Leaving mid-take throws the take away; a preview stops with the screen.
            if (recorder.isRecording) {
                recorder.stop()
                Recordings.discardPending(context, AlertPlayer.RECORDING_ID)
            }
            val previewing = RecitationPlayer.playing.value
            if (previewing == CHIME_KEY || previewing == Recordings.saved(context, AlertPlayer.RECORDING_ID).path) RecitationPlayer.stop()
        }
    }

    val reRingMinutes = (AlertPolicy.RERING_DELAY_MS / 60_000L).toInt()
    SettingsRow(
        stringResource(R.string.settings_alert_sound),
        pluralStringResource(R.plurals.settings_alert_sound_hint, reRingMinutes, reRingMinutes),
        Icons.Rounded.Alarm,
    )
    Column(Modifier.padding(start = Space.gutter, end = Space.gutter, bottom = Space.m)) {
        GlassSegmented(
            listOf(stringResource(R.string.settings_alert_chime), stringResource(R.string.settings_alert_recording)),
            if (sound == AlertSound.CHIME) 0 else 1,
        ) { i -> updateSettings(context) { it.copy(alertSound = if (i == 0) AlertSound.CHIME else AlertSound.RECORDING) } }
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val file = take
            when {
                recording -> {
                    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
                    )
                    Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Nur.danger))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dua_voice_recording), style = Type.label)
                        Text("%d:%02d".format(seconds / 60, seconds % 60), style = Type.caption.copy(fontFeatureSettings = "tnum"))
                    }
                    RoundAction(Icons.Rounded.Stop, stringResource(R.string.dua_voice_stop), Nur.danger) { stopRecording() }
                }
                sound == AlertSound.CHIME -> {
                    val isPlaying = playing == CHIME_KEY
                    RoundAction(
                        if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) R.string.dua_voice_stop else R.string.dua_voice_play),
                        accent,
                    ) { if (isPlaying) RecitationPlayer.stop() else RecitationPlayer.playRaw(context, R.raw.adhkaar_chime, CHIME_KEY) }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_alert_chime_title), style = Type.label)
                        Text(stringResource(R.string.settings_alert_chime_text), style = Type.caption)
                    }
                }
                file != null -> {
                    val isPlaying = playing == file.path
                    RoundAction(
                        if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) R.string.dua_voice_stop else R.string.dua_voice_play),
                        accent,
                    ) { if (isPlaying) RecitationPlayer.stop() else RecitationPlayer.playFile(file) }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_alert_your_recording), style = Type.label)
                        Text(stringResource(R.string.settings_alert_repeats), style = Type.caption)
                    }
                    RoundAction(Icons.Rounded.Mic, stringResource(R.string.dua_voice_record_again), Nur.textSecondary) { record() }
                    Spacer(Modifier.width(Space.s))
                    RoundAction(Icons.Rounded.DeleteOutline, stringResource(R.string.dua_voice_delete), Nur.textSecondary) {
                        RecitationPlayer.stop()
                        Recordings.delete(context, AlertPlayer.RECORDING_ID)
                        take = null
                    }
                }
                else -> {
                    RoundAction(Icons.Rounded.Mic, stringResource(R.string.dua_voice_record), Nur.danger) { record() }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_alert_record_prompt), style = Type.label)
                        Text(
                            stringResource(if (denied) R.string.dua_voice_mic_denied else R.string.settings_alert_record_example),
                            style = Type.caption.copy(color = if (denied) Nur.danger else Nur.textTertiary),
                        )
                    }
                }
            }
        }
    }
}

/** An alert is a sentence or two; the recording stops by itself after this. */
private const val ALERT_MAX_SECONDS = 30

/** How the chime preview shows up in [RecitationPlayer.playing]. */
private const val CHIME_KEY = "alert_chime"

@Composable
private fun RoundAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .glass(CircleShape, level = 2)
            .pressable(scale = 0.9f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Also used by [PrayerTimesScreen] for fixed prayer times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initialMinuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val accent = LocalAura.current.accent
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    GlassDialog(stringResource(R.string.settings_choose_time), onDismiss) {
        TimePicker(
            state,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = TimePickerDefaults.colors(
                clockDialColor = Color.White.copy(alpha = 0.06f),
                selectorColor = accent,
                containerColor = Color.Transparent,
                clockDialSelectedContentColor = Nur.ink,
                clockDialUnselectedContentColor = Nur.textSecondary,
                timeSelectorSelectedContainerColor = accent.copy(alpha = 0.22f),
                timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.06f),
                timeSelectorSelectedContentColor = Nur.textPrimary,
                timeSelectorUnselectedContentColor = Nur.textSecondary,
                periodSelectorBorderColor = Color.White.copy(alpha = 0.12f),
                periodSelectorSelectedContainerColor = accent.copy(alpha = 0.22f),
                periodSelectorSelectedContentColor = Nur.textPrimary,
                periodSelectorUnselectedContentColor = Nur.textTertiary,
            ),
        )
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.settings_set_time), icon = null) { onConfirm(state.hour * 60 + state.minute) }
    }
}

@Composable
private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.settings_duration_minutes, m)
        m == 0 -> stringResource(R.string.settings_duration_hours, h)
        else -> stringResource(R.string.settings_duration_hours_minutes, h, m)
    }
}

@Composable
private fun LanguageOption(language: Languages.Language, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAura.current.accent
    // Arabic-script names in the Arabic interface font, at the same visual size as the Latin ones.
    val arabicScript = language.tag == "ar" || language.tag == "ur"
    val nameStyle = (if (arabicScript) Type.label.copy(fontFamily = NotoSansArabic, fontSize = 15.sp, letterSpacing = 0.sp) else Type.label)
        .copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) accent else Nur.textPrimary)
    Row(
        Modifier.fillMaxWidth().height(52.dp).pressable(scale = 0.985f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(language.nativeName, style = nameStyle, modifier = Modifier.weight(1f))
        if (language.englishName.isNotEmpty() && language.englishName != language.nativeName) {
            Text(language.englishName, style = Type.caption)
        }
        Box(Modifier.padding(start = Space.m).size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}
