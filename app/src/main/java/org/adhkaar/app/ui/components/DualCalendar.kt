package org.adhkaar.app.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.DayRecord
import org.adhkaar.app.data.HijriDay
import org.adhkaar.app.data.Insights
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.label
import org.adhkaar.app.data.monthName
import org.adhkaar.app.ui.reminder.DayReminderSheet
import org.adhkaar.app.ui.theme.ChartColors
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Which calendar leads: its numbers are large and the grid follows its months. Held app-wide so
 * Today and Insights always agree, and saved so the choice survives restarts. The Hijri calendar
 * leads until the user chooses otherwise.
 */
private object PrimaryCalendar {
    private const val PREFS = "calendar"
    private const val KEY_HIJRI = "hijri_primary"
    private var state: MutableState<Boolean>? = null

    fun hijri(context: Context): MutableState<Boolean> =
        state ?: mutableStateOf(prefs(context).getBoolean(KEY_HIJRI, true)).also { state = it }

    fun setHijri(context: Context, value: Boolean) {
        hijri(context).value = value
        prefs(context).edit().putBoolean(KEY_HIJRI, value).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * One page of the calendar: the days it shows, in order, each with its Hijri date. A Gregorian
 * page is a Gregorian month; a Hijri page runs from the 1st to the 29th or 30th of a Hijri month.
 */
private class CalendarPage(val days: List<LocalDate>, val hijri: List<HijriDay>)

private fun gregorianPage(context: Context, month: YearMonth, settings: AppSettings): CalendarPage {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    return CalendarPage(days, days.map { MoonSighting.hijriFor(context, it, settings) })
}

/**
 * The Hijri month [anchor] falls in. It walks out from the anchor instead of counting back from
 * the day number, because the announced dates and the calculated fallback can disagree at a
 * month's edge; walking keeps every day on the page in the one month.
 */
private fun hijriPage(context: Context, anchor: LocalDate, settings: AppSettings): CalendarPage {
    val month = MoonSighting.hijriFor(context, anchor, settings)
    fun inMonth(date: LocalDate) = MoonSighting.hijriFor(context, date, settings).let { it.month == month.month && it.year == month.year }
    var first = anchor
    while (ChronoUnit.DAYS.between(first, anchor) < 29 && inMonth(first.minusDays(1))) first = first.minusDays(1)
    var last = anchor
    while (ChronoUnit.DAYS.between(first, last) < 29 && inMonth(last.plusDays(1))) last = last.plusDays(1)
    val days = (0L..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) }
    return CalendarPage(days, days.map { MoonSighting.hijriFor(context, it, settings) })
}

/** Space between day cells. Narrow, so two numbers fit a square cell on a 360dp phone. */
private val CellGap = 4.dp

/** Day cells and the legend's swatches share one shape, so the key looks like what it explains. */
private val CellRadius = 10.dp

/** Space between a ringed day's ring and its fill, so the ring reads on any fill colour. */
private val RingGap = 3.dp

/**
 * The month as a calendar in both the Hijri and Gregorian reckonings, each day filled by what was
 * done (morning, evening, both). A switch in the header picks which calendar leads; the other is
 * shown under it in every cell, so both dates are always read. Tapping a day opens its reminder.
 */
@Composable
fun DualCalendar(
    /** Completion history (see Streaks). */
    history: Set<String>,
    /** Completion times, "date|type|minute", for saying which sessions were late. */
    times: Set<String>,
    settings: AppSettings,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val hijriPrimary = PrimaryCalendar.hijri(context).value
    // Any day on the page; the page is the month (of the leading calendar) it falls in.
    var anchorText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    var reminderText by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = LocalDate.parse(anchorText)
    val selected = LocalDate.parse(selectedText)

    val page = remember(anchor, hijriPrimary, settings) {
        if (hijriPrimary) hijriPage(context, anchor, settings) else gregorianPage(context, YearMonth.from(anchor), settings)
    }
    val hijriOf = remember(page) { page.days.zip(page.hijri).toMap() }
    val records = remember(history, page) { page.days.associateWith { Insights.day(history, it) } }
    // A Hijri page spans two Gregorian months, so the late days of each are merged.
    val late = remember(times, page, settings) {
        page.days.map(YearMonth::from).distinct().fold(emptyMap<LocalDate, Set<SessionType>>()) { all, month ->
            all + Insights.lateDays(times, month) { date, type -> AdhkaarWindows.window(settings, type, date) }
        }
    }
    // Back through the whole history, and at least two years even without any, so past dates and
    // their reminders can always be looked up. Forward only to the page holding today: later Hijri
    // months aren't known until their moon is sighted.
    val earliest = remember(history, today) {
        val first = history.mapNotNull { runCatching { LocalDate.parse(it.substringBefore('|')) }.getOrNull() }.minOrNull()
        minOf(first ?: today, today.minusYears(2))
    }
    val canGoBack = page.days.first().isAfter(earliest)
    val canGoForward = page.days.last().isBefore(today)

    val gregorianTitle = gregorianSpan(context, page.days.first(), page.days.last(), locale)
    val hijriTitle = hijriSpan(context, page.hijri.first(), page.hijri.last())
    fun dates(date: LocalDate): Pair<String, String> {
        val gregorian = gregorianLabel(context, date)
        val hijri = IslamicCalendar.label(context, hijriOf.getValue(date))
        return if (hijriPrimary) hijri to gregorian else gregorian to hijri
    }

    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarSwitch(hijriPrimary) { PrimaryCalendar.setHijri(context, it) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.graphicsLayer { alpha = if (canGoBack) 1f else 0.3f }) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.insights_previous_month)) {
                    if (canGoBack) anchorText = page.days.first().minusDays(1).toString()
                }
            }
            Box(Modifier.graphicsLayer { alpha = if (canGoForward) 1f else 0.3f }) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.insights_next_month)) {
                    if (canGoForward) anchorText = page.days.last().plusDays(1).toString()
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        Text(if (hijriPrimary) hijriTitle else gregorianTitle, style = Type.titleL)
        Spacer(Modifier.height(2.dp))
        // The Hijri line is gold, like the Hijri numbers in the cells, so the key is obvious.
        Text(
            if (hijriPrimary) gregorianTitle else hijriTitle,
            style = Type.caption.copy(color = if (hijriPrimary) Nur.textTertiary else Nur.gold.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.height(Space.l))
        WeekdayHeader(locale)
        Spacer(Modifier.height(Space.s))

        val lead = page.days.first().dayOfWeek.value % 7 // Sunday first
        val cells = List(lead) { null } + page.days
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    week.forEach { date ->
                        // Square, like every calendar the eye already knows; the gap is the same both ways.
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (date != null) {
                                val record = records.getValue(date)
                                val (first, second) = dates(date)
                                val what = stringResource(
                                    R.string.insights_day_cd,
                                    stringResource(R.string.calendar_day_dates, first, second),
                                    stringResource(
                                        when {
                                            record.both -> R.string.insights_day_cd_both
                                            record.morning -> R.string.insights_day_cd_morning
                                            record.evening -> R.string.insights_day_cd_evening
                                            else -> R.string.insights_day_cd_none
                                        },
                                    ),
                                )
                                DayCell(
                                    date = date,
                                    record = record,
                                    hijri = hijriOf.getValue(date),
                                    hijriPrimary = hijriPrimary,
                                    isToday = date == today,
                                    isFuture = date.isAfter(today),
                                    isSelected = date == selected,
                                    description = if (date in late) stringResource(R.string.insights_day_cd_late, what) else what,
                                ) {
                                    selectedText = date.toString()
                                    reminderText = date.toString()
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            LegendSwatch(ChartColors.morning, stringResource(R.string.insights_morning))
            LegendSwatch(ChartColors.evening, stringResource(R.string.insights_evening))
            LegendSwatch(null, stringResource(R.string.insights_both))
        }

        // The chosen day in words, and a way back to its reminder once the sheet is closed.
        records[selected]?.let { r ->
            Spacer(Modifier.height(Space.l))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(Space.m))
            val (first, second) = dates(selected)
            Row(
                Modifier.fillMaxWidth().pressable(scale = 0.985f) { reminderText = selectedText },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(first, style = Type.label)
                    Spacer(Modifier.height(2.dp))
                    Text(second, style = Type.caption)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        stringResource(
                            when {
                                r.both -> R.string.insights_day_both_done
                                r.morning -> R.string.insights_day_morning_done
                                r.evening -> R.string.insights_day_evening_done
                                selected.isAfter(today) -> R.string.insights_day_still_to_come
                                else -> R.string.insights_day_none
                            },
                        ),
                        style = Type.caption,
                    )
                    late[selected]?.let { types ->
                        Text(
                            stringResource(
                                when {
                                    types.size == SessionType.entries.size -> R.string.insights_day_late_both
                                    SessionType.MORNING in types -> R.string.insights_day_late_morning
                                    else -> R.string.insights_day_late_evening
                                },
                            ),
                            style = Type.caption,
                        )
                    }
                }
                Spacer(Modifier.width(Space.m))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.calendar_open_reminder),
                    tint = Nur.textTertiary, modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    reminderText?.let { DayReminderSheet(LocalDate.parse(it)) { reminderText = null } }
}

/** "Hijri | Gregorian": which calendar leads. Small, so it sits in the header beside the arrows. */
@Composable
private fun CalendarSwitch(hijriPrimary: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(50)
    Row(Modifier.height(36.dp).glass(shape).padding(3.dp)) {
        listOf(true to R.string.calendar_hijri, false to R.string.calendar_gregorian).forEach { (hijri, label) ->
            val on = hijri == hijriPrimary
            val color by animateColorAsState(if (on) Nur.textPrimary else Nur.textTertiary, tween(200), label = "calendarSwitch")
            Box(
                Modifier
                    .fillMaxHeight()
                    .then(if (on) Modifier.glass(shape, level = 2) else Modifier)
                    .pressable(scale = 0.95f, haptic = false, shape = shape) {
                        if (!on) {
                            Haptics.tick(view)
                            onChange(hijri)
                        }
                    }
                    .semantics {
                        role = Role.Tab
                        selected = on
                    }
                    .padding(horizontal = Space.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
            }
        }
    }
}

/**
 * Day names as on a Nigerian Islamic calendar: the Arabic names (Ahd, Ith… as transliteration)
 * above the names in the app's language, on a quiet band whose columns are the grid's. The Arabic
 * row is gold, the key for the Hijri calendar. In Arabic both rows would say the same, so there is one.
 */
@Composable
private fun WeekdayHeader(locale: Locale) {
    val arabicNames = stringArrayResource(R.array.calendar_weekdays_arabic)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CellRadius))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            arabicNames.forEach {
                Text(
                    it, style = Type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Nur.gold.copy(alpha = 0.85f)),
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                )
            }
        }
        if (locale.language != "ar") {
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                listOf(7, 1, 2, 3, 4, 5, 6).forEach {
                    Text(
                        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale),
                        style = Type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One square day: the leading calendar's number large in the middle, the other calendar's number
 * small beneath it. Hijri numbers in the small slot are gold, the key used in the header; the 1st
 * of a month there is set bold and bright, so month turns can be found at a glance. Today wears a
 * gold ring and the chosen day a white one, each standing just off the fill.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    record: DayRecord,
    hijri: HijriDay,
    hijriPrimary: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CellRadius)
    val ringed = isToday || isSelected
    val primary = if (hijriPrimary) hijri.day else date.dayOfMonth
    val secondary = if (hijriPrimary) date.dayOfMonth else hijri.day
    val monthTurn = secondary == 1
    Box(
        Modifier
            .fillMaxSize()
            .pressable(scale = 0.92f, shape = shape, onClick = onClick)
            .then(if (ringed) Modifier.border(1.5.dp, if (isToday) Nur.gold else Color.White, shape) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Concentric with the ring: the fill's corners are the ring's less the gap.
        val fillShape = RoundedCornerShape(if (ringed) CellRadius - RingGap else CellRadius)
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (ringed) RingGap else 0.dp)
                .clip(fillShape)
                .then(
                    when {
                        record.both -> Modifier.drawBehind { drawRect(bothBrush(Offset.Zero, Offset(size.width, size.height))) }
                        record.morning -> Modifier.background(ChartColors.morning)
                        record.evening -> Modifier.background(ChartColors.evening)
                        else -> Modifier.background(Color.White.copy(alpha = if (isFuture) 0.03f else 0.06f))
                    },
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$primary",
                style = Type.caption.copy(
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (record.any || isToday) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        record.any -> Color.White
                        isFuture -> Nur.textDisabled
                        else -> Nur.textPrimary.copy(alpha = 0.86f)
                    },
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                "$secondary",
                style = Type.caption.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (monthTurn) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        record.any -> Color.White.copy(alpha = if (monthTurn) 1f else 0.78f)
                        hijriPrimary -> if (isFuture) Nur.textDisabled else Nur.textTertiary.copy(alpha = if (monthTurn) 0.9f else 0.55f)
                        isFuture -> Nur.gold.copy(alpha = 0.35f)
                        else -> Nur.gold.copy(alpha = if (monthTurn) 1f else 0.7f)
                    },
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

/** A key entry: a swatch in the cells' own shape (split for "both"), then its label. */
@Composable
private fun LegendSwatch(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .drawBehind { drawRect(color?.let { SolidColor(it) } ?: bothBrush(Offset.Zero, Offset(size.width, size.height))) },
        )
        Spacer(Modifier.width(Space.s))
        Text(label, style = Type.caption)
    }
}

/** "September 2026", "September – October 2026" or "December 2026 – January 2027". */
private fun gregorianSpan(context: Context, from: LocalDate, to: LocalDate, locale: Locale): String {
    fun name(date: LocalDate) = date.month.getDisplayName(TextStyle.FULL, locale)
    return when {
        YearMonth.from(from) == YearMonth.from(to) -> context.getString(R.string.insights_month_year, name(from), from.year)
        from.year == to.year -> context.getString(R.string.calendar_gregorian_span, name(from), name(to), to.year)
        else -> context.getString(R.string.calendar_gregorian_span_years, name(from), from.year, name(to), to.year)
    }
}

/** "Rabiʿ al-Thani 1448", or "Rabiʿ al-Awwal – Rabiʿ al-Thani 1448" across a Gregorian month. */
private fun hijriSpan(context: Context, first: HijriDay, last: HijriDay): String {
    val from = IslamicCalendar.monthName(context, first.month)
    val to = IslamicCalendar.monthName(context, last.month)
    return when {
        first.month == last.month && first.year == last.year -> context.getString(R.string.insights_hijri_month, from, first.year)
        first.year == last.year -> context.getString(R.string.insights_hijri_span, from, to, last.year)
        else -> context.getString(R.string.insights_hijri_span_years, from, first.year, to, last.year)
    }
}
