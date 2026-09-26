package org.adhkaar.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarCollection
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.CollectionMode
import org.adhkaar.app.data.CollectionMoments
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.DayEvent
import org.adhkaar.app.data.DayReminder
import org.adhkaar.app.data.DayReminders
import org.adhkaar.app.data.EventKind
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.body
import org.adhkaar.app.data.label
import org.adhkaar.app.data.title
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.PendingSession
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.schedule.Congregation
import org.adhkaar.app.schedule.PrayerClock
import org.adhkaar.app.schedule.PrayerTime
import org.adhkaar.app.schedule.SessionTimeCalculator
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.setup.Requirement
import org.adhkaar.app.ui.components.DayRing
import org.adhkaar.app.ui.components.DualCalendar
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.GlassSurface
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.WeekRings
import org.adhkaar.app.ui.components.formatCountdown
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.components.lateLabel
import org.adhkaar.app.ui.components.opensLabel
import org.adhkaar.app.ui.components.gregorianLabel
import org.adhkaar.app.ui.components.rememberResumeTick
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.library.collectionAura
import org.adhkaar.app.ui.library.shelfIcon
import org.adhkaar.app.ui.reminder.DayReminderSheet
import org.adhkaar.app.ui.reminder.ReminderReading
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** The session the app is "about" right now: the one waiting, else the next one, else by time of day. */
fun heroSession(context: android.content.Context, pending: PendingSession?, now: ZonedDateTime): SessionType =
    pending?.type
        ?: SessionType.entries.mapNotNull { t -> AlarmScheduler.nextTime(context, t, now)?.let { t to it } }.minByOrNull { it.second }?.first
        ?: if (now.hour < 12) SessionType.MORNING else SessionType.EVENING

/** Rough duration: ~1.5s per recitation plus ~20s of reading per dhikr. */
fun estimatedMinutes(context: android.content.Context, type: SessionType): Int {
    val items = AdhkaarRepository.forSession(context, type)
    val seconds = items.sumOf { it.count } * 1.5 + items.size * 20
    return (seconds / 60).toInt().coerceAtLeast(1)
}

/**
 * [onOpenCollection] says a session collection (after salah, before sleep, on waking);
 * [onOpenShelf] opens a reference collection (everyday duas) as its list in the Adhkaar tab;
 * [onOpenPrayerTimes] opens the prayer times, from the card or any of its pills.
 */
@Composable
fun TodayScreen(
    onOpenSetup: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenShelf: (String) -> Unit,
    onOpenPrayerTimes: () -> Unit,
) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val state = remember { SessionState.get(context) }
    // A test session is not today's session: the home screen carries on as if it weren't there.
    val pending = state.pendingFlow.collectAsState().value?.takeUnless { it.test }
    val completed by state.completedFlow.collectAsState()
    val history by state.historyFlow.collectAsState()
    val resumeTick = rememberResumeTick()
    val now by produceState(ZonedDateTime.now(), resumeTick) {
        while (true) {
            value = ZonedDateTime.now()
            delay(20_000)
        }
    }
    val today = now.toLocalDate()
    val next = remember(settings, completed, today, resumeTick) {
        SessionType.entries.associateWith { AlarmScheduler.nextTime(context, it) }
    }
    val missing = remember(settings.strictness, resumeTick) { Requirement.missingRequired(context, settings.strictness) }
    val hero = pending?.type ?: remember(next, now) { heroSession(context, null, now) }
    val streak = Streaks.current(history, today)
    var showStreak by rememberSaveable { mutableStateOf(false) }
    val times = remember(history) { state.completionTimes() }
    val hijri = remember(today, settings) { MoonSighting.hijriFor(context, today, settings) }
    val events = remember(today, hijri) { IslamicCalendar.eventsFor(today, hijri) }
    val prayers = remember(settings) { PrayerClock.day(settings) }
    // Where each session stands against its window: a manual start is only offered inside it.
    val windows = remember(settings, now) { SessionType.entries.associateWith { AdhkaarWindows.status(settings, it, now) } }
    val collections = remember { CollectionsRepository.all(context) }
    // After-salah suggestions follow when the masjid prays, not the reminder to get ready.
    val congregation = remember(settings) { Congregation.day(settings) }
    val moment = remember(prayers, congregation, now, settings.bedtimeMinute) {
        CollectionMoments.at(now.toLocalTime(), prayers, congregation, settings.bedtimeMinute)
    }
    // When nothing fits right now, what comes next and when.
    val upcoming = remember(congregation, now, settings.bedtimeMinute, moment) {
        if (moment != null) null else CollectionMoments.next(now.toLocalTime(), congregation, settings.bedtimeMinute)
    }
    // The same reminder the calendar shows for today.
    val dayReminder = remember(today, hijri) { DayReminders.forDate(DayReminders.all(context), today, hijri) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    val openCollection = { c: AdhkaarCollection -> if (c.mode == CollectionMode.SESSION) onOpenCollection(c.id) else onOpenShelf(c.id) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(gregorianLabel(context, today).uppercase(), style = Type.overline)
                Spacer(Modifier.height(2.dp))
                Text(IslamicCalendar.label(context, hijri), style = Type.caption)
            }
            if (streak > 0) {
                val streakDescription = stringResource(R.string.streak_open_cd)
                GlassPill(
                    "$streak", color = Nur.gold, icon = Icons.Rounded.LocalFireDepartment,
                    modifier = Modifier
                        .pressable(shape = RoundedCornerShape(50)) { showStreak = true }
                        .semantics { contentDescription = streakDescription },
                )
            }
        }
        val fajr = prayers.first { it.name == "Fajr" }.time
        val maghrib = prayers.first { it.name == "Maghrib" }.time
        val doneLabel = stringResource(R.string.today_status_done)
        val (morningEnd, eveningEnd) = SessionType.entries.map { type ->
            SkyEnd(
                label = stringResource(if (type == SessionType.MORNING) R.string.today_morning else R.string.today_evening),
                detail = if (completed[type] == today) doneLabel else sessionClock(context, settings, type, now),
                done = completed[type] == today,
            )
        }
        DaySky(
            progress = dayProgress(now.toLocalTime(), fajr, maghrib),
            morning = morningEnd,
            evening = eveningEnd,
        )
        Spacer(Modifier.height(Space.l))
        Greeting()
        Spacer(Modifier.height(Space.xl))

        // In the order of the day: an after-salah (or other) moment that comes before the session
        // sits above it, so after Asr is prayed its adhkaar come first, then the evening adhkaar.
        val sessionAt = next[hero]
        val momentFirst = pending == null && sessionAt != null && (moment != null || upcoming?.let { (_, at) -> now.with(at).isBefore(sessionAt) } == true)
        val momentCards = @Composable {
            moment?.let { m ->
                collections.firstOrNull { it.id == m.collectionId }?.let { c ->
                    MomentCard(c, m, Modifier.padding(top = Space.m).testTag("moment")) { openCollection(c) }
                }
            }
            upcoming?.let { (m, at) ->
                collections.firstOrNull { it.id == m.collectionId }?.let { c ->
                    MomentCard(c, m, Modifier.padding(top = Space.m).testTag("moment"), startsAt = formatTime(context, now.with(at))) { openCollection(c) }
                }
            }
        }
        if (momentFirst) {
            momentCards()
            Spacer(Modifier.height(Space.m))
            HeroCard(hero, pending, sessionAt, now, settings, windows.getValue(hero))
        } else {
            HeroCard(hero, pending, sessionAt, now, settings, windows.getValue(hero))
            momentCards()
        }

        DayReminderCard(dayReminder, IslamicCalendar.label(context, hijri), Modifier.padding(top = Space.m).testTag("day_reminder")) { showReminder = true }

        if (events.isNotEmpty()) EventsCard(events, Modifier.padding(top = Space.m).testTag("events"))

        AnimatedVisibility(
            visible = missing.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SetupNotice(missing, settings.strictness, Modifier.padding(top = Space.m), onOpenSetup)
        }

        SectionHeader(stringResource(R.string.today_section_prayer_times), nextPrayerLabel(context, prayers, now))
        Box(Modifier.testTag("prayers")) { PrayerTimesCard(prayers, now, onOpen = onOpenPrayerTimes) }
        // Said plainly under the times, so they aren't taken for the exact moment each prayer begins.
        Text(stringResource(R.string.today_prayer_times_caption), style = Type.caption, modifier = Modifier.padding(top = Space.s))

        SectionHeader(stringResource(R.string.today_section_collections), null)
        CollectionGrid(collections, onOpen = openCollection)

        SectionHeader(
            stringResource(R.string.today_section_this_week),
            if (streak > 0) pluralStringResource(R.plurals.today_streak_days, streak, streak) else stringResource(R.string.today_start_streak),
        )
        // No legend here: the calendar right below carries the same key, plus the late mark.
        GlassCard(Modifier.fillMaxWidth()) {
            WeekRings(weekOf(today, history))
        }

        SectionHeader(stringResource(R.string.insights_section_calendar), null)
        DualCalendar(history, times, settings, today)

        SectionHeader(stringResource(R.string.today_section_today), null)
        // Equal heights, so the status pills line up when one subtitle wraps.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SessionType.entries.forEach { type ->
                val time = next[type]
                val window = windows.getValue(type)
                val done = completed[type] == today
                // A waiting session can always be continued; otherwise only inside its window.
                val opens = if (pending?.type == type) null else opensLabel(context, window, now)
                val status = when {
                    done -> Status(stringResource(R.string.today_status_done), Nur.success)
                    pending?.type == type -> Status(stringResource(R.string.today_status_waiting), Nur.gold)
                    !settings.schedule(type).enabled -> Status(stringResource(R.string.today_status_off), Nur.textTertiary)
                    window is AdhkaarWindows.Status.Late -> Status(stringResource(R.string.window_late), Auras.of(type).accent)
                    time?.toLocalDate() == today -> Status(stringResource(R.string.today_status_upcoming), Nur.textTertiary)
                    else -> Status(stringResource(R.string.today_status_not_done), Nur.danger)
                }
                SessionTile(
                    type = type,
                    subtitle = when {
                        opens != null && !done -> opens
                        !settings.schedule(type).enabled -> stringResource(R.string.today_turned_off)
                        else -> todayTime(context, settings, type, now)
                    },
                    status = status,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = if (opens == null) ({ SessionLauncher.startManual(context, type) }) else null,
                )
            }
        }
        Spacer(Modifier.height(Space.tabBarClearance))
    }

    if (showStreak) StreakSheet(history, today) { showStreak = false }
    if (showReminder) DayReminderSheet(today) { showReminder = false }
}

@Composable
private fun Greeting() {
    val accent = LocalAura.current.accent
    val rtlScript = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language in setOf("ar", "ur")
    // The salam is the greeting: in Arabic script for Arabic and Urdu, otherwise in Latin letters.
    // Its second word takes the accent (italic only in Latin; Arabic script has no italic).
    val salam = stringResource(if (rtlScript) R.string.day_design_salam_arabic else R.string.day_design_salam)
    val split = salam.lastIndexOf(' ')
    BasicText(
        buildAnnotatedString {
            append(salam.substring(0, split + 1))
            withStyle(SpanStyle(fontStyle = if (rtlScript) FontStyle.Normal else FontStyle.Italic, color = accent)) { append(salam.substring(split + 1)) }
        },
        style = Type.displayL.copy(color = Nur.textPrimary),
        maxLines = 1,
        // One line always: the Arabic script's wider glyphs shrink to fit rather than wrap.
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = Type.displayL.fontSize),
    )
}

@Composable
private fun HeroCard(
    type: SessionType,
    pending: PendingSession?,
    time: ZonedDateTime?,
    now: ZonedDateTime,
    settings: AppSettings,
    window: AdhkaarWindows.Status,
) {
    val context = LocalContext.current
    val minutes = remember(type) { estimatedMinutes(context, type) }
    val count = remember(type) { AdhkaarRepository.forSession(context, type).size }
    val name = stringResource(if (type == SessionType.MORNING) R.string.today_hero_morning else R.string.today_hero_evening)
    val day = time?.toLocalDate() ?: now.toLocalDate()
    // The session's own window: opens after the prayer has been prayed, best until it ends.
    val bounds = remember(settings, type, day) { AdhkaarWindows.window(settings, type, day, now.zone) }
    // A waiting session can always be continued; otherwise the button waits for the window.
    val opens = if (pending != null) null else opensLabel(context, window, now)

    val canStart = pending != null || window.canStart
    val detail = listOf(
        pluralStringResource(R.plurals.today_adhkaar_count, count, count),
        pluralStringResource(R.plurals.today_about_minutes, minutes, minutes),
    ).joinToString(" · ")

    GlassCard(Modifier.fillMaxWidth(), radius = Radius.hero) {
        // Plain language, top to bottom: which session, when, then one sentence for its window.
        Text((if (pending != null) stringResource(R.string.today_hero_waiting) else name).uppercase(), style = Type.overline)
        Spacer(Modifier.height(Space.m))
        when {
            canStart -> Text(stringResource(R.string.today_hero_its_time), style = Type.displayL)
            time == null -> Text(stringResource(R.string.today_hero_not_scheduled), style = Type.displayL)
            // Before its window: the reminder's time, big, so it can't be mistaken for a countdown.
            else -> Text(formatTime(context, time), style = Type.countdown, maxLines = 1)
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            when {
                window is AdhkaarWindows.Status.Late -> lateLabel(context, window)!! + " · " + detail
                canStart -> stringResource(R.string.hero_best_until, formatTime(context, bounds.idealEnd)) + " · " + detail
                time != null -> stringResource(R.string.hero_reminder_in, formatCountdown(context, now, time)) + " · " + detail
                else -> detail
            },
            style = Type.bodyM,
        )
        if (!canStart) {
            Spacer(Modifier.height(Space.l))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Spacer(Modifier.height(Space.l))
            Text(
                stringResource(R.string.hero_best_read_times, formatTime(context, bounds.opens), formatTime(context, bounds.idealEnd)),
                style = Type.bodyM.copy(color = Nur.textSecondary),
            )
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            opens ?: stringResource(if (pending != null) R.string.today_continue else R.string.today_begin_now),
            icon = if (opens == null) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Schedule,
            enabled = opens == null,
        ) { SessionLauncher.startManual(context, type) }
    }
}

private fun eventIcon(kind: EventKind): ImageVector = when (kind) {
    EventKind.JUMUAH -> Icons.Rounded.Mosque
    EventKind.WHITE_DAYS -> Icons.Rounded.Brightness2
    EventKind.RAMADAN -> Icons.Rounded.NightsStay
    EventKind.LAST_TEN_NIGHTS -> Icons.Rounded.AutoAwesome
    EventKind.DHUL_HIJJAH_TEN, EventKind.ARAFAH -> Icons.Rounded.Landscape
    EventKind.EID_AL_FITR, EventKind.EID_AL_ADHA -> Icons.Rounded.Celebration
    EventKind.TASUA, EventKind.ASHURA -> Icons.Rounded.EventAvailable
}

/** One card for the day's special occasions, each as a row. */
@Composable
private fun EventsCard(events: List<DayEvent>, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_events_overline), style = Type.overline.copy(color = Nur.gold))
        events.forEachIndexed { i, event ->
            Spacer(Modifier.height(if (i == 0) Space.m else Space.l))
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                Spacer(Modifier.height(Space.l))
            }
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(eventIcon(event.kind), tint = Nur.gold, size = 36.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(event.title(LocalContext.current), style = Type.label)
                    Spacer(Modifier.height(2.dp))
                    Text(withHonorifics(event.body(LocalContext.current)), style = Type.bodyM)
                    Spacer(Modifier.height(Space.xs))
                    Text(event.reference, style = Type.caption)
                }
            }
        }
    }
}

/** Longer Arabic is left to the sheet, where it has room; on the card it would push the meaning down. */
private const val CARD_ARABIC_MAX_CHARS = 90

/** Past this the meaning is finished in the sheet; five lines keep Today's rhythm. */
private const val CARD_MEANING_MAX_LINES = 5

/**
 * Today's verse or hadith, as the calendar shows it, typeset like a quotation on a page lit from
 * above: the day in gold under the overline, the words centred, a gold ornament, the source.
 * Tapping it, or the share button, opens it in full to share or save.
 */
@Composable
internal fun DayReminderCard(reminder: DayReminder, hijriDate: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    GlassSurface(modifier.fillMaxWidth(), shape, onClick = onClick) {
        // A soft light from the top edge, warm like the gold it frames, gone by the middle.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to Nur.gold.copy(alpha = 0.10f), 1f to Color.Transparent,
                            center = Offset(size.width / 2, 0f), radius = size.width * 0.75f,
                        ),
                    )
                },
        )
        Column(Modifier.padding(Space.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_day_reminder).uppercase(), style = Type.overline)
                    Spacer(Modifier.height(Space.xs))
                    Text(hijriDate.uppercase(), style = Type.overline.copy(color = Nur.gold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(Space.m))
                QuietShareButton(onClick)
            }
            Spacer(Modifier.height(Space.xxl))
            ReminderReading(reminder, CARD_ARABIC_MAX_CHARS, CARD_MEANING_MAX_LINES)
            Spacer(Modifier.height(Space.s))
        }
    }
}

/**
 * A 36dp glass circle holding the share icon. It is laid out at the size it is drawn, so the
 * header keeps its height, while its touch area reaches the full 48dp around it.
 */
@Composable
private fun QuietShareButton(onClick: () -> Unit) {
    val label = stringResource(R.string.share_action)
    Box(
        Modifier
            .layout { measurable, _ ->
                val target = 48.dp.roundToPx()
                val drawn = 36.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(target, target))
                layout(drawn, drawn) { placeable.place((drawn - target) / 2, (drawn - target) / 2) }
            }
            .pressable(shape = CircleShape, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).glass(CircleShape, level = 2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.IosShare, null, tint = Nur.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

/** The next prayer today, or tomorrow's Fajr once Isha has passed (sunrise isn't a prayer). */
/**
 * The salah under way: from its time until [PRAYER_ACTIVE_MINUTES] later, which covers getting
 * ready, the congregation 20 minutes in, and the prayer itself.
 */
private fun activePrayer(prayers: List<PrayerTime>, now: ZonedDateTime): PrayerTime? {
    val t = now.toLocalTime()
    return prayers.filter { it.name != "Sunrise" }.firstOrNull { !t.isBefore(it.time) && t.isBefore(it.time.plusMinutes(PRAYER_ACTIVE_MINUTES)) }
}

private const val PRAYER_ACTIVE_MINUTES = 30L

private fun nextPrayer(prayers: List<PrayerTime>, now: ZonedDateTime): PrayerTime =
    prayers.filter { it.name != "Sunrise" }.firstOrNull { it.time.isAfter(now.toLocalTime()) } ?: prayers.first()

/** "10h 52m" with the numbers large and the units small, like "32°C". */
/** "Asr in 1h 20m", "Fajr in 9h 35m", or "Asr now". */
private fun nextPrayerLabel(context: android.content.Context, prayers: List<PrayerTime>, now: ZonedDateTime): String {
    activePrayer(prayers, now)?.let { return context.getString(R.string.today_prayer_now, context.getString(prayerNameRes(it.name))) }
    val p = nextPrayer(prayers, now)
    var at = now.with(p.time)
    if (!at.isAfter(now)) at = at.plusDays(1)
    val countdown = formatCountdown(context, now, at)
    val name = context.getString(prayerNameRes(p.name))
    return if (countdown == context.getString(R.string.format_now)) context.getString(R.string.today_prayer_now, name)
    else context.getString(R.string.today_prayer_in, name, countdown)
}

/**
 * Corners of the prayer times card and its pills: tighter than a card's, so the six tall pills
 * read as tiles in a row rather than as capsules.
 */
private val PrayerCardRadius = 20.dp

@Composable
private fun PrayerTimesCard(prayers: List<PrayerTime>, now: ZonedDateTime, onOpen: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAura.current.accent
    val active = activePrayer(prayers, now)
    // While a salah is under way it stays lit as "Now"; otherwise the next one is.
    val next = active ?: nextPrayer(prayers, now)
    // Like an hourly forecast: one glass pill per time, the next prayer raised and lit.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        prayers.forEach { p ->
            val isNext = p == next
            val past = !isNext && (p.time.isBefore(now.toLocalTime()) || next.time.isBefore(now.toLocalTime()))
            val shape = RoundedCornerShape(PrayerCardRadius)
            Column(
                Modifier
                    .weight(1f)
                    .height(if (isNext) 132.dp else 116.dp)
                    .pressable(shape = shape, onClick = onOpen)
                    .clip(shape)
                    .then(
                        if (isNext) Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f))))
                        else Modifier,
                    )
                    .glass(shape, level = if (isNext) 2 else 1)
                    .padding(vertical = Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    when {
                        isNext && active != null -> stringResource(R.string.today_now_prayer)
                        isNext -> stringResource(R.string.today_next_prayer)
                        else -> stringResource(prayerNameRes(p.name))
                    },
                    style = Type.caption.copy(color = if (isNext) Nur.textPrimary else Nur.textTertiary, fontSize = 11.sp),
                    maxLines = 1,
                )
                Icon(
                    prayerIcon(p.name), null,
                    tint = when {
                        isNext -> accent
                        past -> Nur.textDisabled
                        else -> Nur.textSecondary
                    },
                    modifier = Modifier.size(20.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isNext) Text(stringResource(prayerNameRes(p.name)), style = Type.caption.copy(color = accent, fontSize = 11.sp), maxLines = 1)
                    Text(
                        clockParts(context, now.with(p.time)).first,
                        style = Type.label.copy(
                            fontSize = 13.sp, fontFeatureSettings = "tnum",
                            color = if (past) Nur.textTertiary else Nur.textPrimary,
                        ),
                    )
                    clockParts(context, now.with(p.time)).second?.let {
                        Text(it, style = Type.caption.copy(fontSize = 9.sp, color = Nur.textTertiary))
                    }
                }
            }
        }
    }
}

/** "5:26 AM" → ("5:26", "AM"); 24-hour clocks have no second part. */
private fun clockParts(context: android.content.Context, time: ZonedDateTime): Pair<String, String?> {
    val text = formatTime(context, time)
    val space = text.lastIndexOf(' ')
    return if (space > 0 && text.substring(space + 1).any { it.isLetter() }) text.substring(0, space) to text.substring(space + 1) else text to null
}

/** The display name for a [PrayerTime.name], which stays English as an identifier. */
@androidx.annotation.StringRes
private fun prayerNameRes(name: String): Int = when (name) {
    "Fajr" -> R.string.prayer_fajr
    "Sunrise" -> R.string.prayer_sunrise
    "Dhuhr" -> R.string.prayer_dhuhr
    "Asr" -> R.string.prayer_asr
    "Maghrib" -> R.string.prayer_maghrib
    else -> R.string.prayer_isha
}

private fun prayerIcon(name: String): ImageVector = when (name) {
    "Fajr" -> Icons.Rounded.WbTwilight
    "Sunrise" -> Icons.Rounded.WbSunny
    "Dhuhr" -> Icons.Rounded.LightMode
    "Asr" -> Icons.Rounded.WbCloudy
    "Maghrib" -> Icons.Rounded.Brightness4
    else -> Icons.Rounded.NightsStay
}

@Composable
private fun SetupNotice(missing: List<Requirement>, strictness: Strictness, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.ErrorOutline, tint = Nur.danger)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_setup_title), style = Type.label)
                Text(
                    pluralStringResource(R.plurals.today_setup_body, missing.size, strictnessLabel(strictness), missing.size),
                    style = Type.caption,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nur.textTertiary)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xxl, bottom = Space.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = Type.titleL, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Type.caption)
    }
}


private data class Status(val label: String, val color: Color)

/** A null [onClick] means the session is outside its window: the tile goes quiet and can't be tapped. */
@Composable
private fun SessionTile(type: SessionType, subtitle: String, status: Status, modifier: Modifier, onClick: (() -> Unit)?) {
    val aura = Auras.of(type)
    GlassCard(modifier, onClick = onClick) {
        IconBadge(
            if (type == SessionType.MORNING) Icons.Rounded.WbTwilight else Icons.Rounded.NightsStay,
            tint = if (onClick != null) aura.accent else Nur.textTertiary,
        )
        Spacer(Modifier.height(Space.l))
        Text(stringResource(if (type == SessionType.MORNING) R.string.today_morning else R.string.today_evening), style = Type.titleM)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = Type.caption, maxLines = 2)
        Spacer(Modifier.height(Space.l))
        // Takes up the difference when the tile beside this one is taller.
        Spacer(Modifier.weight(1f))
        GlassPill(status.label, dot = status.color)
    }
}

/** The collection that fits this moment: after a prayer, on waking, or before sleep. */
@Composable
private fun MomentCard(
    collection: AdhkaarCollection,
    moment: CollectionMoments.Moment,
    modifier: Modifier,
    startsAt: String? = null,
    onClick: () -> Unit,
) {
    val aura = collectionAura(collection.id)
    val prayer = moment.afterPrayer?.let { stringResource(R.string.today_collection_after_prayer, stringResource(prayerNameRes(it))) }
    // "After Asr · around 4:15 PM" when it's coming, "After Asr · now" when it's time.
    val overline = when {
        prayer != null && startsAt != null -> stringResource(R.string.moment_around, prayer, startsAt)
        prayer != null -> stringResource(R.string.moment_now, prayer)
        startsAt != null -> stringResource(R.string.moment_tonight_around, startsAt)
        else -> stringResource(R.string.today_collection_now)
    }
    // Named as adhkaar, so it doesn't read as a prayer or a second "up next".
    val title = when (collection.id) {
        CollectionMoments.AFTER_SALAH -> stringResource(R.string.moment_title_after_salah)
        CollectionMoments.BEFORE_SLEEP -> stringResource(R.string.moment_title_before_sleep)
        CollectionMoments.WAKING -> stringResource(R.string.moment_title_waking)
        else -> collection.title
    }
    GlassSurface(modifier.fillMaxWidth(), RoundedCornerShape(Radius.card), onClick = onClick) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(aura.glows[0].copy(alpha = 0.22f), Color.Transparent))))
        Row(Modifier.padding(Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(shelfIcon(collection.id), tint = aura.accent)
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(overline.uppercase(), style = Type.overline.copy(color = aura.accent))
                Spacer(Modifier.height(2.dp))
                Text(title, style = Type.titleM)
                Spacer(Modifier.height(2.dp))
                Text(collection.subtitle, style = Type.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nur.textTertiary)
        }
    }
}

/** Every collection, two to a row, with the same icons and colours as the Adhkaar tab. */
@Composable
private fun CollectionGrid(collections: List<AdhkaarCollection>, onOpen: (AdhkaarCollection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        collections.chunked(2).forEach { row ->
            // Equal heights when one title wraps to two lines.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                row.forEach { c ->
                    GlassCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(Space.l), onClick = { onOpen(c) }) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(shelfIcon(c.id), tint = collectionAura(c.id).accent, size = 36.dp)
                            Spacer(Modifier.width(Space.m))
                            Text(c.title, style = Type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The session's time today, whether or not it has passed. */
private fun todayTime(context: android.content.Context, settings: AppSettings, type: SessionType, now: ZonedDateTime): String =
    context.getString(R.string.today_today_at, sessionClock(context, settings, type, now))

/** Today's time for a session, e.g. "5:41 AM". */
private fun sessionClock(context: android.content.Context, settings: AppSettings, type: SessionType, now: ZonedDateTime): String {
    val time = SessionTimeCalculator.sessionTime(settings.schedule(type), now.toLocalDate(), PrayerClock.provider(settings, type))
    return formatTime(context, now.with(time))
}

/** This week's rings, Sunday first. Shared with the streak sheet. */
internal fun weekOf(today: LocalDate, history: Set<String>): List<DayRing> {
    val start = today.minusDays(((today.dayOfWeek.value % 7)).toLong()) // week starts Sunday
    return (0L..6L).map { i ->
        val day = start.plusDays(i)
        DayRing(
            label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            morning = Streaks.isDone(history, day, SessionType.MORNING),
            evening = Streaks.isDone(history, day, SessionType.EVENING),
            isToday = day == today,
        )
    }
}

@Composable
fun strictnessLabel(s: Strictness) = androidx.compose.ui.res.stringResource(
    when (s) {
        Strictness.GENTLE -> org.adhkaar.app.R.string.mode_gentle
        Strictness.FULL_SCREEN -> org.adhkaar.app.R.string.mode_full_screen
        Strictness.LOCKDOWN -> org.adhkaar.app.R.string.mode_lockdown
    },
)

@Composable
fun strictnessDescription(s: Strictness) = androidx.compose.ui.res.stringResource(
    when (s) {
        Strictness.GENTLE -> org.adhkaar.app.R.string.mode_gentle_description
        Strictness.FULL_SCREEN -> org.adhkaar.app.R.string.mode_full_screen_description
        Strictness.LOCKDOWN -> org.adhkaar.app.R.string.mode_lockdown_description
    },
)
