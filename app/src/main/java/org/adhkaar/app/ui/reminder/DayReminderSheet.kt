package org.adhkaar.app.ui.reminder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.icu.text.NumberFormat
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adhkaar.app.R
import org.adhkaar.app.data.DayReminder
import org.adhkaar.app.data.DayReminders
import org.adhkaar.app.data.HijriDay
import org.adhkaar.app.data.HijriMonths
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.MoonSighting
import org.adhkaar.app.data.ReminderKind
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.monthName
import org.adhkaar.app.ui.components.GlassSurface
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.share.DayCardFrame
import org.adhkaar.app.ui.share.GoldFoil
import org.adhkaar.app.ui.share.GoldOrnament
import org.adhkaar.app.ui.share.shareImage
import org.adhkaar.app.ui.theme.InstrumentSerif
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * The day, opened from Today or the calendar: its date large in both calendars, its reminder set
 * for reading, and the day card to share or save. The card itself isn't shown here; it is drawn
 * off screen at full size and exported as is.
 */
@Composable
fun DayReminderSheet(date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val settings by SettingsStore.get(context).flow.collectAsState()
    val hijri = remember(date, settings) { MoonSighting.hijriFor(context, date, settings) }
    val span = remember(date, hijri, settings) {
        // The grid follows whichever calendar gave the date: the announced months when the date
        // came from them, else the calculated calendar with the user's offset.
        val announced = MoonSighting.current(context).takeIf { settings.followMoonSighting && MoonSighting.hijri(it, date) != null }
        HijriMonths.span(date, hijri, announced, settings.hijriOffset)
    }
    val reminder = remember(date, hijri) { DayReminders.forDate(DayReminders.all(context), date, hijri) }
    val imageName = "adhkaar-$date"

    RisingSheet(onDismiss) {
        DateHeader(date, hijri)
        Spacer(Modifier.height(Space.xl))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Spacer(Modifier.height(Space.xl))
        // Reading: everything about the words is centred (DESIGN.md → Alignment).
        Text(
            kindLabel(reminder.kind).uppercase(Locale.getDefault()),
            style = Type.overline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.l))
        ReminderReading(reminder, arabicMaxChars = Int.MAX_VALUE)
        Spacer(Modifier.height(Space.xxl))
        Box {
            // Recorded for export only; drawn at a point, so it takes no room and is never seen.
            DayCardFrame(
                date, hijri, span, reminder, { MoonSighting.hijriFor(context, it, settings) }, layer,
                Modifier.width(1.dp), visible = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                // Saving to the gallery needs no permission from Android 10 on; before that it
                // would need storage access, so older phones share instead.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SaveButton(Modifier.weight(1f)) {
                        scope.launch {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            val saved = withContext(Dispatchers.IO) { saveToPictures(context, bitmap, imageName) }
                            val message = if (saved) R.string.reminder_saved else R.string.reminder_save_failed
                            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.day_design_share_image), Modifier.weight(1.5f), icon = Icons.Rounded.IosShare) {
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, imageName, shareText(reminder), context.getString(R.string.reminder_share_chooser))
                    }
                }
            }
        }
    }
}

/**
 * A sheet that rises from the bottom edge with the app's entrance curve over a dimming scrim, and
 * falls away when the scrim is tapped, Back is pressed, or it is dragged down.
 */
@Composable
private fun RisingSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.animateTo(1f, Motion.enter()) }
    val close = {
        if (!closing) {
            closing = true
            scope.launch {
                shown.animateTo(0f, Motion.exit())
                onDismiss()
            }
        }
    }
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val accent = LocalAura.current.accent
    val shape = RoundedCornerShape(topStart = Radius.hero, topEnd = Radius.hero)
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.6f * shown.value)) }
                .clickable(remember { MutableInteractionSource() }, null) { close() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .graphicsLayer { translationY = (1f - shown.value) * size.height + drag.value }
                    .draggable(
                        rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                        Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (drag.value > dismissDistance || velocity > 1_500f) close() else drag.animateTo(0f, Motion.move())
                        },
                    )
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .clip(shape)
                    .background(Color(0xFF0C1022))
                    // The aura's light pools under the top edge, like the dialogs.
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                0f to accent.copy(alpha = 0.16f), 1f to Color.Transparent,
                                center = Offset(size.width / 2, 0f), radius = size.width * 0.9f,
                            ),
                        )
                    }
                    .glass(shape)
                    .navigationBarsPadding()
                    .padding(start = Space.gutter, end = Space.gutter, bottom = Space.gutter),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.l), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)))
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}

/**
 * The date, centred like everything else in the sheet: the Hijri day as a large gold numeral,
 * the month with its year on one line, and the Gregorian date beneath.
 */
@Composable
private fun DateHeader(date: LocalDate, hijri: HijriDay) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Inter for the numeral, like the countdown: the serif's "1" reads as "l".
        Text(
            numbers.format(hijri.day.toLong()),
            style = Type.countdown.copy(
                fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2.5).sp, brush = GoldFoil,
                // Proportional figures: a tabular "1" leaves a gap in a numeral this large.
                fontFeatureSettings = "pnum",
            ),
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xs))
        // The year in gold after the month, the same key as the gold Hijri numbers on the calendar.
        Text(
            buildAnnotatedString {
                append(IslamicCalendar.monthName(context, hijri.month))
                append(" ")
                withStyle(SpanStyle(color = Nur.gold)) { append(numbers.format(hijri.year.toLong())) }
            },
            style = Type.displayM.copy(fontSize = 28.sp, lineHeight = 32.sp, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(
                R.string.reminder_gregorian_date,
                date.dayOfWeek.getDisplayName(DateStyle.FULL, locale),
                date.dayOfMonth,
                date.month.getDisplayName(DateStyle.FULL, locale),
                date.year,
            ),
            style = Type.bodyM.copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** The meaning in the display serif at a reading size, with lines balanced rather than ragged. */
private val MeaningStyle = TextStyle(
    fontFamily = InstrumentSerif,
    fontSize = 21.sp,
    lineHeight = 28.sp,
    color = Nur.textPrimary,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Heading,
)

/**
 * A reminder set for reading, centred: the Arabic large when it is at most [arabicMaxChars]
 * long, the meaning (clipped to [meaningMaxLines]), a gold ornament, and the source in gold
 * capitals. Shared by Today's card and the day sheet so the two read the same.
 */
@Composable
internal fun ReminderReading(reminder: DayReminder, arabicMaxChars: Int, meaningMaxLines: Int = Int.MAX_VALUE) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        reminder.arabic?.takeIf { it.length <= arabicMaxChars }?.let { arabic ->
            Text(
                arabic,
                style = Type.arabicReading.copy(
                    fontSize = if (arabic.length <= 60) 30.sp else 26.sp,
                    lineHeight = 1.9.em,
                    textAlign = TextAlign.Center,
                    // The generous leading goes between lines only, so the space above and below
                    // the Arabic is the spacing the layout sets, not the font's reserve.
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xl))
        }
        // The meanings are English for now, so the Latin serif is right in every language.
        Text(withHonorifics(reminder.meaning), style = MeaningStyle, maxLines = meaningMaxLines, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Space.xl))
        GoldOrnament()
        Spacer(Modifier.height(Space.l))
        Text(
            reminder.reference.uppercase(Locale.getDefault()),
            style = Type.overline.copy(color = Nur.gold, letterSpacing = 1.6.sp),
            textAlign = TextAlign.Center,
        )
    }
}

/** A glass pill with the firm confirm haptic, like the primary action beside it. */
@Composable
private fun SaveButton(modifier: Modifier, onClick: () -> Unit) {
    GlassSurface(modifier.height(56.dp), RoundedCornerShape(50), level = 2, onClick = onClick, haptic = true) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, null, tint = Nur.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(stringResource(R.string.day_design_save), style = Type.label.copy(fontSize = 16.sp), maxLines = 1)
        }
    }
}

/** The small label over the reminder: what kind of text it is. Also on the day card. */
@Composable
internal fun kindLabel(kind: ReminderKind): String = stringResource(
    when (kind) {
        ReminderKind.VERSE -> R.string.reminder_kind_verse
        ReminderKind.HADITH -> R.string.reminder_kind_hadith
        ReminderKind.FACT -> R.string.reminder_kind_fact
    },
)

/** For apps that take text rather than the image. */
private fun shareText(reminder: DayReminder): String =
    listOfNotNull(reminder.arabic, reminder.meaning, "— ${reminder.reference}").joinToString("\n\n")

/** Writes the card to Pictures/Adhkaar. Hidden from the gallery until it is complete. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToPictures(context: Context, bitmap: Bitmap, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Adhkaar")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }.getOrNull() ?: return false
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }.getOrDefault(false)
    if (!written) {
        resolver.delete(uri, null, null)
        return false
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    return true
}
