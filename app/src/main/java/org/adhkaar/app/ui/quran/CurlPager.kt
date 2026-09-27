@file:OptIn(ExperimentalPageCurlApi::class)

package org.adhkaar.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.PageCurlState
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran

/**
 * The mushaf's pages, turned like paper. The page bends from wherever it is taken hold of: from
 * the bottom corner the bottom lifts first, from the top the top does. Its edge stays under the
 * finger, the page beneath shows in its shadow, and the back of the turning page shows the words
 * faintly through. A mushaf opens right to left, so the whole curl is mirrored: the page lifts
 * from its left edge and turns over to the right, and each page is mirrored back to read.
 */
@Composable
fun CurlPager(
    state: PageCurlState,
    mushaf: Mushaf,
    layouts: PageLayouts,
    colors: PageColors,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
    onBackground: () -> Unit,
    pageModifier: Modifier = Modifier,
) {
    val config = rememberPageCurlConfig(
        backPageColor = colors.paper,
        backPageContentAlpha = 0.12f,
        shadowColor = Color.Black,
        shadowAlpha = if (colors == PageColors.night) 0.5f else 0.22f,
        shadowRadius = 18.dp,
        shadowOffset = DpOffset((-4).dp, 0.dp),
        // Taps belong to the ayahs and the bars; pages turn by drag only.
        tapForwardEnabled = false,
        tapBackwardEnabled = false,
        tapCustomEnabled = false,
        // A drag's direction decides: the page's edge follows the finger from wherever it starts.
        dragInteraction = PageCurlConfig.GestureDragInteraction(PageCurlConfig.DragInteraction.PointerBehavior.PageEdge),
    )
    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { scaleX = -1f }) {
        PageCurl(count = Quran.PAGES, state = state, config = config) { index ->
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }.background(colors.paper)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    MushafPage(mushaf, layouts, index + 1, colors, selectedAyah, onAyah, onBackground, modifier = pageModifier)
                }
            }
        }
    }
}
