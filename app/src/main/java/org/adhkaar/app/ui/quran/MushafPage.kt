package org.adhkaar.app.ui.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.PageTheme
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.ui.theme.UthmanicHafs

/** The paper, the ink and the accents of a page. */
@Immutable
data class PageColors(
    val paper: Color,
    val ink: Color,
    /** The surah heading's frame and the page number's rule. */
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
 * A page of the Madinah mushaf with the printed line breaks: the juz and the surah above, fifteen
 * lines each meeting both edges, surah headings in the Complex's own frame, the page number below.
 * Laid out in the background by [layouts] and drawn as one surface. Tapping a word chooses its
 * ayah; tapping elsewhere calls [onBackground].
 */
@Composable
fun MushafPage(
    mushaf: Mushaf,
    layouts: PageLayouts,
    number: Int,
    colors: PageColors,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
    onBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier.fillMaxSize().background(colors.paper).padding(horizontal = 14.dp, vertical = 6.dp)) {
            // The running heading: the juz on the right, the surah on the left, as printed.
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الجزء " + Quran.arabicDigits(mushaf.juzOfPage(number)), style = running(colors.accent, 15.sp))
                Text("سورة " + Quran.arabicNames[mushaf.surahsOfPage(number).last() - 1], style = running(colors.accent, 15.sp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
                val width = with(density) { maxWidth.toPx() }
                val height = with(density) { maxHeight.toPx() }
                val layout by produceState(layouts.cached(number, width, height), number, width, height) {
                    value = layouts.get(mushaf, number, width, height)
                    // The pages either side, ready before they turn into view.
                    for (near in listOf(number + 1, number - 1, number + 2)) {
                        if (near in 1..Quran.PAGES) launch { layouts.get(mushaf, near, width, height) }
                    }
                }
                val ready = layout
                if (ready != null) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            // Its own layer: turning the page moves the drawing, never redraws it.
                            .graphicsLayer()
                            .pointerInput(number, ready) {
                                detectTapGestures { at ->
                                    val hit = ready.words.firstOrNull { it.box.contains(at) }
                                    if (hit != null) onAyah(hit.ayah) else onBackground()
                                }
                            },
                    ) {
                        if (selectedAyah != null) {
                            // The chosen ayah, one soft band per line it runs across.
                            ready.words.filter { it.ayah == selectedAyah }.groupBy { it.box.top }.values.forEach { line ->
                                val left = line.minOf { it.box.left }
                                val right = line.maxOf { it.box.right }
                                val box = line.first().box
                                drawRoundRect(colors.highlight, Offset(left, box.top + 2f), Size(right - left, box.height - 4f), CornerRadius(12f))
                            }
                        }
                        ready.ornaments.forEach {
                            drawText(it.layout, color = if (it.ink == Ink.FRAME) colors.frame else colors.ink, topLeft = Offset(it.x, it.top))
                        }
                        ready.words.forEach { w ->
                            translate(w.x, w.top) {
                                scale(w.scaleX, 1f, pivot = Offset.Zero) { drawText(w.layout, color = colors.ink) }
                            }
                        }
                    }
                }
            }
            PageNumber(number, colors)
        }
    }
}

/** The page number, centred between two short rules, as a mushaf sets it. */
@Composable
private fun PageNumber(number: Int, colors: PageColors) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp).height(1.dp).background(colors.frame.copy(alpha = 0.6f)))
        Spacer(Modifier.width(10.dp))
        Text(Quran.arabicDigits(number), style = running(colors.accent, 19.sp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(28.dp).height(1.dp).background(colors.frame.copy(alpha = 0.6f)))
    }
}

/** The page's own lettering: Amiri, whose digits stay plain (in the mushaf font digits become ayah markers). */
private fun running(color: Color, size: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = org.adhkaar.app.ui.theme.AmiriQuran, fontSize = size, color = color, textDirection = TextDirection.Rtl)
