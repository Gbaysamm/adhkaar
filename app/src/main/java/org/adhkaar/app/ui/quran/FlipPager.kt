package org.adhkaar.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import org.adhkaar.app.data.quran.Mushaf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The mushaf's pages, turned like a book's. The page stays where it is and turns about its spine
 * (the right edge, as a mushaf opens), catching the light as it lifts; the page beneath waits in
 * its shadow and comes out of it as the turn completes. Where the finger takes hold tilts the
 * turn: from the top the page leans one way, from the bottom the other.
 */
@Composable
fun FlipPager(
    pager: PagerState,
    mushaf: Mushaf,
    layouts: PageLayouts,
    colors: PageColors,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
    onBackground: () -> Unit,
    pageModifier: Modifier = Modifier,
) {
    // Where the finger went down: -1 at the top of the page, 1 at the bottom.
    var grab by remember { mutableFloatStateOf(0f) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HorizontalPager(
            pager,
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        grab = (down.position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                    }
                },
            beyondViewportPageCount = 1,
            key = { it },
            // A quick, firm finish when the finger lets go, like a page falling into place.
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(320, easing = FastOutSlowInEasing)),
        ) { index ->
            // How far this page is through its turn: 0 lying open, 1 turned away. Negative: underneath.
            fun turn() = (pager.currentPage - index) + pager.currentPageOffsetFraction
            val onTop by remember(index) { derivedStateOf { turn() >= 0f } }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (onTop) 1f else 0f)
                    .graphicsLayer {
                        val o = turn()
                        // Stay put: the pager would slide the page across; the turn replaces the slide.
                        translationX = -o * size.width
                        // Every page now sits in one place, so only the page turning and the one
                        // beneath it may show; the pages kept ready further off would cover them.
                        alpha = if (o <= -1f || o >= 1f) 0f else 1f
                        if (o > 0f) {
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            cameraDistance = 56f * density
                            // The free edge stays under the finger: seen from the front, a page turned
                            // by θ reaches cos θ of its width, so θ = acos(1 − o). Positive: it lifts
                            // towards the reader, as a turned page does.
                            rotationY = Math.toDegrees(kotlin.math.acos((1f - o).coerceIn(0f, 1f).toDouble())).toFloat()
                            rotationZ = grab * 2.5f * sin(o * PI).toFloat()
                        } else {
                            rotationY = 0f
                            rotationZ = 0f
                        }
                    },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    // The paper fills the screen, under the bars; the page's text is inset in it.
                    Box(Modifier.fillMaxSize().background(colors.paper)) {
                        MushafPage(mushaf, layouts, index + 1, colors, selectedAyah, onAyah, onBackground, modifier = pageModifier)
                    }
                }
                // Light and shadow, changed in the layer only, so the page itself is never redrawn.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val o = turn()
                            alpha = if (o > 0f) 0.55f * o else 0.45f * abs(o).coerceAtMost(1f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.8f)),
                            ),
                        ),
                )
            }
        }
    }
}
