package org.adhkaar.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.PageFonts
import org.adhkaar.app.data.quran.PageTheme
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.ui.theme.QcfHeadings
import org.adhkaar.app.ui.theme.Type
import org.adhkaar.app.ui.theme.UthmanicHafs

/** The paper, the ink and the accents of a page. */
@Immutable
data class PageColors(
    val paper: Color,
    val ink: Color,
    val frame: Color,
    /** Chips and bars drawn over the page. */
    val band: Color,
    /** Behind the ayah that is chosen. */
    val highlight: Color,
    /** The page's running heading and number. */
    val accent: Color,
) {
    companion object {
        val sepia = PageColors(Color(0xFFFBF5E6), Color(0xFF1E160C), Color(0xFFA9853F), Color(0xFFF1E6CB), Color(0x40D8B46A), Color(0xFF7D5A1C))
        val night = PageColors(Color(0xFF0F1115), Color(0xFFE9E3D4), Color(0xFFB9975A), Color(0xFF1B1F27), Color(0x33C9A55E), Color(0xFFCDAE72))
        val white = PageColors(Color(0xFFFFFFFF), Color(0xFF0D0D0D), Color(0xFF2A7FA6), Color(0xFFEAF3F8), Color(0x332A7FA6), Color(0xFF1F6A8C))

        fun of(theme: PageTheme, night: Boolean) = when (theme) {
            PageTheme.SEPIA -> sepia
            PageTheme.NIGHT -> this.night
            PageTheme.WHITE -> white
            PageTheme.AUTO -> if (night) this.night else sepia
        }
    }
}

/**
 * The share of a line's width one em takes in the Complex's page fonts: every full line is about
 * 14.25 em wide, so this sets the size at which a line fills the page from edge to edge.
 */
private const val LINE_EMS = 14.25f

/**
 * A page of the Madinah mushaf exactly as printed, in the Complex's own font for it: the juz and
 * the surah above, fifteen lines, the page number below. Tapping a word chooses its ayah; tapping
 * anywhere else calls [onBackground].
 */
@Composable
fun MushafPage(
    mushaf: Mushaf,
    number: Int,
    colors: PageColors,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
    onBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val page = mushaf.pages[number - 1]
    val opening = number <= 2
    var attempt by remember { mutableStateOf(0) }
    val family by produceState<FontFamily?>(null, number, attempt) { value = PageFonts.family(context, number) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier
                .fillMaxSize()
                .background(colors.paper)
                .pointerInput(number) { detectTapGestures { onBackground() } }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            // The running heading: the juz on the right, the surah on the left, as printed.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الجزء " + Quran.arabicDigits(mushaf.juzOfPage(number)), style = running(colors.accent))
                Text("سورة " + Quran.arabicNames[mushaf.surahsOfPage(number).last() - 1], style = running(colors.accent))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                val slot = maxHeight / 15
                val width = maxWidth
                val size = with(LocalDensity.current) { (maxWidth.toPx() / LINE_EMS).toSp() }.let { minOf(it.value, with(LocalDensity.current) { (slot * 0.95f).toSp().value }).sp }
                val loaded = family
                if (loaded == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.quran_page_loading),
                            style = Type.bodyM.copy(color = colors.ink.copy(alpha = 0.6f)),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { attempt++ } },
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = if (opening) Arrangement.Center else Arrangement.SpaceBetween,
                    ) {
                        page.lines.forEach { line ->
                            when (line) {
                                is Mushaf.Line.Header -> SurahHeading(line.surah, colors, width, slot)
                                Mushaf.Line.Basmala -> Box(Modifier.fillMaxWidth().height(slot), contentAlignment = Alignment.Center) {
                                    Text(Quran.HEADING_BASMALA.toChar().toString(), style = glyphs(QcfHeadings, colors.ink, size * 0.95f))
                                }
                                is Mushaf.Line.Text -> GlyphLine(line, loaded, colors, size, slot * (if (opening) 1.35f else 1f), selectedAyah, onAyah)
                            }
                        }
                    }
                }
            }
            Text(Quran.arabicDigits(number), style = running(colors.accent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun running(color: Color) = TextStyle(fontFamily = UthmanicHafs, fontSize = 14.sp, color = color, textDirection = TextDirection.Rtl)

private fun glyphs(family: FontFamily, color: Color, size: TextUnit) = TextStyle(
    fontFamily = family, fontSize = size, color = color, textDirection = TextDirection.Rtl, lineHeight = 1.5.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** One printed line in the page's font. A tap finds the word under the finger, and so its ayah. */
@Composable
private fun GlyphLine(
    line: Mushaf.Line.Text,
    family: FontFamily,
    colors: PageColors,
    size: TextUnit,
    height: Dp,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val chosen = remember(selectedAyah, line) {
        if (selectedAyah == null) null else line.ayahOf.indices.filter { line.ayahOf[it] == selectedAyah }.let { if (it.isEmpty()) null else it.first()..it.last() }
    }
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(
            line.glyphs,
            style = glyphs(family, colors.ink, size),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            onTextLayout = { layout = it },
            modifier = Modifier
                .drawBehind {
                    val l = layout ?: return@drawBehind
                    chosen?.let { drawPath(l.getPathForRange(it.first, it.last + 1), colors.highlight) }
                }
                .pointerInput(line) {
                    detectTapGestures { at ->
                        val l = layout ?: return@detectTapGestures
                        val i = l.getOffsetForPosition(at).coerceIn(0, line.glyphs.length - 1)
                        onAyah(line.ayahOf[i])
                    }
                },
        )
    }
}

/** A surah's heading as printed: the Complex's framed band, with "سورة" and the name in it. */
@Composable
private fun SurahHeading(surah: Int, colors: PageColors, width: Dp, slot: Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The frame glyph is sized to span the line; the name sits inside it at its own size.
    val frameSize = remember(width) {
        val probe = 40.sp
        val w = measurer.measure(Quran.HEADING_FRAME.toChar().toString(), TextStyle(fontFamily = QcfHeadings, fontSize = probe)).size.width
        with(density) { (probe.toPx() * width.toPx() / w).toSp() }
    }
    Box(Modifier.fillMaxWidth().height(slot), contentAlignment = Alignment.Center) {
        Text(Quran.HEADING_FRAME.toChar().toString(), style = glyphs(QcfHeadings, colors.frame, frameSize), maxLines = 1, softWrap = false)
        // Each glyph on its own, so the Latin codes they sit on can't reorder them.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = with(density) { (slot * 0.5f).toSp() }
            Text(Quran.HEADING_SURAH.toChar().toString(), style = glyphs(QcfHeadings, colors.ink, name))
            Text(Quran.headingCodes[surah - 1].toChar().toString(), style = glyphs(QcfHeadings, colors.ink, name))
        }
    }
}
