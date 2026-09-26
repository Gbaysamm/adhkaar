package org.adhkaar.app.ui.insights

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.Insights
import org.adhkaar.app.data.Punctuality
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Streaks
import org.adhkaar.app.data.WeekdayRate
import org.adhkaar.app.ui.components.DualCalendar
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.LegendDot
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.library.shelfIcon
import org.adhkaar.app.ui.theme.ChartColors
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val state = remember { SessionState.get(context) }
    val history by state.historyFlow.collectAsState()
    val log by state.collectionLogFlow.collectAsState()
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    val times = remember(history) { state.completionTimes() }
    val settings by SettingsStore.get(context).flow.collectAsState()
    // Each day's windows, from today's settings: on time means said before sunrise or Maghrib.
    val windowOf = remember(settings) {
        { date: LocalDate, type: SessionType -> AdhkaarWindows.window(settings, type, date) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        Spacer(Modifier.height(Space.xl))
        Text(stringResource(R.string.insights_title), style = Type.displayL)
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(R.string.insights_subtitle), style = Type.bodyM)
        Spacer(Modifier.height(Space.xl))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            val current = Streaks.current(history, today)
            StatTile(Icons.Rounded.LocalFireDepartment, Nur.gold, "$current", pluralStringResource(R.plurals.insights_day_streak, current), Modifier.weight(1f))
            StatTile(Icons.Rounded.EmojiEvents, Nur.gold, "${Streaks.best(history, today)}", stringResource(R.string.insights_best_streak), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            StatTile(Icons.Rounded.CalendarMonth, ChartColors.evening, "${Insights.sessions(history, thisMonth)}", stringResource(R.string.insights_sessions_this_month), Modifier.weight(1f))
            StatTile(Icons.Rounded.TaskAlt, Nur.success, "${Insights.fullDays(history, thisMonth)}", stringResource(R.string.insights_full_days_this_month), Modifier.weight(1f))
        }

        Header(stringResource(R.string.insights_section_calendar))
        DualCalendar(history, times, settings, today)

        Header(stringResource(R.string.insights_section_rhythm))
        GlassCard(Modifier.fillMaxWidth()) {
            Row {
                UsualTime(stringResource(R.string.insights_morning), Insights.usualMinute(times, SessionType.MORNING), ChartColors.morning, Modifier.weight(1f))
                UsualTime(stringResource(R.string.insights_evening), Insights.usualMinute(times, SessionType.EVENING), ChartColors.evening, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.xl))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(Space.l))
            OnTime(remember(times, thisMonth, windowOf) { Insights.punctuality(times, thisMonth, windowOf) })
            Spacer(Modifier.height(Space.xl))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.insights_weekday_title), style = Type.label)
            Spacer(Modifier.height(Space.l))
            WeekdayBars(remember(history) { Insights.weekdayRates(history, today) })
        }

        Header(stringResource(R.string.insights_section_collections))
        val counts = remember(log, thisMonth) { Insights.collectionCounts(log, thisMonth) }
        val collections = remember { CollectionsRepository.all(context).filter { it.mode == org.adhkaar.app.data.CollectionMode.SESSION } }
        GlassCard(Modifier.fillMaxWidth()) {
            collections.forEachIndexed { i, c ->
                if (i > 0) {
                    Spacer(Modifier.height(Space.m))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    Spacer(Modifier.height(Space.m))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(shelfIcon(c.id), size = 36.dp)
                    Spacer(Modifier.width(Space.l))
                    Text(c.title, style = Type.label, modifier = Modifier.weight(1f))
                    val n = counts[c.id] ?: 0
                    Text(if (n == 0) "—" else stringResource(R.string.insights_collection_count, n), style = Type.stat.copy(fontSize = 17.sp, color = if (n == 0) Nur.textTertiary else Nur.textPrimary))
                }
            }
        }
        Spacer(Modifier.height(Space.tabBarClearance))
    }
}

@Composable
private fun Header(title: String) {
    Text(title, style = Type.titleL, modifier = Modifier.padding(top = Space.xxl, bottom = Space.m))
}

/** A hero number: the figure is the point, so no chart. */
@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    GlassCard(modifier) {
        IconBadge(icon, tint = tint, size = 36.dp)
        Spacer(Modifier.height(Space.l))
        Text(value, style = Type.countdown.copy(fontSize = 34.sp, lineHeight = 38.sp))
        Text(label, style = Type.caption)
    }
}

@Composable
private fun UsualTime(label: String, minute: Int?, color: Color, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        LegendDot(color, label)
        Spacer(Modifier.height(Space.s))
        Text(
            minute?.let { formatTime(context, ZonedDateTime.now().withHour(it / 60).withMinute(it % 60)) } ?: "—",
            style = Type.stat.copy(fontSize = 24.sp, lineHeight = 28.sp),
        )
        Text(stringResource(if (minute == null) R.string.insights_not_enough_data else R.string.insights_usually_around), style = Type.caption)
    }
}

/** Share of this month's sessions said in their best time: a gentle nudge, not a score. */
@Composable
private fun OnTime(p: Punctuality) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.insights_on_time), style = Type.label)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.insights_on_time_body), style = Type.caption)
        }
        if (p.total > 0) {
            Spacer(Modifier.width(Space.m))
            val locale = LocalConfiguration.current.locales[0]
            Text(NumberFormat.getPercentInstance(locale).format(p.onTime.toDouble() / p.total), style = Type.stat)
        }
    }
    Spacer(Modifier.height(Space.m))
    if (p.total == 0) {
        Text(stringResource(R.string.insights_on_time_none), style = Type.caption)
    } else {
        val share by animateFloatAsState(p.onTime.toFloat() / p.total, Motion.move(), label = "onTime")
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(RoundedCornerShape(50)).background(ChartColors.evening))
        }
        Spacer(Modifier.height(Space.s))
        Text(pluralStringResource(R.plurals.insights_on_time_count, p.total, p.onTime, p.total), style = Type.caption)
    }
}

/**
 * One series, seven bars (Sunday first). The best day is labelled; tap any bar to read it.
 * Thin bars, rounded tops, anchored to a hairline baseline.
 */
@Composable
private fun WeekdayBars(rates: List<WeekdayRate>) {
    val best = rates.withIndex().maxByOrNull { it.value.rate }?.index ?: 0
    var selected by remember(rates) { mutableStateOf(best) }
    val chartHeight = 112.dp
    Column {
        Row(Modifier.fillMaxWidth().height(chartHeight + 24.dp), verticalAlignment = Alignment.Bottom) {
            rates.forEachIndexed { i, r ->
                val h by animateFloatAsState(r.rate, Motion.move(), label = "bar")
                val description = pluralStringResource(
                    R.plurals.insights_weekday_cd, r.total,
                    r.day.getDisplayName(TextStyle.FULL, Locale.getDefault()), r.done, r.total,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scale = 0.95f) { selected = i }
                        .semantics {
                            contentDescription = description
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (i == selected) {
                        Text("${r.done}/${r.total}", style = Type.caption.copy(color = Nur.textPrimary, fontFeatureSettings = "tnum"))
                        Spacer(Modifier.height(Space.xs))
                    }
                    // Rounded data-end on top, square where it meets the baseline.
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(chartHeight * h.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (i == selected) ChartColors.evening else ChartColors.evening.copy(alpha = 0.55f)),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Spacer(Modifier.height(Space.s))
        Row {
            rates.forEachIndexed { i, r ->
                Text(
                    r.day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Type.caption.copy(color = if (i == selected) Nur.textPrimary else Nur.textTertiary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
