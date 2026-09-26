package org.adhkaar.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.R
import org.adhkaar.app.data.DayReminders
import org.adhkaar.app.data.EventKind
import org.adhkaar.app.data.HijriDay
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.monthName
import org.adhkaar.app.data.titleRes
import org.adhkaar.app.ui.components.gregorianLabel
import java.time.LocalDate

/** The next special day worth counting down to. Pure, so it can be unit tested. */
object SpecialDays {
    data class Upcoming(val kind: EventKind, val inDays: Int)

    /** Two months always reaches the next white days, so something is always found. */
    const val HORIZON_DAYS = 60

    /**
     * The first day from [today] on which a special day or season begins: Arafah, ʿAshura, the
     * first of Ramadan, the last ten nights, the white days. A season already under way isn't
     * news, so its later days don't count. Jumuʿah counts only on the day itself: every week has one.
     * When several begin together, the rarer one speaks, as in the reminder of the day.
     */
    fun next(today: LocalDate, hijriOf: (LocalDate) -> HijriDay, horizonDays: Int = HORIZON_DAYS): Upcoming? {
        var before = kinds(today.minusDays(1), hijriOf)
        for (i in 0..horizonDays) {
            val kinds = kinds(today.plusDays(i.toLong()), hijriOf)
            val begins = kinds.filter { it !in before && (i == 0 || it != EventKind.JUMUAH) }
            begins.minByOrNull { DayReminders.precedence.indexOf(it) }?.let { return Upcoming(it, i) }
            before = kinds
        }
        return null
    }

    private fun kinds(date: LocalDate, hijriOf: (LocalDate) -> HijriDay): List<EventKind> =
        IslamicCalendar.eventsFor(date, hijriOf(date)).map { it.kind }
}

/** Hijri date: today's date as announced in Nigeria (or calculated), the Gregorian, and the next special day. */
class HijriWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context, id)
        val settings = SettingsStore.get(context).current
        val today = LocalDate.now()
        val hijriOf = { date: LocalDate -> MoonSighting.hijriFor(context, date, settings) }
        val hijri = hijriOf(today)
        val upcoming = SpecialDays.next(today, hijriOf)?.let { (kind, inDays) ->
            val title = context.getString(kind.titleRes())
            when (inDays) {
                0 -> context.getString(R.string.widget_event_today, title)
                1 -> context.getString(R.string.widget_event_tomorrow, title)
                else -> context.resources.getQuantityString(R.plurals.widget_event_in_days, inDays, title, inDays)
            }
        }
        provideContent {
            HijriContent(
                context, look,
                day = context.getString(R.string.widget_number, hijri.day),
                monthYear = context.getString(R.string.widget_hijri_month_year, IslamicCalendar.monthName(context, hijri.month), hijri.year),
                gregorian = gregorianLabel(context, today),
                upcoming = upcoming,
            )
        }
    }
}

class HijriWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriWidget()
}

@Composable
private fun HijriContent(context: Context, look: WidgetLook, day: String, monthYear: String, gregorian: String, upcoming: String?) {
    val palette = look.palette
    WidgetFrame(look, openAppAction(context), padding = 14.dp) {
        Text(gregorian, style = look.text(palette.tertiary, 11.sp, FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text(day, style = look.text(palette.text, 40.sp, family = FontFamily.Serif), maxLines = 1)
        Text(monthYear, style = look.text(palette.gold, 13.sp, FontWeight.Medium), maxLines = 1)
        if (upcoming != null) {
            Spacer(GlanceModifier.height(8.dp))
            DirRow(look.dir, GlanceModifier.fillMaxWidth()) {
                item {
                    Image(
                        ImageProvider(R.drawable.widget_dot), null,
                        modifier = GlanceModifier.size(6.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.gold)),
                    )
                }
                item { Spacer(GlanceModifier.width(6.dp)) }
                item { Text(upcoming, style = look.text(palette.secondary, 11.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 2) }
            }
        }
    }
}
