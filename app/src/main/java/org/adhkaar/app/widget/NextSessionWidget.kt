package org.adhkaar.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionType

/** The orb's box, halo included; the glass sphere inside is 30dp. */
private const val ORB_DP = 48f

/** Next session: the orb of the coming session, its name and its time. */
class NextSessionWidget : GlanceAppWidget() {
    // One layout for every size: the orb bitmap is sent once, not once per size.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val look = WidgetLook.load(context, id)
        val orb = WidgetArt.orb(context, ORB_DP, sun = data.type == SessionType.MORNING)
        provideContent { NextContent(context, data, look, orb) }
    }
}

class NextSessionWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextSessionWidget()
}

@Composable
private fun NextContent(context: Context, data: WidgetData, look: WidgetLook, orb: Bitmap) {
    val palette = look.palette
    WidgetFrame(look, openAppAction(context), padding = 14.dp) {
        Image(ImageProvider(orb), null, modifier = GlanceModifier.size(ORB_DP.dp))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            context.getString(if (data.type == SessionType.MORNING) R.string.widget_morning else R.string.widget_evening).uppercase(),
            style = look.text(palette.accent(data.type), 11.sp, FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            if (data.waiting) context.getString(R.string.widget_now) else data.time ?: "—",
            style = look.text(palette.text, 26.sp, FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            if (data.waiting) context.getString(R.string.widget_waiting) else data.countdown ?: data.day.orEmpty(),
            style = look.text(palette.secondary, 11.sp),
            maxLines = 1,
        )
    }
}
