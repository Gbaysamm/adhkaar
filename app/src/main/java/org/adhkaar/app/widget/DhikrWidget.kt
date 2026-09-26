package org.adhkaar.app.widget

import android.content.Context
import android.graphics.Bitmap
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.CollectionMoments
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.SessionDhikr
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.ui.home.heroSession
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Which short dhikr the widget shows. Pure, so it can be unit tested. */
object DhikrOfTheMoment {
    /** Longer Arabic (counted with its tashkeel) doesn't fit three lines of a 4×2 widget. */
    const val MAX_ARABIC = 125
    /** Longer meanings don't fit two lines under it. */
    const val MAX_MEANING = 150
    /** A new dhikr every three hours: eight turns a day. */
    const val HOURS_PER_TURN = 3

    fun isShort(dhikr: SessionDhikr) = dhikr.arabic.length <= MAX_ARABIC && dhikr.translation.length <= MAX_MEANING

    /**
     * The same moment always gives the same dhikr. Each turn takes the next short one in a fixed
     * order, so every one comes round before any repeats; null only when none is short enough.
     */
    fun pick(pool: List<SessionDhikr>, at: LocalDateTime): SessionDhikr? {
        // Ordered by hash, not by id, so the after-salah tasbih ("salah_…") don't come in a row.
        val short = pool.filter(::isShort).distinctBy { it.arabic }.sortedWith(compareBy({ it.id.hashCode() }, { it.id }))
        if (short.isEmpty()) return null
        val turn = at.toLocalDate().toEpochDay() * (24 / HOURS_PER_TURN) + at.hour / HOURS_PER_TURN
        return short[Math.floorMod(turn, short.size.toLong()).toInt()]
    }
}

/** Dhikr of the moment: one short dhikr in Arabic with its meaning, changing through the day. */
class DhikrWidget : GlanceAppWidget() {
    // One layout for every size: the Arabic bitmap is sent once, not once per size.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context, id)
        // The adhkaar of the session the day is in (morning wording in the morning, evening
        // wording after), and the tasbih said after every salah.
        val session = heroSession(context, SessionState.get(context).pending, ZonedDateTime.now())
        val pool = AdhkaarRepository.forSession(context, session) +
            CollectionsRepository.get(context, CollectionMoments.AFTER_SALAH)?.items.orEmpty().filterNot { it.reused }
        val dhikr = DhikrOfTheMoment.pick(pool, LocalDateTime.now())
        val arabic = dhikr?.let {
            WidgetArt.arabic(context, it.arabic, look.palette.text, look.palette.shadow, ARABIC_WIDTH_DP, ARABIC_SIZE_SP, maxLines = 3)
        }
        provideContent { DhikrContent(context, look, dhikr, arabic) }
    }

    private companion object {
        // A 4×2 widget's inner width; the image scales down to fit smaller ones.
        const val ARABIC_WIDTH_DP = 280f
        const val ARABIC_SIZE_SP = 20f
    }
}

class DhikrWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DhikrWidget()
}

@Composable
private fun DhikrContent(context: Context, look: WidgetLook, dhikr: SessionDhikr?, arabic: Bitmap?) {
    val palette = look.palette
    WidgetFrame(look, openAppAction(context), padding = 14.dp) {
        DirRow(look.dir, GlanceModifier.fillMaxWidth()) {
            item { Image(ImageProvider(R.drawable.widget_dot), null, modifier = GlanceModifier.size(6.dp), colorFilter = ColorFilter.tint(ColorProvider(palette.gold))) }
            item { Spacer(GlanceModifier.width(8.dp)) }
            item {
                Text(
                    context.getString(R.string.widget_name_dhikr).uppercase(),
                    style = look.text(palette.tertiary, 10.sp, FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        if (dhikr != null && arabic != null) {
            // Reading is centred, as everywhere a dhikr is read in the app.
            Image(
                ImageProvider(arabic), dhikr.arabic,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                dhikr.translation,
                style = look.text(palette.secondary, 12.sp, align = TextAlign.Center),
                modifier = GlanceModifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}
