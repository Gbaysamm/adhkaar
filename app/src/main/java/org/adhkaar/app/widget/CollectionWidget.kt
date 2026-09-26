package org.adhkaar.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.ui.MainActivity

/** A collection shortcut: one tap into the collection chosen when the widget was added. */
class CollectionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(ROW, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context, id)
        val collectionId = WidgetPrefs.collection(context, WidgetLook.appWidgetId(context, id))
        // Title and subtitle in the app's language, as the Adhkaar tab shows them.
        val collection = CollectionsRepository.get(context, collectionId)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_COLLECTION, collectionId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            CollectionContent(
                look, open, collectionId,
                title = collection?.title.orEmpty(),
                subtitle = collection?.subtitle.orEmpty(),
            )
        }
    }

    companion object {
        /** 2×1: icon beside the name. */
        val ROW = DpSize(110.dp, 40.dp)
        /** 2×2 and up: icon above the name. */
        val TALL = DpSize(110.dp, 100.dp)
    }
}

class CollectionWidgetReceiver : StyledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollectionWidget()
}

@Composable
private fun CollectionContent(look: WidgetLook, open: Intent, collectionId: String, title: String, subtitle: String) {
    val palette = look.palette
    val accent = WidgetCollections.accent(collectionId, palette)
    val tall = LocalSize.current.height >= CollectionWidget.TALL.height
    WidgetFrame(look, actionStartActivity(open), padding = if (tall) 14.dp else 10.dp) {
        if (tall) {
            CollectionIcon(collectionId, palette, accent, 44.dp)
            Spacer(GlanceModifier.defaultWeight())
            Text(title, style = look.text(palette.text, 16.sp, FontWeight.Bold), maxLines = 2)
            Text(subtitle, style = look.text(palette.secondary, 11.sp), maxLines = 2)
        } else {
            DirRow(look.dir, GlanceModifier.fillMaxSize()) {
                item { CollectionIcon(collectionId, palette, accent, 36.dp) }
                item { Spacer(GlanceModifier.width(10.dp)) }
                item {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = look.dir.start) {
                        Text(title, style = look.text(palette.text, 14.sp, FontWeight.Bold), maxLines = 1)
                        Text(subtitle, style = look.text(palette.secondary, 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The collection's icon, as in the Adhkaar tab, on a soft disc. */
@Composable
private fun CollectionIcon(collectionId: String, palette: WidgetPalette, accent: Color, size: Dp) {
    Box(GlanceModifier.size(size).background(ImageProvider(palette.disc)), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(WidgetCollections.icon(collectionId)), null,
            modifier = GlanceModifier.size(size * 0.5f),
            colorFilter = ColorFilter.tint(ColorProvider(accent)),
        )
    }
}
