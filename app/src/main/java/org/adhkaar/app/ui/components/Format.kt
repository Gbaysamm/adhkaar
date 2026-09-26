package org.adhkaar.app.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.adhkaar.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** A time in the user's 12/24-hour preference. */
fun formatTime(context: Context, time: ZonedDateTime): String =
    // Isolated left-to-right, so "6:35 AM" never flips to "AM 6:35" inside Arabic or Urdu text.
    "⁦" + DateFormat.getTimeFormat(context).format(Date.from(time.toInstant())) + "⁩"

/** "2h 14m", "9m", "now". */
fun formatCountdown(context: Context, from: ZonedDateTime, to: ZonedDateTime): String {
    val d = Duration.between(from, to)
    if (d.isNegative || d.toMinutes() < 1) return context.getString(R.string.format_now)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h > 0) context.getString(R.string.format_hours_minutes, h.toInt(), m.toInt()) else context.getString(R.string.format_minutes, m.toInt())
}

/** "Thursday, 25 September". */
fun gregorianLabel(context: Context, date: LocalDate): String =
    context.getString(
        R.string.format_gregorian_date,
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        date.dayOfMonth,
        date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
    )

/** Increments every time the screen resumes, so permission checks refresh after visiting system settings. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return tick
}

/** Renders ﷺ in Amiri Quran at a readable size; Inter has no glyph for it. */
fun withHonorifics(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        text.split("ﷺ").forEachIndexed { i, part ->
            if (i > 0) {
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontFamily = org.adhkaar.app.ui.theme.AmiriQuran,
                        fontSize = androidx.compose.ui.unit.TextUnit(1.35f, androidx.compose.ui.unit.TextUnitType.Em),
                    ),
                )
                append("ﷺ")
                pop()
            }
            append(part)
        }
    }
