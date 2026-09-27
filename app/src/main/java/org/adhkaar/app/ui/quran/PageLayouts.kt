package org.adhkaar.app.ui.quran

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.ui.theme.QcfHeadings
import org.adhkaar.app.ui.theme.UthmanicHafs

/**
 * How far a line may be stretched or narrowed to meet both edges. The printed mushaf fills its
 * lines by drawing letters longer or shorter; a line here is scaled across within these bounds,
 * which the eye doesn't notice, and any remainder goes into the spaces between words.
 */
private const val STRETCH_MAX = 1.08f
private const val SQUEEZE_MAX = 0.84f

/** Where the baseline sits in a line's slot. */
private const val BASELINE = 0.64f

/** What an ornament is drawn in: the frame's colour or the ink. */
enum class Ink { FRAME, TEXT }

/** A word (or an ayah's number) placed on the page. */
class Placed(val layout: TextLayoutResult, val x: Float, val top: Float, val scaleX: Float, val ayah: Int, val box: Rect)

/** A heading glyph placed on the page. */
class Ornament(val layout: TextLayoutResult, val x: Float, val top: Float, val ink: Ink)

class PageLayout(val words: List<Placed>, val ornaments: List<Ornament>)

/**
 * Lays pages out off the main thread and keeps the last few, so a page is ready before it turns
 * into view and turning never waits on measuring. Positions only: colours are applied when drawn.
 */
@androidx.compose.runtime.Stable
class PageLayouts(context: Context, private val density: Density) {
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Rtl, cacheSize = 0)
    private val cache = object : LinkedHashMap<String, PageLayout>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageLayout>) = size > 10
    }
    private var sizeFor: Pair<Float, Float>? = null

    fun cached(page: Int, width: Float, height: Float): PageLayout? = synchronized(cache) { cache[key(page, width, height)] }

    suspend fun get(mushaf: Mushaf, page: Int, width: Float, height: Float): PageLayout = withContext(Dispatchers.Default) {
        cached(page, width, height) ?: layOut(mushaf, page, width, height).also { synchronized(cache) { cache[key(page, width, height)] = it } }
    }

    private fun key(page: Int, width: Float, height: Float) = "$page:${width.toInt()}:${height.toInt()}"

    private fun textStyle(family: FontFamily, sizeSp: Float, direction: TextDirection = TextDirection.Rtl) =
        TextStyle(fontFamily = family, fontSize = sizeSp.sp, textDirection = direction)

    /** The type size, in sp: a typical line (the middle one of a dense page) just fills the width. */
    private fun lineSize(mushaf: Mushaf, width: Float): Float {
        sizeFor?.let { (w, s) -> if (w == width) return s }
        val probe = 100f
        val widths = mushaf.pages[2].lines.filterIsInstance<Mushaf.Line.Text>()
            .map { line -> measurer.measure(tokens(mushaf, line).joinToString(" ") { it.second }, textStyle(UthmanicHafs, probe), softWrap = false).size.width.toFloat() }
            .sorted()
        return (probe * width / widths[widths.size / 2]).also { sizeFor = width to it }
    }

    /**
     * The heading font keeps its glyphs on Latin codes, some of them brackets ("]" is al-Fatihah's
     * name). Set right to left they would be mirrored into other glyphs, so they are set left to right.
     */
    private fun heading(code: Int, sizeSp: Float) =
        measurer.measure(code.toChar().toString(), textStyle(QcfHeadings, sizeSp, TextDirection.Ltr), softWrap = false)

    private fun layOut(mushaf: Mushaf, number: Int, width: Float, height: Float): PageLayout {
        val opening = number <= 2
        val base = lineSize(mushaf, width)
        val size = if (opening) base * 1.12f else base
        val style = textStyle(UthmanicHafs, size)
        val page = mushaf.pages[number - 1]
        val slot = height / 15
        // The opening pages sit in the middle, in a smaller block; the others fill all fifteen lines.
        val step = if (opening) slot * 1.3f else slot
        var y = if (opening) (height - step * page.lines.size) / 2 else 0f
        val space = measurer.measure(" ", style).size.width.toFloat()
        val words = mutableListOf<Placed>()
        val ornaments = mutableListOf<Ornament>()

        page.lines.forEach { line ->
            val baseline = y + step * BASELINE
            when (line) {
                is Mushaf.Line.Text -> {
                    val toks = tokens(mushaf, line)
                    val measured = toks.map { measurer.measure(it.second, style, softWrap = false) }
                    val sum = measured.sumOf { it.size.width }.toFloat()
                    val natural = sum + space * (toks.size - 1)
                    val scaleX: Float
                    val gap: Float
                    var x: Float
                    if (opening) {
                        scaleX = 1f
                        gap = space
                        x = (width + natural) / 2
                    } else {
                        scaleX = (width / natural).coerceIn(SQUEEZE_MAX, STRETCH_MAX)
                        gap = if (toks.size > 1) (width - sum * scaleX) / (toks.size - 1) else 0f
                        x = if (toks.size > 1) width else (width + sum * scaleX) / 2
                    }
                    // Right to left: the first word ends at the right edge.
                    toks.zip(measured).forEach { (tok, m) ->
                        val w = m.size.width * scaleX
                        x -= w
                        words += Placed(m, x, baseline - m.firstBaseline, scaleX, tok.first, Rect(x - gap / 2, y, x + w + gap / 2, y + step))
                        x -= gap
                    }
                }
                is Mushaf.Line.Header -> {
                    // The Complex's framed band across the line, with "سورة" and the name centred in it.
                    val probe = heading(Quran.HEADING_FRAME, 100f)
                    val frame = heading(Quran.HEADING_FRAME, 100f * width / probe.size.width)
                    val frameTop = y + (step - frame.size.height) / 2
                    ornaments += Ornament(frame, 0f, frameTop, Ink.FRAME)
                    val nameSize = size * 1.1f
                    val word = heading(Quran.HEADING_SURAH, nameSize)
                    val name = heading(Quran.headingCodes[line.surah - 1], nameSize)
                    val gap = space * 0.6f
                    val total = word.size.width + gap + name.size.width
                    val left = (width - total) / 2
                    val middle = frameTop + frame.size.height / 2
                    // Read right to left: "سورة" on the right, the name to its left.
                    ornaments += Ornament(name, left, middle - name.size.height / 2, Ink.TEXT)
                    ornaments += Ornament(word, left + name.size.width + gap, middle - word.size.height / 2, Ink.TEXT)
                }
                Mushaf.Line.Basmala -> {
                    val probe = heading(Quran.HEADING_BASMALA, 100f)
                    val b = heading(Quran.HEADING_BASMALA, 100f * width * 0.62f / probe.size.width)
                    ornaments += Ornament(b, (width - b.size.width) / 2, y + (step - b.size.height) / 2, Ink.TEXT)
                }
            }
            y += step
        }
        return PageLayout(words, ornaments)
    }

    companion object {
        /** A line's words (and ayah numbers), each with its ayah. The digits alone draw the ornamented number. */
        fun tokens(mushaf: Mushaf, line: Mushaf.Line.Text): List<Pair<Int, String>> = line.runs.flatMap { run ->
            val words = if (run.hasWords) (run.from..run.to).map { run.ayah to mushaf.ayahs[run.ayah][it] } else emptyList()
            if (run.end) words + (run.ayah to Quran.arabicDigits(Quran.ref(run.ayah).second)) else words
        }
    }
}
