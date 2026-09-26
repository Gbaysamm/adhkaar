package org.adhkaar.app.ui.session

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.DisposableEffect
import org.adhkaar.app.session.AudioLibrary
import org.adhkaar.app.session.RecitationPlayer
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.share.ShareSheet
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.PaceClock
import org.adhkaar.app.data.Pacing
import org.adhkaar.app.ui.components.Haptics
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.settings.Stepper
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.ResumeState
import org.adhkaar.app.data.SessionDhikr
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.data.UserDuaStore
import org.adhkaar.app.session.AlertPolicy
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.Celebration
import org.adhkaar.app.ui.components.CounterOrb
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.SegmentedProgress
import org.adhkaar.app.ui.components.Stat
import org.adhkaar.app.ui.components.StatRow
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.settings.GlassSwitchRow
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue
import java.time.ZonedDateTime

/** Morning or evening: the session the alarm opens, with breaks and Lockdown. */
@Composable
fun SessionScreen(type: SessionType, onFinish: () -> Unit) {
    val context = LocalContext.current
    val state = remember { SessionState.get(context) }
    val settings by SettingsStore.get(context).flow.collectAsState()
    val pending by state.pendingFlow.collectAsState()
    // The user's own duas for this session come last, after the adhkaar of the Sunnah.
    val items = remember(type) { AdhkaarRepository.forSession(context, type) + UserDuaStore.get(context).forSession(type) }

    // Only when this screen first shows the session: recreated after the process was killed, a
    // session finished meanwhile stays finished (its completion is shown again) rather than restarting.
    var begun by rememberSaveable(type) { mutableStateOf(false) }
    LaunchedEffect(type) {
        if (!begun && state.pending?.type != type) state.start(type, LocalDate.now(), System.currentTimeMillis(), enforced = false)
        begun = true
    }

    // Breaks belong to a session the alarm started; one opened by hand can simply be closed.
    val enforced = pending?.takeIf { it.enforced }
    val morning = type == SessionType.MORNING
    DhikrSession(
        key = type.key,
        overline = stringResource(if (morning) R.string.session_overline_morning else R.string.session_overline_evening),
        items = items,
        initialProgress = state.progress(),
        onProgress = { state.saveProgress(it) },
        // The page kept belongs to the pending session; one about to be started opens as usual.
        initialPage = if (state.pending?.type == type) state.page() else -1,
        onPage = { state.savePage(it) },
        showClose = pending?.test == true || !SessionLauncher.isLockdownActive(context),
        // Closing keeps the progress saved for later, so there is nothing to confirm.
        closeConfirmTitle = null,
        bottomControls = if (enforced?.test == true) {
            {
                // A test can be ended at any time; it records nothing.
                Box(Modifier.fillMaxWidth().height(48.dp).pressable { SessionLauncher.endTest(context); onFinish() }, contentAlignment = Alignment.Center) {
                    GlassPill(stringResource(R.string.session_end_test), icon = Icons.Rounded.Close)
                }
            }
        } else if (enforced == null) null else {
            {
                BreakControl(
                    AlertPolicy.windowEndMillis(enforced, SessionLauncher.windowMinutes(context, enforced)),
                    remaining = { AlertPolicy.remaining(items.map { it.id to it.count }, state.progress()) },
                    streak = { Streaks.current(state.historyFlow.value, LocalDate.now()) },
                ) { minutes ->
                    SessionLauncher.takeBreak(context, minutes)
                    onFinish()
                }
            }
        },
        onComplete = {
            val started = state.pending?.startedAtMillis ?: System.currentTimeMillis()
            SessionLauncher.complete(context, type)
            Summary(
                adhkaar = items.size,
                minutes = ((System.currentTimeMillis() - started) / 60_000L).toInt().coerceAtLeast(1),
                streak = Streaks.current(state.historyFlow.value, LocalDate.now()),
            )
        },
        completion = { summary -> CompletionView(type, summary, onDone = onFinish) },
        onFinish = onFinish,
    )
}

/**
 * The shared session experience: one dhikr per page, tap to count, auto-advance, then a
 * completion moment. Morning/evening and every collection use it.
 *
 * [initialProgress]: the counts kept by the caller, or null when they are kept nowhere else; they
 * are then saved with the screen, so they survive Android killing the process in the background.
 * [initialPage]: the page last shown (-1 for none) and [onPage] keeps it, for a caller that keeps
 * progress; the screen's own saved state restores the page either way.
 * [closeConfirmTitle]: when set, closing part-way (close button or back) asks first, with this title.
 */
@Composable
fun DhikrSession(
    key: String,
    overline: String,
    items: List<SessionDhikr>,
    initialProgress: Map<String, Int>?,
    onProgress: (Map<String, Int>) -> Unit,
    initialPage: Int,
    onPage: (Int) -> Unit,
    showClose: Boolean,
    closeConfirmTitle: String?,
    bottomControls: (@Composable () -> Unit)?,
    onComplete: () -> Summary,
    completion: @Composable (Summary) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore.get(context) }
    val settings by settingsStore.flow.collectAsState()
    val state = remember { SessionState.get(context) }
    val favorites by state.favoritesFlow.collectAsState()

    // Counts the caller keeps are read afresh from it, so a session restarted meanwhile never
    // picks up stale counts from the saved screen.
    val counts = if (initialProgress != null) {
        remember(key) { mutableStateMapOf<String, Int>().apply { putAll(initialProgress) } }
    } else {
        rememberSaveable(key, saver = CountsSaver) { mutableStateMapOf() }
    }
    var summary by rememberSaveable(key) { mutableStateOf<Summary?>(null) }
    var hasTapped by remember(key) { mutableStateOf(counts.isNotEmpty()) }

    fun isDone(d: SessionDhikr) = (counts[d.id] ?: 0) >= d.count
    fun nextIncomplete(from: Int): Int? =
        (items.indices.drop(from + 1) + items.indices.take(from + 1)).firstOrNull { !isDone(items[it]) }

    val pager = rememberPagerState(initialPage = ResumeState.startPage(initialPage, items.size, nextIncomplete(-1))) { items.size }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }

    // Recitation follows the page: stop when moving on, auto-play if chosen; silence on leaving.
    LaunchedEffect(pager.currentPage) {
        RecitationPlayer.stop()
        val id = items[pager.currentPage].audioId
        if (settings.autoPlayRecitation && AudioLibrary.has(context, id)) RecitationPlayer.play(context, id)
    }
    DisposableEffect(key) { onDispose { RecitationPlayer.stop() } }

    // Each count waits at least as long as the words take to say, so a dhikr can't be tapped
    // through. The wait is timed from when its page is shown or from its last count.
    val pace = remember(key) { PaceClock(items.associate { it.id to Pacing.minMillisPerCount(it.arabic, it.translation) }) }
    val readiness = remember(key) { Animatable(1f) }
    val current = items[pager.currentPage]
    val currentCount = counts[current.id] ?: 0
    LaunchedEffect(current.id, currentCount) {
        val now = SystemClock.elapsedRealtime()
        pace.show(current.id, now)
        readiness.snapTo(pace.readiness(current.id, now))
        readiness.animateTo(1f, tween(pace.remaining(current.id, now).toInt(), easing = LinearEasing))
    }
    // A too-early tap: a gentle word under the orb that fades away. Each one shows it afresh.
    var tooSoon by remember(key) { mutableIntStateOf(0) }
    val gentle = remember(key) { Animatable(0f) }
    LaunchedEffect(tooSoon) {
        if (tooSoon == 0) return@LaunchedEffect
        gentle.animateTo(1f, tween(200))
        delay(1_600)
        gentle.animateTo(0f, tween(700, easing = Motion.emphasized))
    }

    fun tap(index: Int) {
        val d = items[index]
        val c = counts[d.id] ?: 0
        if (c >= d.count) {
            nextIncomplete(index)?.let { scope.launch { pager.animateScrollToPage(it) } }
            return
        }
        if (!pace.count(d.id, SystemClock.elapsedRealtime())) {
            Haptics.reject(view)
            tooSoon++
            return
        }
        hasTapped = true
        counts[d.id] = c + 1
        onProgress(counts.toMap())
        if (c + 1 < d.count) {
            Haptics.tap(view)
            return
        }
        val next = nextIncomplete(index)
        if (next == null) {
            Haptics.success(view)
            val result = onComplete()
            scope.launch { delay(350); summary = result }
        } else {
            Haptics.confirm(view)
            if (settings.autoAdvance) scope.launch { delay(450); pager.animateScrollToPage(next, animationSpec = Motion.enter()) }
        }
    }

    // Leaving part-way loses the counts, so ask first; with nothing counted or all said, just close.
    val remaining = items.count { !isDone(it) }
    val askBeforeClosing = closeConfirmTitle != null && counts.values.any { it > 0 } && remaining > 0
    var confirmingClose by rememberSaveable(key) { mutableStateOf(false) }
    fun close() {
        if (askBeforeClosing) confirmingClose = true else onFinish()
    }
    BackHandler(enabled = askBeforeClosing) { confirmingClose = true }
    if (confirmingClose && closeConfirmTitle != null) {
        LeaveDialog(
            title = closeConfirmTitle,
            remaining = remaining,
            total = items.size,
            onStay = { confirmingClose = false },
            onLeave = {
                confirmingClose = false
                onFinish()
            },
        )
    }

    AuraBackground(Modifier.fillMaxSize(), intensity = 0.8f) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(
                overline, pager.currentPage, items.size, showClose = showClose,
                settings = settings,
                onChange = { transform -> settingsStore.update(transform) },
                onClose = { close() },
            )
            SegmentedProgress(
                items.map { isDone(it) }, pager.currentPage,
                Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m),
            )
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = Space.gutter),
                pageSpacing = Space.m,
                modifier = Modifier.weight(1f).padding(top = Space.xs),
            ) { page ->
                DhikrPanel(
                    dhikr = items[page],
                    count = counts[items[page].id] ?: 0,
                    settings = settings,
                    favorite = items[page].id in favorites,
                    onToggleFavorite = { state.toggleFavorite(items[page].id) },
                    modifier = Modifier.pageEffect(pager, page),
                    onTap = { tap(page) },
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OrbWithRecitation(
                    audioId = current.audioId.takeIf { AudioLibrary.has(context, it) },
                ) {
                    CounterOrb(
                        currentCount, current.count,
                        readiness = { readiness.value },
                        onTap = { tap(pager.currentPage) },
                    )
                }
                // Fixed-height hint slot so the counter never shifts between pages. The gentle
                // word after a too-early tap takes the same place, over the first-time hint.
                Box(Modifier.padding(top = Space.m).height(20.dp), contentAlignment = Alignment.Center) {
                    val hint by animateFloatAsState(if (hasTapped) 0f else 1f, tween(500), label = "hint")
                    Text(stringResource(R.string.session_tap_hint), style = Type.caption, modifier = Modifier.graphicsLayer { alpha = hint * (1f - gentle.value) })
                    Text(stringResource(R.string.pacing_take_your_time), style = Type.caption, modifier = Modifier.graphicsLayer { alpha = gentle.value })
                }
                bottomControls?.invoke()
            }
        }

        AnimatedVisibility(visible = summary != null, enter = fadeIn(tween(600)), exit = fadeOut()) {
            summary?.let { completion(it) }
        }
    }
}

/**
 * A null [Summary.streakLabel] means "day streak", resolved from resources when shown.
 * Serializable so the completion moment is saved with the screen and survives the process being killed.
 */
data class Summary(val adhkaar: Int, val minutes: Int, val streak: Int, val streakLabel: String? = null) : Serializable

/** Saves a collection's counts with the screen; a HashMap goes into the saved-state bundle as is. */
private val CountsSaver = Saver<SnapshotStateMap<String, Int>, HashMap<String, Int>>(
    save = { HashMap(it) },
    restore = { mutableStateMapOf<String, Int>().apply { putAll(it) } },
)

/** Asked before leaving part-way. Staying is the highlighted choice; leaving is the quiet one. */
@Composable
internal fun LeaveDialog(title: String, remaining: Int, total: Int, onStay: () -> Unit, onLeave: () -> Unit) {
    val view = LocalView.current
    GlassDialog(title, onDismiss = onStay) {
        Text(stringResource(R.string.pacing_remaining, remaining, total), style = Type.bodyM.copy(color = Nur.textSecondary))
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(stringResource(R.string.pacing_keep_going), icon = null, onClick = onStay)
        Spacer(Modifier.height(Space.s))
        SecondaryButton(stringResource(R.string.pacing_leave)) {
            Haptics.confirm(view)
            onLeave()
        }
    }
}

/**
 * For a busy moment: a quiet pill that asks how long, and the time the adhkaar must be done by.
 * Only breaks that end before the window closes ([windowEndMillis]) are offered, so a break
 * postpones the adhkaar but never drops them. Choosing a time asks once more, gently, with how
 * little is left and why it matters; staying is the main action at every step.
 */
@Composable
internal fun BreakControl(
    windowEndMillis: Long,
    remaining: () -> Int,
    streak: () -> Int,
    onBreak: (minutes: Int) -> Unit,
) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val doneBy = stringResource(
        R.string.break_done_by,
        formatTime(context, Instant.ofEpochMilli(windowEndMillis).atZone(ZoneId.systemDefault())),
    )
    Column(
        Modifier.fillMaxWidth().padding(top = Space.s, start = Space.gutter, end = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A 48dp touch target around the small pill.
        Box(Modifier.height(48.dp).pressable { choosing = true }, contentAlignment = Alignment.Center) {
            GlassPill(stringResource(R.string.break_take), icon = Icons.Rounded.Pause)
        }
        Text(doneBy, style = Type.caption)
    }
    val close = { choosing = false; confirming = false; chosen = null }
    if (choosing && !confirming) {
        // Read when the dialog opens, so the choices fit the time actually left.
        val options = remember { AlertPolicy.breakOptions(System.currentTimeMillis(), windowEndMillis) }
        val left = remember { remaining() }
        GlassDialog(stringResource(R.string.break_title), onDismiss = close) {
            Text(
                if (options.isEmpty()) stringResource(R.string.break_no_time) else pluralStringResource(R.plurals.break_left, left, left),
                style = Type.bodyM.copy(color = Nur.textSecondary),
            )
            Spacer(Modifier.height(Space.m))
            GlassPill(doneBy, icon = Icons.Rounded.Schedule)
            if (options.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                options.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        pair.forEach { minutes ->
                            BreakChip(minutes, selected = chosen == minutes, Modifier.weight(1f)) {
                                chosen = minutes
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.s))
                }
                val back = chosen?.let { formatTime(context, ZonedDateTime.now().plusMinutes(it.toLong())) }
                Text(
                    back?.let { stringResource(R.string.break_back_at, it) } ?: stringResource(R.string.break_rings_again),
                    style = Type.caption,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(stringResource(R.string.break_keep_reading), Modifier.fillMaxWidth(), icon = null, onClick = close)
            if (options.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                QuietButton(
                    chosen?.let { breakLength(it) }?.let { stringResource(R.string.break_pause_for, it) } ?: stringResource(R.string.break_pick_time),
                    enabled = chosen != null,
                ) { confirming = true }
            }
        }
    }
    if (confirming) {
        val minutes = chosen ?: return
        val left = remember { remaining() }
        val days = remember { streak() }
        GlassDialog(stringResource(R.string.break_confirm_title), onDismiss = close) {
            Text(pluralStringResource(R.plurals.break_confirm_left, left, left), style = Type.bodyL)
            if (days > 1) {
                Spacer(Modifier.height(Space.s))
                Text(pluralStringResource(R.plurals.break_confirm_streak, days, days), style = Type.bodyM.copy(color = Nur.textSecondary))
            }
            Spacer(Modifier.height(Space.l))
            // The reminder the whole app is built on: remembrance is what gives the heart life.
            GlassCard(Modifier.fillMaxWidth(), level = 2) {
                Text(
                    "مَثَلُ الَّذِي يَذْكُرُ رَبَّهُ وَالَّذِي لَا يَذْكُرُ رَبَّهُ مَثَلُ الْحَيِّ وَالْمَيِّتِ",
                    style = Type.arabicAccent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    stringResource(R.string.break_confirm_hadith),
                    style = Type.bodyM,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(R.string.break_confirm_source).uppercase(),
                    style = Type.overline.copy(color = LocalAura.current.accent),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(stringResource(R.string.break_confirm_stay), Modifier.fillMaxWidth(), icon = null) {
                close()
            }
            Spacer(Modifier.height(Space.s))
            QuietButton(stringResource(R.string.break_confirm_pause, breakLength(minutes))) {
                close()
                onBreak(minutes)
            }
        }
    }
}

@Composable
private fun breakLength(minutes: Int) =
    if (minutes % 60 == 0) {
        pluralStringResource(R.plurals.break_option_hours, minutes / 60, minutes / 60)
    } else {
        pluralStringResource(R.plurals.break_option_minutes, minutes, minutes)
    }

/** One break length: the number large, the unit small; the chosen one takes the accent. */
@Composable
private fun BreakChip(minutes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAura.current.accent
    val shape = RoundedCornerShape(20.dp)
    val hours = minutes % 60 == 0
    Column(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), shape)
            .pressable(shape = shape, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            (if (hours) minutes / 60 else minutes).toString(),
            style = Type.titleL.copy(fontFeatureSettings = "tnum", color = if (selected) accent else Nur.textPrimary),
        )
        Text(
            stringResource(if (hours) R.string.break_unit_hour else R.string.break_unit_min),
            style = Type.caption.copy(color = if (selected) accent else Nur.textTertiary),
        )
    }
}

/** A text-only action, for the choice we'd rather people didn't take. */
@Composable
private fun QuietButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label.copy(color = if (enabled) Nur.textSecondary else Nur.textTertiary.copy(alpha = 0.5f)))
    }
}

/** Neighbouring pages recede while swiping. */
private fun Modifier.pageEffect(pager: PagerState, page: Int) = graphicsLayer {
    val offset = pager.getOffsetDistanceInPages(page).absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(1f, 0.92f, offset)
    scaleX = scale
    scaleY = scale
    alpha = lerp(1f, 0.4f, offset)
}

@Composable
private fun TopBar(
    overline: String,
    position: Int,
    total: Int,
    showClose: Boolean,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.iconEdge, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            if (showClose) GlassIconButton(Icons.Rounded.Close, stringResource(R.string.session_close), onClick = onClose)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(overline, style = Type.overline)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.session_position, position + 1, total), style = Type.label.copy(fontFeatureSettings = "tnum"))
        }
        Box {
            GlassIconButton(Icons.Rounded.TextFields, stringResource(R.string.session_reading_options), onClick = { menu = true })
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = RoundedCornerShape(Radius.card),
                containerColor = Color(0xF20C1022),
                modifier = Modifier.width(264.dp),
            ) {
                Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                    Text(stringResource(R.string.session_reading_overline), style = Type.overline)
                    Spacer(Modifier.height(Space.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.session_arabic_size), style = Type.bodyM, modifier = Modifier.weight(1f))
                        Stepper(
                            stringResource(R.string.session_arabic_scale_percent, (settings.arabicScale * 100).roundToInt()),
                            onMinus = { onChange { it.copy(arabicScale = (it.arabicScale - 0.1f).coerceAtLeast(0.8f)) } },
                            onPlus = { onChange { it.copy(arabicScale = (it.arabicScale + 0.1f).coerceAtMost(1.5f)) } },
                            canMinus = settings.arabicScale > 0.85f,
                            canPlus = settings.arabicScale < 1.45f,
                        )
                    }
                    Spacer(Modifier.height(Space.m))
                    GlassSwitchRow(stringResource(R.string.session_transliteration), settings.showTransliteration) { v -> onChange { it.copy(showTransliteration = v) } }
                    Spacer(Modifier.height(Space.m))
                    GlassSwitchRow(stringResource(R.string.session_translation), settings.showTranslation) { v -> onChange { it.copy(showTranslation = v) } }
                    Spacer(Modifier.height(Space.m))
                    GlassSwitchRow(stringResource(R.string.session_auto_advance), settings.autoAdvance) { v -> onChange { it.copy(autoAdvance = v) } }
                    Spacer(Modifier.height(Space.m))
                    GlassSwitchRow(stringResource(R.string.session_auto_play_recitation), settings.autoPlayRecitation) { v -> onChange { it.copy(autoPlayRecitation = v) } }
                    Spacer(Modifier.height(Space.m))
                    GlassSwitchRow(stringResource(R.string.session_haptics), settings.haptics) { v -> onChange { it.copy(haptics = v) } }
                }
            }
        }
    }
}

@Composable
private fun DhikrPanel(
    dhikr: SessionDhikr,
    count: Int,
    settings: AppSettings,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val showTransliteration = settings.showTransliteration
    val showTranslation = settings.showTranslation
    val done = count >= dhikr.count
    var sharing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val shape = RoundedCornerShape(Radius.hero)
    Column(
        modifier
            .fillMaxSize()
            .glass(shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(Space.xl),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(dhikr.title, style = Type.label, color = LocalAura.current.accent)
                if (done || dhikr.count > 1) Spacer(Modifier.width(Space.s))
                if (done) GlassPill(stringResource(R.string.session_done), color = Nur.gold, icon = Icons.Rounded.Check) else if (dhikr.count > 1) GlassPill(pluralStringResource(R.plurals.counter_times, dhikr.count, dhikr.count))
            }
            Spacer(Modifier.height(Space.xl))
            if (dhikr.arabic.isBlank()) {
                // A dua written only in the user's language reads as the main text.
                Text(withHonorifics(dhikr.translation), style = Type.displayM, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else Text(
                dhikr.arabic,
                style = Type.arabicReading.copy(
                    fontSize = Type.arabicReading.fontSize * settings.arabicScale * (if (dhikr.arabic.length > 280) 0.86f else 1f),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showTransliteration || showTranslation) {
                Spacer(Modifier.height(Space.xl))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            }
            if (showTransliteration) {
                Spacer(Modifier.height(Space.l))
                Text(
                    dhikr.transliteration, style = Type.bodyM.copy(fontStyle = FontStyle.Italic, color = Nur.textTertiary),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showTranslation && dhikr.arabic.isNotBlank()) {
                Spacer(Modifier.height(Space.m))
                Text(withHonorifics(dhikr.translation), style = Type.bodyL, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            dhikr.virtue?.let {
                Spacer(Modifier.height(Space.l))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(Radius.hero - Space.m), level = 2)
                        .padding(Space.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Nur.gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(Space.s))
                    Text(it, style = Type.caption.copy(color = Nur.textSecondary), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = Nur.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Space.s))
                Text(dhikr.reference, style = Type.caption)
            }
        }
        FavoriteButton(favorite, Modifier.align(Alignment.TopEnd).padding(Space.s), onToggleFavorite)
        ShareButton(Modifier.align(Alignment.TopStart).padding(Space.s)) { sharing = true }
        if (sharing) ShareSheet(dhikr, onDismiss = { sharing = false })
        // Fade at the bottom while there is more to read.
        val fade by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, tween(250), label = "fade")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(bottomStart = Radius.hero, bottomEnd = Radius.hero))
                .graphicsLayer { alpha = fade }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC070A18)))),
        )
    }
    }
}

@Composable
private fun ShareButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(scale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.IosShare, stringResource(R.string.share_action), tint = Nur.textTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (favorite) 1f else 0.9f, Motion.press(), label = "heart")
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(scale = 0.85f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            stringResource(if (favorite) R.string.session_favourite_remove else R.string.session_favourite_add),
            tint = if (favorite) Color(0xFFFF7A8A) else Nur.textTertiary,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
internal fun CompletionView(type: SessionType, summary: Summary, onDone: () -> Unit) {
    val morning = type == SessionType.MORNING
    CompletionView(
        headline = stringResource(if (morning) R.string.completion_morning_headline else R.string.completion_evening_headline),
        quote = stringResource(if (morning) R.string.completion_morning_quote else R.string.completion_evening_quote),
        reference = stringResource(R.string.completion_reference),
        summary = summary,
        onDone = onDone,
    )
}

/** The completion moment: light, gratitude, the promise in the words of the hadith, and the numbers. */
@Composable
internal fun CompletionView(headline: String, quote: String, reference: String, summary: Summary, onDone: () -> Unit) {
    val bloom = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val card = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { bloom.animateTo(1f, tween(1400, easing = Motion.emphasized)) }
        delay(300)
        launch { text.animateTo(1f, tween(700, easing = Motion.emphasized)) }
        delay(250)
        card.animateTo(1f, tween(700, easing = Motion.emphasized))
    }
    AuraBackground(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {},
        intensity = 1.25f,
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            Box(contentAlignment = Alignment.Center) {
                Celebration(Modifier.size(300.dp))
                NurOrb(120.dp, bloom = bloom.value, modifier = Modifier.graphicsLayer { scaleX = 0.8f + 0.2f * bloom.value; scaleY = scaleX })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.rise(text.value)) {
                Text("الحمد لله", style = Type.arabicDisplay.copy(color = Nur.gold))
                Spacer(Modifier.height(Space.s))
                Text(headline, style = Type.displayL, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Space.xl))
            // The promise, in the words of the hadith, so the moment carries meaning.
            Column(
                Modifier
                    .fillMaxWidth()
                    .rise(card.value)
                    .glass(RoundedCornerShape(Radius.card))
                    .padding(Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.completion_quote_format, quote),
                    style = Type.bodyL.copy(fontStyle = FontStyle.Italic, color = Nur.textPrimary),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.s))
                Text(reference, style = Type.caption, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.l))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(Space.l))
                StatRow(
                    listOf(
                        Stat("${summary.adhkaar}", pluralStringResource(R.plurals.completion_stat_adhkaar, summary.adhkaar)),
                        Stat(stringResource(R.string.completion_minutes, summary.minutes), stringResource(R.string.completion_stat_time)),
                        Stat("${summary.streak}", summary.streakLabel ?: pluralStringResource(R.plurals.completion_day_streak, summary.streak)),
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.completion_done), icon = Icons.Rounded.Check, modifier = Modifier.rise(card.value), onClick = onDone)
            Spacer(Modifier.height(Space.l))
        }
    }
}

/** Fade in while rising 16dp into place. */
private fun Modifier.rise(progress: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 16.dp.toPx()
}
