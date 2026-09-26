package org.adhkaar.app.ui.share

import android.icu.text.NumberFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.DayReminder
import org.adhkaar.app.data.HijriDay
import org.adhkaar.app.data.HijriMonthSpan
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.monthName
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.reminder.kindLabel
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Inter
import org.adhkaar.app.ui.theme.InstrumentSerif
import org.adhkaar.app.ui.theme.Type
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The exported image: a 4:5 portrait, the shape feeds and status updates show whole. */
const val DAY_CARD_WIDTH_PX = 1080
const val DAY_CARD_HEIGHT_PX = 1350

// The card is laid out on a 360 × 450 canvas at 3 px per unit, with no font scaling, so the
// image is identical on every phone whatever its screen density or text size setting.
private val CardDensity = Density(density = DAY_CARD_WIDTH_PX / 360f, fontScale = 1f)

/** One margin on every side (72 px), so the card's edges read as a deliberate frame. */
private val Margin = 24.dp

/** Arabic longer than this would crowd the meaning off the panel; the meaning carries the day. */
private const val CARD_ARABIC_MAX_CHARS = 120

private object DayColors {
    // The evening sky of the app, a touch deeper so light text holds its contrast when compressed.
    val base = listOf(Color(0xFF0C1538), Color(0xFF080C22), Color(0xFF05060F))
    val glows = Auras.evening.glows
    /** The warm light low in the frame, as if from a lamp below the words. */
    val lamp = Color(0xFFFFB86B)
    val gold = listOf(Color(0xFFFFEBC2), Color(0xFFF2CF8A), Color(0xFFC99A45))
    val goldText = Color(0xFFF2CF8A)
    val goldInk = Color(0xFF2B1E07)
    val lattice = Color(0xFFF2CF8A).copy(alpha = 0.07f)
}

/** Gold foil: light at the top edge, deeper below, like the gilding on a printed calendar. */
internal val GoldFoil = Brush.verticalGradient(DayColors.gold)

/**
 * Draws the day card at exactly [DAY_CARD_WIDTH_PX] × [DAY_CARD_HEIGHT_PX], recorded into
 * [layer] for export, and shows it scaled down to [modifier]'s width. What is shown is what is
 * saved or shared. With [visible] false it is only recorded, for screens that export the card
 * without showing it. [hijriOf] dates the days of the week that fall before the month began.
 */
@Composable
fun DayCardFrame(
    date: LocalDate,
    hijri: HijriDay,
    span: HijriMonthSpan,
    reminder: DayReminder,
    hijriOf: (LocalDate) -> HijriDay,
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    BoxWithConstraints(modifier.aspectRatio(DAY_CARD_WIDTH_PX / DAY_CARD_HEIGHT_PX.toFloat())) {
        val scale = constraints.maxWidth / DAY_CARD_WIDTH_PX.toFloat()
        Box(
            Modifier
                // Full size whatever room the parent has; the parent centres the overflow, and the
                // scale (about the centre) brings it back inside.
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(DAY_CARD_WIDTH_PX, DAY_CARD_HEIGHT_PX))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    if (visible) drawLayer(layer)
                },
        ) {
            CompositionLocalProvider(LocalDensity provides CardDensity) {
                DayCard(date, hijri, span, reminder, hijriOf)
            }
        }
    }
}

/**
 * The day as a small poster in the app's own light: the Hijri date large in gold with the
 * Gregorian beside it, the week in a glass band, and the reminder as the hero on a glass panel,
 * over the evening sky and a faint star lattice. The Adhkaar mark closes it.
 */
@Composable
private fun DayCard(date: LocalDate, hijri: HijriDay, span: HijriMonthSpan, reminder: DayReminder, hijriOf: (LocalDate) -> HijriDay) {
    val locale = Locale.getDefault()
    // Years and days without grouping ("1448", never "1,448"), in the locale's digits.
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(DayColors.base))) {
        Sky(Modifier.fillMaxSize())
        StarLattice(DayColors.lattice, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(Margin), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            DateLockup(date, hijri, numbers)
            Spacer(Modifier.height(16.dp))
            WeekBand(date, span, numbers, hijriOf, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            ReadingPanel(reminder, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(14.dp))
            Wordmark()
        }
    }
}

/**
 * Evening light: the app's blue aura high on the right, violet low on the left, and a warm glow
 * rising from the foot of the card behind the panel, so the reminder sits in the brightest place.
 */
@Composable
private fun Sky(modifier: Modifier) {
    Canvas(modifier) {
        fun glow(color: Color, alpha: Float, x: Float, y: Float, radius: Float) {
            val c = Offset(size.width * x, size.height * y)
            val r = size.width * radius
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), c, r), r, c)
        }
        glow(DayColors.glows[0], 0.42f, 0.86f, 0.02f, 0.85f)
        glow(DayColors.glows[2], 0.10f, 0.18f, 0.12f, 0.5f)
        glow(DayColors.glows[1], 0.22f, 0.02f, 0.58f, 0.75f)
        glow(DayColors.lamp, 0.16f, 0.5f, 1.04f, 0.8f)
    }
}

/**
 * The Hijri day as a large gold numeral, a fine gold rule, then the month in the display serif,
 * the year in gold capitals and the Gregorian date beneath. Centred as one lockup.
 */
@Composable
private fun DateLockup(date: LocalDate, hijri: HijriDay, numbers: NumberFormat) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val gregorian = stringResource(
        R.string.reminder_gregorian_date,
        date.dayOfWeek.getDisplayName(DateStyle.FULL, locale),
        date.dayOfMonth,
        date.month.getDisplayName(DateStyle.FULL, locale),
        date.year,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            numbers.format(hijri.day.toLong()),
            // Inter, not the serif: the serif's "1" reads as "l" at this size (see Type.countdown).
            style = TextStyle(
                fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 60.sp, lineHeight = 60.sp,
                letterSpacing = (-2.5).sp, brush = GoldFoil,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(56.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, DayColors.goldText.copy(alpha = 0.7f), Color.Transparent))),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f, fill = false)) {
            // The year sits on the month's line, in gold: one date, not a separate caption.
            val year = stringResource(R.string.day_design_year_ah, numbers.format(hijri.year.toLong()))
            FitText(
                buildAnnotatedString {
                    append(IslamicCalendar.monthName(context, hijri.month))
                    append(" ")
                    withStyle(SpanStyle(color = DayColors.goldText)) { append(year) }
                },
                Type.displayM.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp, color = Color.White),
                26.sp, TextAlign.Start,
            )
            Spacer(Modifier.height(5.dp))
            FitText(gregorian, ui(10.sp, FontWeight.Medium, Color.White.copy(alpha = 0.62f)), 10.sp, TextAlign.Start)
        }
    }
}

/**
 * The week around the day on a glass band, Sunday first: the Arabic day names over the Hijri
 * dates, the day itself on gold. Days before the month began are dated by [hijriOf]; days after
 * an end that hasn't been announced yet (29 or 30 days) show "?", never a guess.
 */
@Composable
private fun WeekBand(date: LocalDate, span: HijriMonthSpan, numbers: NumberFormat, hijriOf: (LocalDate) -> HijriDay, modifier: Modifier) {
    val names = stringArrayResource(R.array.day_card_weekdays)
    val unknown = stringResource(R.string.day_card_unknown_day)
    val sunday = date.minusDays(date.dayOfWeek.value % 7L)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), shape)
            .padding(4.dp),
    ) {
        (0 until 7).forEach { i ->
            val day = sunday.plusDays(i.toLong())
            val n = ChronoUnit.DAYS.between(span.start, day).toInt() + 1
            val length = span.length
            val inMonth = n >= 1 && n <= (length ?: 29)
            val label = when {
                n < 1 -> numbers.format(hijriOf(day).day.toLong())
                inMonth -> numbers.format(n.toLong())
                length == null -> unknown
                else -> numbers.format((n - length).toLong())
            }
            val isDay = day == date
            val itemShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isDay) Modifier.clip(itemShape).background(GoldFoil) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FitText(
                    names[i],
                    ui(7.5.sp, FontWeight.SemiBold, if (isDay) DayColors.goldInk.copy(alpha = 0.7f) else DayColors.goldText.copy(alpha = 0.75f), tracking = 0.4.sp),
                    7.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    label,
                    style = ui(
                        15.sp, if (isDay) FontWeight.Bold else FontWeight.SemiBold,
                        when {
                            isDay -> DayColors.goldInk
                            inMonth -> Color.White.copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.4f)
                        },
                        tight = true,
                    ).copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The reminder, the hero of the card, on a glass panel of the week band's family, lit softly
 * from the top: the kind of text, the Arabic when it is short, the meaning as large as it fits
 * with balanced lines, an ornament, and the source in gold.
 */
@Composable
private fun ReadingPanel(reminder: DayReminder, modifier: Modifier) {
    val locale = Locale.getDefault()
    val arabic = reminder.arabic?.takeIf { it.length <= CARD_ARABIC_MAX_CHARS }
    val meaning = withHonorifics(reminder.meaning)
    // Room shared by length, so neither the Arabic nor the meaning is squeezed.
    val arabicShare = arabic?.let { (it.length / (it.length + meaning.length * 0.7f)).coerceIn(0.32f, 0.45f) } ?: 0f
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))))
            .drawBehind {
                val top = Offset(size.width / 2, 0f)
                drawCircle(Brush.radialGradient(listOf(DayColors.goldText.copy(alpha = 0.12f), Color.Transparent), top, size.width * 0.7f), size.width * 0.7f, top)
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), shape),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FitText(kindLabel(reminder.kind).uppercase(locale), ui(8.5.sp, FontWeight.SemiBold, DayColors.goldText, tracking = 2.2.sp), 8.5.sp)
            Spacer(Modifier.height(12.dp))
            if (arabic != null) {
                Box(Modifier.fillMaxWidth().weight(arabicShare), contentAlignment = Alignment.Center) {
                    BasicText(
                        arabic,
                        style = Type.arabicReading.copy(
                            color = Color.White, textAlign = TextAlign.Center, lineHeight = 1.8.em,
                            // Leading between lines only, so the Arabic can grow into all of its room.
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 30.sp, stepSize = 0.5.sp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GoldOrnament(rule = 36.dp, star = 8.dp)
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f - arabicShare), contentAlignment = Alignment.Center) {
                BasicText(
                    meaning,
                    // The meanings are English for now, so the Latin serif is safe here in every language.
                    style = TextStyle(
                        fontFamily = InstrumentSerif, color = Color.White, lineHeight = 1.22.em,
                        textAlign = TextAlign.Center, lineBreak = LineBreak.Heading,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = if (arabic == null) 28.sp else 22.sp, stepSize = 0.5.sp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (arabic == null) {
                GoldOrnament(rule = 36.dp, star = 8.dp)
                Spacer(Modifier.height(12.dp))
            }
            FitText(reminder.reference.uppercase(locale), ui(8.5.sp, FontWeight.SemiBold, DayColors.goldText, tracking = 1.6.sp), 8.5.sp)
        }
    }
}

/** An eight-point star (khatam) of outer radius [radius]; [waist] sets how deep its notches cut. */
private fun starPath(c: Offset, radius: Float, waist: Float) = Path().apply {
    for (i in 0 until 16) {
        val r = if (i % 2 == 0) radius else radius * waist
        val angle = Math.toRadians(i * 22.5 - 90)
        val pt = Offset(c.x + (r * cos(angle)).toFloat(), c.y + (r * sin(angle)).toFloat())
        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
    }
    close()
}

/**
 * A gold eight-point star between two hair-thin rules that fade out at their ends: the divider
 * of the share cards, used between the words and their source on Today and in the day sheet too.
 */
@Composable
internal fun GoldOrnament(rule: Dp = 40.dp, star: Dp = 10.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(rule).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, DayColors.goldText.copy(alpha = 0.7f)))))
        Spacer(Modifier.width(star * 0.8f))
        Canvas(Modifier.size(star)) { drawPath(starPath(center, size.minDimension / 2, 0.5f), GoldFoil) }
        Spacer(Modifier.width(star * 0.8f))
        Box(Modifier.width(rule).height(1.dp).background(Brush.horizontalGradient(listOf(DayColors.goldText.copy(alpha = 0.7f), Color.Transparent))))
    }
}

/**
 * A faint lattice of eight-point stars, strongest at the edges and fading toward the centre so
 * the words sit on clear ground (the share cards' pattern).
 */
@Composable
private fun StarLattice(color: Color, modifier: Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val step = 30.dp.toPx()
        val r = step * 0.34f
        val stroke = Stroke(0.6.dp.toPx())
        val side = Size(r * 1.42f, r * 1.42f)
        var row = 0
        var y = -step / 2
        while (y < size.height + step) {
            var x = if (row % 2 == 0) -step / 2 else 0f
            while (x < size.width + step) {
                val topLeft = Offset(x - side.width / 2, y - side.height / 2)
                drawRect(color, topLeft, side, style = stroke)
                rotate(45f, Offset(x, y)) { drawRect(color, topLeft, side, style = stroke) }
                x += step
            }
            y += step / 2
            row++
        }
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black,
                center = center, radius = size.maxDimension * 0.62f,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** The app's mark: the orb with its crescent, and the name in the display serif. */
@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NurOrb(14.dp, aura = Auras.evening, float = false)
        Spacer(Modifier.width(7.dp))
        BasicText(
            stringResource(R.string.app_name),
            style = TextStyle(fontFamily = InstrumentSerif, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp, color = Color.White.copy(alpha = 0.86f)),
        )
    }
}

/** One line that shrinks rather than wraps or cuts off (long month names, translated labels). */
@Composable
private fun FitText(text: String, style: TextStyle, max: TextUnit, align: TextAlign = TextAlign.Center) =
    FitText(AnnotatedString(text), style, max, align)

@Composable
private fun FitText(text: AnnotatedString, style: TextStyle, max: TextUnit, align: TextAlign = TextAlign.Center) {
    BasicText(
        text,
        style = style.copy(textAlign = align),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 5.sp, maxFontSize = max, stepSize = 0.25.sp),
    )
}

/**
 * Card text in the interface typeface (Inter, or Noto Sans Arabic for Arabic and Urdu). [tracking]
 * only applies to Latin: spacing letters out breaks Arabic joins. [tight] trims the line to the
 * glyphs, for numbers centred in a small box.
 */
private fun ui(size: TextUnit, weight: FontWeight, color: Color, tracking: TextUnit = 0.sp, tight: Boolean = false) = Type.label.copy(
    fontSize = size,
    lineHeight = if (tight) 1.em else 1.25.em,
    fontWeight = weight,
    color = color,
    letterSpacing = if (Type.uiFamily == Inter) tracking else 0.sp,
)
