package org.adhkaar.app.ui.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.PageTheme
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.ui.theme.QcfHeadings
import org.adhkaar.app.ui.theme.UthmanicHafs

/** The paper, the ink and the accents of a page. */
@Immutable
data class PageColors(
    val paper: Color,
    val ink: Color,
    /** The surah heading's frame. */
    val frame: Color,
    /** Chips and bars drawn over the page. */
    val band: Color,
    /** Behind the ayah that is chosen. */
    val highlight: Color,
    /** The page's running heading and number. */
    val accent: Color,
) {
    companion object {
        val sepia = PageColors(Color(0xFFFBF5E6), Color(0xFF1E160C), Color(0xFF9A7A3C), Color(0xFFF1E6CB), Color(0x45D8B46A), Color(0xFF7D5A1C))
        val night = PageColors(Color(0xFF0F1115), Color(0xFFE9E3D4), Color(0xFFB9975A), Color(0xFF1B1F27), Color(0x38C9A55E), Color(0xFFCDAE72))
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
 * How far a line may be stretched or narrowed to meet both edges. The printed mushaf fills its
 * lines by drawing letters longer or shorter; a line here is scaled across within these bounds,
 * which the eye doesn't notice, and any remainder goes into the spaces between words.
 */
private const val STRETCH_MAX = 1.08f
private const val SQUEEZE_MAX = 0.84f

/** Where the baseline sits in a line's slot. */
private const val BASELINE = 0.64f

/** A word (or an ayah's number) placed on the page. */
private class Placed(val layout: TextLayoutResult, val x: Float, val top: Float, val scaleX: Float, val ayah: Int, val box: Rect)

/** A heading glyph placed on the page, drawn in [color]. */
private class Ornament(val layout: TextLayoutResult, val x: Float, val top: Float, val color: Color)

private class PageLayout(val words: List<Placed>, val ornaments: List<Ornament>)

/**
 * A page of the Madinah mushaf with the printed line breaks: the juz and the surah above, fifteen
 * lines each meeting both edges, surah headings in the Complex's own frame, the page number below.
 * Drawn as one surface. Tapping a word chooses its ayah; tapping elsewhere calls [onBackground].
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
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier.fillMaxSize().background(colors.paper).padding(horizontal = 14.dp, vertical = 6.dp)) {
            // The running heading: the juz on the right, the surah on the left, as printed.
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الجزء " + Quran.arabicDigits(mushaf.juzOfPage(number)), style = running(colors.accent))
                Text("سورة " + Quran.arabicNames[mushaf.surahsOfPage(number).last() - 1], style = running(colors.accent))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
                val width = with(density) { maxWidth.toPx() }
                val height = with(density) { maxHeight.toPx() }
                val size = remember(width) { lineSize(measurer, mushaf, width) }
                val layout = remember(number, width, height, colors) { layOut(measurer, mushaf, number, width, height, size, colors) }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(number, layout) {
                            detectTapGestures { at ->
                                val hit = layout.words.firstOrNull { it.box.contains(at) }
                                if (hit != null) onAyah(hit.ayah) else onBackground()
                            }
                        },
                ) {
                    if (selectedAyah != null) {
                        // The chosen ayah, one soft band per line it runs across.
                        layout.words.filter { it.ayah == selectedAyah }.groupBy { it.top }.values.forEach { line ->
                            val left = line.minOf { it.box.left }
                            val right = line.maxOf { it.box.right }
                            val box = line.first().box
                            drawRoundRect(colors.highlight, Offset(left - 4f, box.top), Size(right - left + 8f, box.height), CornerRadius(10f))
                        }
                    }
                    layout.ornaments.forEach { drawText(it.layout, color = it.color, topLeft = Offset(it.x, it.top)) }
                    layout.words.forEach { w ->
                        translate(w.x, w.top) {
                            scale(w.scaleX, 1f, pivot = Offset.Zero) { drawText(w.layout, color = colors.ink) }
                        }
                    }
                }
            }
            Text(Quran.arabicDigits(number), style = running(colors.accent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun running(color: Color) = TextStyle(fontFamily = UthmanicHafs, fontSize = 14.sp, color = color, textDirection = TextDirection.Rtl)

/** The type size, in px: a typical line (the middle one of a dense page) just fills the width. */
private fun lineSize(measurer: TextMeasurer, mushaf: Mushaf, width: Float): Float {
    val probe = 100f
    val style = TextStyle(fontFamily = UthmanicHafs, fontSize = probe.sp, textDirection = TextDirection.Rtl)
    val widths = mushaf.pages[2].lines.filterIsInstance<Mushaf.Line.Text>()
        .map { line -> measurer.measure(tokens(mushaf, line).joinToString(" ") { it.second }, style, softWrap = false).size.width.toFloat() }
        .sorted()
    return probe * width / widths[widths.size / 2]
}

/** A line's words (and ayah numbers), each with its ayah. The digits alone draw the ornamented number. */
private fun tokens(mushaf: Mushaf, line: Mushaf.Line.Text): List<Pair<Int, String>> = line.runs.flatMap { run ->
    val words = if (run.hasWords) (run.from..run.to).map { run.ayah to mushaf.ayahs[run.ayah][it] } else emptyList()
    if (run.end) words + (run.ayah to Quran.arabicDigits(Quran.ref(run.ayah).second)) else words
}

private fun layOut(
    measurer: TextMeasurer,
    mushaf: Mushaf,
    number: Int,
    width: Float,
    height: Float,
    sizeSp: Float,
    colors: PageColors,
): PageLayout {
    // sizeSp came from a probe in sp; the measurer works in the same units, so keep it in sp.
    val opening = number <= 2
    val size = if (opening) sizeSp * 1.12f else sizeSp
    val textStyle = TextStyle(fontFamily = UthmanicHafs, fontSize = size.sp, textDirection = TextDirection.Rtl)
    val headingStyle = { s: Float -> TextStyle(fontFamily = QcfHeadings, fontSize = s.sp, textDirection = TextDirection.Rtl) }
    val page = mushaf.pages[number - 1]
    val slot = height / 15
    val lineCount = page.lines.size
    // The opening pages sit in the middle, in a smaller block; the others fill all fifteen lines.
    var y = if (opening) (height - slot * 1.3f * lineCount) / 2 else 0f
    val step = if (opening) slot * 1.3f else slot
    val space = measurer.measure(" ", textStyle).size.width.toFloat()
    val words = mutableListOf<Placed>()
    val ornaments = mutableListOf<Ornament>()

    page.lines.forEach { line ->
        val baseline = y + step * BASELINE
        when (line) {
            is Mushaf.Line.Text -> {
                val toks = tokens(mushaf, line)
                val measured = toks.map { measurer.measure(it.second, textStyle, softWrap = false) }
                val sum = measured.sumOf { it.size.width }.toFloat()
                val natural = sum + space * (toks.size - 1)
                val k = width / natural
                val scaleX: Float
                val gap: Float
                var x: Float
                if (opening) {
                    scaleX = 1f
                    gap = space
                    x = (width + natural) / 2
                } else {
                    scaleX = k.coerceIn(SQUEEZE_MAX, STRETCH_MAX)
                    gap = if (toks.size > 1) (width - sum * scaleX) / (toks.size - 1) else 0f
                    x = if (toks.size > 1) width else (width + sum * scaleX) / 2
                }
                // Right to left: the first word ends at the right edge.
                toks.zip(measured).forEach { (tok, m) ->
                    val w = m.size.width * scaleX
                    x -= w
                    val top = baseline - m.firstBaseline
                    words += Placed(m, x, top, scaleX, tok.first, Rect(x, y, x + w, y + step))
                    x -= gap
                }
            }
            is Mushaf.Line.Header -> {
                // The Complex's framed band across the line, with "سورة" and the name inside it.
                val frameChar = Quran.HEADING_FRAME.toChar().toString()
                val probe = measurer.measure(frameChar, headingStyle(100f), softWrap = false)
                val frame = measurer.measure(frameChar, headingStyle(100f * width / probe.size.width), softWrap = false)
                ornaments += Ornament(frame, 0f, y + (step - frame.size.height) / 2, colors.frame)
                val nameSize = size * 1.05f
                val surahWord = measurer.measure(Quran.HEADING_SURAH.toChar().toString(), headingStyle(nameSize), softWrap = false)
                val name = measurer.measure(Quran.headingCodes[line.surah - 1].toChar().toString(), headingStyle(nameSize), softWrap = false)
                val total = surahWord.size.width + space + name.size.width
                val left = (width - total) / 2
                ornaments += Ornament(name, left, baseline - name.firstBaseline, colors.ink)
                ornaments += Ornament(surahWord, left + name.size.width + space, baseline - surahWord.firstBaseline, colors.ink)
            }
            Mushaf.Line.Basmala -> {
                val basmala = Quran.HEADING_BASMALA.toChar().toString()
                val probe = measurer.measure(basmala, headingStyle(100f), softWrap = false)
                val b = measurer.measure(basmala, headingStyle(100f * width * 0.56f / probe.size.width), softWrap = false)
                ornaments += Ornament(b, (width - b.size.width) / 2, baseline - b.firstBaseline, colors.ink)
            }
        }
        y += step
    }
    return PageLayout(words, ornaments)
}
