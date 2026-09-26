package org.adhkaar.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.adhkaar.app.R
import org.adhkaar.app.ui.MainActivity

/** Every widget the app offers, with the receiver the launcher knows it by. */
enum class WidgetKind(
    val receiver: Class<out GlanceAppWidgetReceiver>,
    @StringRes val title: Int,
    val create: () -> GlanceAppWidget,
) {
    TODAY(AdhkaarWidgetReceiver::class.java, R.string.widget_name_today, ::AdhkaarWidget),
    NEXT(NextSessionWidgetReceiver::class.java, R.string.widget_name_next, ::NextSessionWidget),
    SKY(DaySkyWidgetReceiver::class.java, R.string.widget_name_sky, ::DaySkyWidget),
    DHIKR(DhikrWidgetReceiver::class.java, R.string.widget_name_dhikr, ::DhikrWidget),
    HIJRI(HijriWidgetReceiver::class.java, R.string.widget_name_hijri, ::HijriWidget),
    STREAK(StreakWidgetReceiver::class.java, R.string.widget_name_streak, ::StreakWidget),
    COLLECTION(CollectionWidgetReceiver::class.java, R.string.widget_name_collection, ::CollectionWidget);

    companion object {
        fun of(receiverClassName: String?): WidgetKind? = entries.firstOrNull { it.receiver.name == receiverClassName }
    }
}

object Widgets {
    /** Redraws every placed widget of every kind (a kind with none placed costs nothing), then plans the next redraw. */
    suspend fun updateAll(context: Context) {
        WidgetKind.entries.forEach { kind -> runCatching { kind.create().updateAll(context) } }
        WidgetTicker.schedule(context)
    }

    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.any { manager.getAppWidgetIds(ComponentName(context, it.receiver)).isNotEmpty() }
    }
}

/**
 * Every widget's receiver: starts the ticker when a widget is placed (and after a reboot, which
 * clears alarms but updates every widget), forgets a widget's choices once it is removed, and
 * stops the ticker after the last one.
 */
abstract class StyledWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetTicker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPrefs.forget(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTicker.schedule(context)
    }
}

/**
 * The app's reading direction inside a launcher that lays out in the phone's. RemoteViews are
 * inflated by the launcher, so a Row runs in the phone's direction even when the app is set to
 * Arabic or Urdu on an English phone, or to English on an Arabic one. Where the two differ, rows
 * are built in reverse and alignments swapped, so the widget reads like the app.
 */
class Dir(val rtl: Boolean, hostRtl: Boolean) {
    private val flip = rtl != hostRtl
    val start: Alignment.Horizontal = if (flip) Alignment.Horizontal.End else Alignment.Horizontal.Start
    val end: Alignment.Horizontal = if (flip) Alignment.Horizontal.Start else Alignment.Horizontal.End
    // Text alignment is absolute: a TextView's "start" would follow the launcher's direction.
    val textStart: TextAlign = if (rtl) TextAlign.Right else TextAlign.Left
    val textEnd: TextAlign = if (rtl) TextAlign.Left else TextAlign.Right

    fun <T> inOrder(items: List<T>): List<T> = if (flip) items.asReversed() else items

    companion object {
        fun of(context: Context) = Dir(
            rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            hostRtl = Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )
    }
}

/** A widget's style and direction, read once per update. */
class WidgetLook(val style: WidgetStyle, val dir: Dir) {
    val palette = style.palette

    fun text(
        color: Color,
        size: TextUnit,
        weight: FontWeight = FontWeight.Normal,
        align: TextAlign = dir.textStart,
        family: FontFamily? = null,
    ) = TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight, textAlign = align, fontFamily = family)

    companion object {
        fun load(context: Context, id: GlanceId): WidgetLook =
            WidgetLook(WidgetPrefs.style(context, appWidgetId(context, id)), Dir.of(context))

        /** The launcher's id for a widget; invalid for a preview rendered outside a launcher. */
        fun appWidgetId(context: Context, id: GlanceId): Int =
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
    }
}

/** The widget's panel in its style, tappable as a whole. Content starts on the app's start side. */
@Composable
fun WidgetFrame(look: WidgetLook, onClick: Action, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(look.palette.background))
            .clickable(onClick)
            .padding(padding),
        horizontalAlignment = look.dir.start,
        content = content,
    )
}

/** Collects a row's children so [DirRow] can lay them out in the app's direction. */
class RowItems {
    internal val items = mutableListOf<@Composable RowScope.() -> Unit>()

    fun item(content: @Composable RowScope.() -> Unit) {
        items += content
    }
}

/** A Row whose children run from the app's start side, whatever the launcher's direction (see [Dir]). */
@Composable
fun DirRow(
    dir: Dir,
    modifier: GlanceModifier = GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.Vertical.CenterVertically,
    build: RowItems.() -> Unit,
) {
    val items = RowItems().apply(build).items
    Row(modifier, verticalAlignment = verticalAlignment) {
        dir.inOrder(items).forEach { it(this) }
    }
}

fun openAppAction(context: Context): Action = actionStartActivity(Intent(context, MainActivity::class.java))
