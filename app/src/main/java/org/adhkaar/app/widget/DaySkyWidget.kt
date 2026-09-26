package org.adhkaar.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import org.adhkaar.app.R
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.schedule.PrayerClock
import org.adhkaar.app.schedule.SessionTimeCalculator
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.home.dayProgress
import java.time.LocalDate
import java.time.ZonedDateTime

/** One end of the arc: a session's name, and its time or that it is done. */
private class SkyEnd(val label: String, val detail: String, val done: Boolean)

/** Day sky: the day from Fajr to Maghrib as an arc, the orb where the day is now, the two sessions at its ends. */
class DaySkyWidget : GlanceAppWidget() {
    // One layout for every size: the sky bitmap is sent once, not once per size.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context, id)
        val settings = SettingsStore.get(context).current
        val state = SessionState.get(context)
        val now = ZonedDateTime.now()
        val today = LocalDate.now()
        val fajr = PrayerClock.time(settings, Prayer.FAJR)
        val maghrib = PrayerClock.time(settings, Prayer.MAGHRIB)
        val ends = SessionType.entries.map { type ->
            val done = state.lastCompleted(type) == today
            val time = SessionTimeCalculator.sessionTime(settings.schedule(type), today, PrayerClock.provider(settings, type))
            SkyEnd(
                label = context.getString(if (type == SessionType.MORNING) R.string.widget_morning else R.string.widget_evening),
                detail = if (done) context.getString(R.string.widget_done) else formatTime(context, now.with(time)),
                done = done,
            )
        }
        val sky = WidgetArt.sky(context, dayProgress(now.toLocalTime(), fajr, maghrib), look.palette, look.dir.rtl, SKY_WIDTH_DP, SKY_HEIGHT_DP)
        provideContent { SkyContent(context, look, sky, ends[0], ends[1]) }
    }

    private companion object {
        // A 4×2 widget's inner width; the image scales down to fit smaller ones.
        const val SKY_WIDTH_DP = 300f
        const val SKY_HEIGHT_DP = 84f
    }
}

class DaySkyWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaySkyWidget()
}

@Composable
private fun SkyContent(context: Context, look: WidgetLook, sky: Bitmap, morning: SkyEnd, evening: SkyEnd) {
    val palette = look.palette
    WidgetFrame(look, openAppAction(context), padding = 14.dp) {
        Image(
            ImageProvider(sky), context.getString(R.string.widget_name_sky),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentScale = ContentScale.Fit,
        )
        DirRow(look.dir, GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            item { EndLabel(look, morning, palette.morning, look.dir.start, look.dir.textStart) }
            item { Spacer(GlanceModifier.defaultWeight()) }
            item { EndLabel(look, evening, palette.evening, look.dir.end, look.dir.textEnd) }
        }
    }
}

@Composable
private fun EndLabel(look: WidgetLook, end: SkyEnd, accent: Color, align: Alignment.Horizontal, textAlign: TextAlign) {
    Column(horizontalAlignment = align) {
        Text(end.label.uppercase(), style = look.text(accent, 10.sp, FontWeight.Bold, textAlign), maxLines = 1)
        Text(
            end.detail,
            style = look.text(if (end.done) look.palette.done else look.palette.secondary, 12.sp, FontWeight.Medium, textAlign),
            maxLines = 1,
        )
    }
}
