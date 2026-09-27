package org.adhkaar.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionDhikr
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** The three looks a card can take. */
enum class CardStyle { MIDNIGHT, DAWN, PARCHMENT }

private data class CardPalette(
    val background: List<Color>,
    val glow: Color,
    val pattern: Color,
    val frame: List<Color>,
    val title: List<Color>,
    val arabic: Color,
    val meaning: Color,
    val quiet: Color,
    val swatch: Color,
)

private fun palette(style: CardStyle) = when (style) {
    CardStyle.MIDNIGHT -> CardPalette(
        background = listOf(Color(0xFF0B1433), Color(0xFF070B1F), Color(0xFF04060F)),
        glow = Auras.evening.glows[0],
        pattern = Color(0xFFF2CF8A).copy(alpha = 0.10f),
        frame = listOf(Color(0xFFFFE9B8), Color(0xFFC9973F), Color(0xFFFFE9B8)),
        title = listOf(Color(0xFFFFF1CC), Color(0xFFE2B866), Color(0xFFB8862F)),
        arabic = Color(0xFFF7F3E8),
        meaning = Color.White.copy(alpha = 0.74f),
        quiet = Color.White.copy(alpha = 0.5f),
        swatch = Color(0xFF16245C),
    )
    CardStyle.DAWN -> CardPalette(
        background = listOf(Color(0xFF241238), Color(0xFF6B2F55), Color(0xFFD9825B)),
        glow = Color(0xFFFFC98B),
        pattern = Color.White.copy(alpha = 0.08f),
        frame = listOf(Color(0xFFFFF4DE), Color(0xFFFFC98B), Color(0xFFFFF4DE)),
        title = listOf(Color(0xFFFFF7E6), Color(0xFFFFD49A), Color(0xFFF2A76B)),
        arabic = Color.White,
        meaning = Color.White.copy(alpha = 0.82f),
        quiet = Color.White.copy(alpha = 0.62f),
        swatch = Color(0xFFB0566A),
    )
    CardStyle.PARCHMENT -> CardPalette(
        background = listOf(Color(0xFFF7EFDD), Color(0xFFF1E5CB), Color(0xFFE6D3AE)),
        glow = Color(0xFFFFFFFF),
        pattern = Color(0xFF8A6428).copy(alpha = 0.09f),
        frame = listOf(Color(0xFFB48A3C), Color(0xFF7A5A22), Color(0xFFB48A3C)),
        title = listOf(Color(0xFF9A6F28), Color(0xFF7A5519), Color(0xFF9A6F28)),
        arabic = Color(0xFF231810),
        meaning = Color(0xFF4A3A28),
        quiet = Color(0xFF7A6650),
        swatch = Color(0xFFEBDCB9),
    )
}

/**
 * A preview of the card with a style picker, then the system share sheet. The card is captured
 * exactly as shown, so what you see is what is sent.
 */
@Composable
fun ShareSheet(dhikr: SessionDhikr, quran: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var style by rememberSaveable { mutableStateOf(CardStyle.MIDNIGHT) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = Space.gutter)
                    .navigationBarsPadding()
                    .clickable(remember { MutableInteractionSource() }, null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShareCard(
                    dhikr,
                    style,
                    quran = quran,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                )
                Spacer(Modifier.height(Space.l))
                StylePicker(style) { style = it }
                Spacer(Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    SecondaryButton(stringResource(R.string.share_cancel), Modifier.weight(1f), onClick = onDismiss)
                    PrimaryButton(stringResource(R.string.share_action), Modifier.weight(1f), icon = Icons.Rounded.IosShare) {
                        scope.launch {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            share(context, dhikr, bitmap)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePicker(selected: CardStyle, onSelect: (CardStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        CardStyle.entries.forEach { style ->
            val isSelected = style == selected
            val edge by animateColorAsState(if (isSelected) Nur.gold else Color.Transparent, label = "styleEdge")
            Row(
                Modifier
                    .height(40.dp)
                    .glass(RoundedCornerShape(50), level = if (isSelected) 2 else 1)
                    .border(1.dp, edge, RoundedCornerShape(50))
                    .pressable(scale = 0.95f) { onSelect(style) }
                    .padding(start = 6.dp, end = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(palette(style).background))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    stringResource(
                        when (style) {
                            CardStyle.MIDNIGHT -> R.string.share_style_midnight
                            CardStyle.DAWN -> R.string.share_style_dawn
                            CardStyle.PARCHMENT -> R.string.share_style_parchment
                        },
                    ),
                    style = Type.label.copy(fontSize = 13.sp, color = if (isSelected) Nur.textPrimary else Nur.textSecondary),
                )
            }
        }
    }
}

/**
 * The card: a 4:5 poster. A mihrab arch in a fine double gold rule frames the words over a
 * faint eight-fold star lattice. Inside it, the name in gold-foil capitals, the Arabic as the
 * hero (sized to fit, never cut off), a Rub el Hizb, the meaning, and the source.
 */
@Composable
fun ShareCard(dhikr: SessionDhikr, style: CardStyle = CardStyle.MIDNIGHT, modifier: Modifier = Modifier, quran: Boolean = false) {
    val p = palette(style)
    val arabicShare = if (dhikr.arabic.isBlank()) 0f else
        (dhikr.arabic.length / (dhikr.arabic.length + dhikr.translation.length * 0.75f)).coerceIn(0.42f, 0.68f)
    Box(
        modifier
            .aspectRatio(4f / 5f)
            .background(Brush.verticalGradient(p.background)),
    ) {
        // Light and pattern behind everything.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(p.glow.copy(alpha = if (style == CardStyle.PARCHMENT) 0.5f else 0.35f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.36f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width / 2, size.height * 0.36f),
            )
        }
        StarLattice(p.pattern, Modifier.fillMaxSize())
        MihrabFrame(p.frame, Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp))

        Column(
            Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 72.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                dhikr.title.uppercase() + if (dhikr.count > 1) "  ·  " + pluralStringResource(R.plurals.counter_times, dhikr.count, dhikr.count) else "",
                style = Type.overline.copy(brush = Brush.linearGradient(p.title), letterSpacing = 2.5.sp, fontSize = 10.sp),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            if (dhikr.arabic.isNotBlank()) {
                Box(Modifier.fillMaxWidth().weight(arabicShare), contentAlignment = Alignment.Center) {
                    BasicText(
                        dhikr.arabic,
                        // An ayah is set in the mushaf's own script.
                        style = Type.arabicReading.copy(
                            color = p.arabic, textAlign = TextAlign.Center, lineHeight = 1.85.em,
                            fontFamily = if (quran) org.adhkaar.app.ui.theme.UthmanicHafs else Type.arabicReading.fontFamily,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 36.sp, stepSize = 1.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Ornament(p.frame, p.title)
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.fillMaxWidth().weight(if (dhikr.arabic.isNotBlank()) 1f - arabicShare else 1f), contentAlignment = Alignment.Center) {
                BasicText(
                    withHonorifics(dhikr.translation),
                    style = Type.bodyL.copy(
                        fontFamily = org.adhkaar.app.ui.theme.InstrumentSerif,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 1.35.em,
                        color = if (dhikr.arabic.isBlank()) p.arabic else p.meaning,
                        textAlign = TextAlign.Center,
                    ),
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = if (dhikr.arabic.isBlank()) 30.sp else 19.sp, stepSize = 0.5.sp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(dhikr.reference, style = Type.caption.copy(color = p.quiet, fontSize = 11.sp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Brand, quietly, in the frame's bottom band.
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (style != CardStyle.PARCHMENT) NurOrb(10.dp, float = false)
            if (style != CardStyle.PARCHMENT) Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.app_brand_caps), style = Type.overline.copy(color = p.quiet, fontSize = 7.sp, letterSpacing = 2.sp))
        }
    }
}

/**
 * A faint lattice of eight-point stars (khatam), strongest at the edges and fading toward the
 * centre so the words sit on clear ground.
 */
@Composable
private fun StarLattice(color: Color, modifier: Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val step = 34.dp.toPx()
        val r = step * 0.34f
        var row = 0
        var y = -step / 2
        while (y < size.height + step) {
            var x = if (row % 2 == 0) -step / 2 else 0f
            while (x < size.width + step) {
                star(Offset(x, y), r, color)
                x += step
            }
            y += step / 2
            row++
        }
        // Fade out towards the middle.
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black,
                center = center, radius = size.maxDimension * 0.62f,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val stroke = Stroke(0.7.dp.toPx())
    val s = Size(r * 1.42f, r * 1.42f)
    val tl = Offset(c.x - s.width / 2, c.y - s.height / 2)
    drawRect(color, tl, s, style = stroke)
    rotate(45f, c) { drawRect(color, tl, s, style = stroke) }
}

/** A pointed mihrab arch in a double rule, with a small star at its apex. */
@Composable
private fun MihrabFrame(gold: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        fun arch(inset: Float): Path {
            val l = inset
            val r = size.width - inset
            val t = inset
            val b = size.height - inset
            val w = r - l
            val spring = t + w * 0.42f
            val cx = (l + r) / 2
            return Path().apply {
                moveTo(l, b)
                lineTo(l, spring)
                cubicTo(l, spring - w * 0.24f, cx - w * 0.2f, t + w * 0.08f, cx, t)
                cubicTo(cx + w * 0.2f, t + w * 0.08f, r, spring - w * 0.24f, r, spring)
                lineTo(r, b)
                close()
            }
        }
        val brush = Brush.linearGradient(gold, start = Offset.Zero, end = Offset(size.width, size.height))
        drawPath(arch(0f), brush, style = Stroke(1.4.dp.toPx()))
        drawPath(arch(5.dp.toPx()), brush, alpha = 0.45f, style = Stroke(0.7.dp.toPx()))
        // Apex star
        val apex = Offset(size.width / 2, -1.dp.toPx())
        val a = 5.dp.toPx()
        val s = Path().apply {
            for (i in 0 until 16) {
                val radius = if (i % 2 == 0) a else a * 0.45f
                val angle = Math.toRadians(i * 22.5 - 90)
                val pt = Offset(apex.x + (radius * cos(angle)).toFloat(), apex.y + (radius * sin(angle)).toFloat())
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }
        drawPath(s, brush)
    }
}

/** A filled eight-point star between two tapering rules. */
@Composable
private fun Ornament(gold: List<Color>, rule: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, rule[1]))))
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.size(12.dp)) {
            val c = center
            val a = size.minDimension / 2
            val path = Path().apply {
                for (i in 0 until 16) {
                    val radius = if (i % 2 == 0) a else a * 0.5f
                    val angle = Math.toRadians(i * 22.5 - 90)
                    val pt = Offset(c.x + (radius * cos(angle)).toFloat(), c.y + (radius * sin(angle)).toFloat())
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(path, Brush.linearGradient(gold))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(48.dp).height(1.dp).background(Brush.horizontalGradient(listOf(rule[1], Color.Transparent))))
    }
}

private fun share(context: Context, dhikr: SessionDhikr, bitmap: Bitmap) {
    val text = listOf(dhikr.arabic, dhikr.translation, "— ${dhikr.reference}").filter { it.isNotBlank() }.joinToString("\n\n")
    shareImage(context, bitmap, "adhkaar-${dhikr.id}", text, context.getString(R.string.share_chooser_title))
}

/**
 * Hands [bitmap] to the system share sheet, with [text] for apps that take words rather than
 * images. The PNG goes in the cache's shared/ folder, which the FileProvider exposes.
 */
internal fun shareImage(context: Context, bitmap: Bitmap, name: String, text: String, chooserTitle: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "$name.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
