package org.adhkaar.app.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import org.adhkaar.app.R
import org.adhkaar.app.data.CollectionMoments
import org.adhkaar.app.data.SessionType

/** How a widget sits on the home screen. Chosen per widget when it is added. */
enum class WidgetStyle(val key: String, @StringRes val title: Int, @StringRes val description: Int) {
    /** The app's own midnight glass. */
    GLASS("glass", R.string.widget_style_glass, R.string.widget_style_glass_desc),
    /** Warm cream with dark ink and gold, for light wallpapers. */
    PARCHMENT("parchment", R.string.widget_style_parchment, R.string.widget_style_parchment_desc),
    /** No panel at all: white text straight on the wallpaper, over a faint shadow. */
    CLEAR("clear", R.string.widget_style_clear, R.string.widget_style_clear_desc);

    val palette: WidgetPalette get() = when (this) {
        GLASS -> glass
        PARCHMENT -> parchment
        CLEAR -> clear
    }

    companion object {
        fun fromKey(key: String?): WidgetStyle = entries.firstOrNull { it.key == key } ?: GLASS
    }
}

/**
 * Every colour and surface a widget needs in one style. Colours are Compose colours because Glance
 * takes them; the bitmaps (orb, sky, Arabic) use the same values through toArgb().
 */
class WidgetPalette(
    @DrawableRes val background: Int,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val morning: Color,
    val evening: Color,
    val gold: Color,
    val done: Color,
    /**
     * A week dot not yet done. A drawable with its own translucency, like [disc]: tinting with a
     * translucent colour turns solid before Android 12.
     */
    @DrawableRes val emptyDot: Int,
    @DrawableRes val morningPill: Int,
    @DrawableRes val eveningPill: Int,
    val onPill: Color,
    /** A soft round backdrop for an icon. */
    @DrawableRes val disc: Int,
    /** Text drawn into bitmaps gets a soft shadow so it holds up on any wallpaper. */
    val shadow: Boolean,
) {
    fun accent(type: SessionType) = if (type == SessionType.MORNING) morning else evening
    fun pill(type: SessionType) = if (type == SessionType.MORNING) morningPill else eveningPill
}

// Glass: DESIGN.md tokens (text, dawn/evening accents, gold, success).
private val glass = WidgetPalette(
    background = R.drawable.widget_background,
    text = Color(0xFFF5F7FF),
    secondary = Color.White.copy(alpha = 0.72f),
    tertiary = Color.White.copy(alpha = 0.48f),
    morning = Color(0xFFFFB88A),
    evening = Color(0xFF8FB1FF),
    gold = Color(0xFFF2CF8A),
    done = Color(0xFF5BE3A4),
    emptyDot = R.drawable.widget_dot_empty_glass,
    morningPill = R.drawable.widget_pill_dawn,
    eveningPill = R.drawable.widget_pill,
    onPill = Color.White,
    disc = R.drawable.widget_disc_light,
    shadow = false,
)

// Parchment: the same hues, deepened until they read as ink on cream (text 4.5:1, captions 4:1).
private val ink = Color(0xFF2B2118)
private val parchment = WidgetPalette(
    background = R.drawable.widget_bg_parchment,
    text = ink,
    secondary = ink.copy(alpha = 0.8f),
    tertiary = ink.copy(alpha = 0.66f),
    morning = Color(0xFFA9521F),
    evening = Color(0xFF2F4DA8),
    gold = Color(0xFF8C6418),
    done = Color(0xFF23764D),
    emptyDot = R.drawable.widget_dot_empty_parchment,
    morningPill = R.drawable.widget_pill_parchment,
    eveningPill = R.drawable.widget_pill_parchment,
    onPill = Color(0xFFFBF3E2),
    disc = R.drawable.widget_disc_dark,
    shadow = false,
)

// Clear: lighter accents than glass, because a busy wallpaper eats contrast.
private val clear = WidgetPalette(
    background = R.drawable.widget_bg_clear,
    text = Color.White,
    secondary = Color.White.copy(alpha = 0.9f),
    tertiary = Color.White.copy(alpha = 0.74f),
    morning = Color(0xFFFFCBA6),
    evening = Color(0xFFB9CDFF),
    gold = Color(0xFFF7DCA4),
    done = Color(0xFF86F2C1),
    emptyDot = R.drawable.widget_dot_empty_clear,
    morningPill = R.drawable.widget_pill_clear,
    eveningPill = R.drawable.widget_pill_clear,
    onPill = Color.White,
    disc = R.drawable.widget_disc_light,
    shadow = true,
)

/** The collections a shortcut widget can open, in the order the Adhkaar tab lists them. */
object WidgetCollections {
    const val EVERYDAY_DUAS = "daily"
    val all = listOf(CollectionMoments.AFTER_SALAH, CollectionMoments.BEFORE_SLEEP, CollectionMoments.WAKING, EVERYDAY_DUAS)

    @DrawableRes
    fun icon(id: String): Int = when (id) {
        CollectionMoments.BEFORE_SLEEP -> R.drawable.widget_ic_sleep
        CollectionMoments.WAKING -> R.drawable.widget_ic_waking
        EVERYDAY_DUAS -> R.drawable.widget_ic_duas
        else -> R.drawable.widget_ic_salah
    }

    /** Same light as the collection has in the app: dawn for waking, evening for the rest. */
    fun accent(id: String, palette: WidgetPalette): Color = when (id) {
        CollectionMoments.WAKING -> palette.morning
        EVERYDAY_DUAS -> palette.gold
        else -> palette.evening
    }
}

/** Each placed widget's choices, by app widget id. */
object WidgetPrefs {
    private const val PREFS = "widgets"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun style(context: Context, appWidgetId: Int): WidgetStyle =
        WidgetStyle.fromKey(prefs(context).getString("style_$appWidgetId", null))

    fun collection(context: Context, appWidgetId: Int): String =
        prefs(context).getString("collection_$appWidgetId", null)?.takeIf { it in WidgetCollections.all }
            ?: WidgetCollections.all.first()

    fun save(context: Context, appWidgetId: Int, style: WidgetStyle, collection: String?) {
        prefs(context).edit().apply {
            putString("style_$appWidgetId", style.key)
            if (collection != null) putString("collection_$appWidgetId", collection)
        }.commit()
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply {
            appWidgetIds.forEach { remove("style_$it"); remove("collection_$it") }
        }.apply()
    }
}
