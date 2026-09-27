package org.adhkaar.app.ui.quran

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran
import kotlin.math.abs
import kotlin.math.hypot

/** Which page the reader is on (0-based), and turns to others. */
@Stable
class CurlState(initial: Int) {
    var current by mutableIntStateOf(initial.coerceIn(0, Quran.PAGES - 1))
        internal set

    /** Opens [page] (0-based) at once. */
    fun snapTo(page: Int) {
        current = page.coerceIn(0, Quran.PAGES - 1)
    }
}

@Composable
fun rememberCurlState(initial: Int): CurlState = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver({ it.current }, { CurlState(it) })) { CurlState(initial) }

/** A turn under way: which page lies on top and folds, which lies beneath, and whether it goes forward. */
private data class Turn(val top: Int, val beneath: Int, val forward: Boolean, val corner: Offset)

/**
 * The mushaf's pages, turned like paper. A mushaf opens right to left: its spine is on the right,
 * so a page is taken by its left edge and folded over to the right, and a page already read comes
 * back from the right. The page folds along the line halfway between the corner taken and the
 * finger, so its corner stays under the finger: taken low, the bottom lifts first; taken high,
 * the top. The page beneath shows in the fold's shadow; the back of the fold is plain paper.
 * Let go past halfway and the turn completes; short of it, the page falls back.
 */
@Composable
fun CurlPager(
    state: CurlState,
    mushaf: Mushaf,
    layouts: PageLayouts,
    colors: PageColors,
    selectedAyah: Int?,
    onAyah: (Int) -> Unit,
    onBackground: () -> Unit,
    pageModifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    // Where the folding page's corner is, while a turn is under way.
    val tip = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var turn by remember { mutableStateOf<Turn?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.paper)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        fun finish(t: Turn, complete: Boolean) {
            scope.launch {
                // Forward: done when the corner reaches the far side of the spine; back: when it lies flat.
                val flat = t.corner
                val over = Offset(2 * w + 40f, t.corner.y)
                val goal = if (t.forward == complete) over else flat
                val distance = (goal - tip.value).getDistance()
                tip.animateTo(goal, tween((180 + distance / w * 260).toInt().coerceIn(180, 420), easing = FastOutSlowInEasing))
                if (complete) {
                    state.current = if (t.forward) t.beneath else t.top
                    // A light tick as the page lands.
                    org.adhkaar.app.ui.components.Haptics.tick(view)
                }
                turn = null
            }
        }

        val gestures = Modifier.pointerInput(w, h) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var direction = 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    direction = over
                    change.consume()
                } ?: return@awaitEachGesture
                // Finger to the right: forward (the next page). To the left: back.
                val forward = direction > 0
                val page = state.current
                val target = if (forward) page + 1 else page - 1
                if (target !in 0 until Quran.PAGES || turn != null) return@awaitEachGesture
                // The corner nearest where the page was taken: its left edge, top or bottom.
                val corner = Offset(0f, if (down.position.y < h / 2) 0f else h)
                val t = if (forward) Turn(page, target, true, corner) else Turn(target, page, false, corner)
                val start = if (forward) corner else Offset(2 * w, corner.y)
                val origin = drag.position
                scope.launch { tip.snapTo(start) }
                turn = t
                var velocity = 0f
                horizontalDrag(drag.id) { change ->
                    velocity = change.positionChange().x
                    val moved = change.position - origin
                    // The corner follows the finger across; up and down it follows at half the rate, within reach.
                    val y = (corner.y + moved.y * 0.5f).coerceIn(if (corner.y == 0f) 0f else h * 0.55f, if (corner.y == 0f) h * 0.45f else h)
                    val x = (start.x + moved.x * 2f).coerceIn(0f, 2 * w)
                    scope.launch { tip.snapTo(Offset(x, y)) }
                    change.consume()
                }
                // Past halfway, or flicked the right way, completes the turn.
                val progress = tip.value.x / (2 * w)
                val complete = if (forward) progress > 0.25f || velocity > 18f else progress < 0.75f || velocity < -18f
                finish(t, complete)
            }
        }

        Box(Modifier.fillMaxSize().then(gestures)) {
            val t = turn
            val shown = listOf(state.current - 1, state.current, state.current + 1).filter { it in 0 until Quran.PAGES }
            for (index in shown) {
                key(index) {
                    val role = when {
                        t == null -> if (index == state.current) Role.Flat else Role.Hidden
                        index == t.top -> Role.Folding
                        index == t.beneath -> Role.Beneath
                        else -> Role.Hidden
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (role == Role.Folding) 2f else if (role == Role.Beneath) 1f else 0f)
                            .graphicsLayer {
                                alpha = if (role == Role.Hidden) 0f else 1f
                                if (role == Role.Folding) {
                                    clip = true
                                    shape = UnfoldedShape(tip.value, t!!.corner)
                                }
                            }
                            .background(colors.paper),
                    ) {
                        MushafPage(mushaf, layouts, index + 1, colors, selectedAyah, onAyah, onBackground, modifier = pageModifier)
                    }
                }
            }
            if (t != null) {
                // The fold: its shadow on the page beneath, and the back of the page folded over.
                Canvas(Modifier.fillMaxSize().zIndex(3f)) {
                    drawFold(t.corner, tip.value, size.width, size.height, colors, density)
                }
                // The words showing faintly through the back of the page, mirrored, as through thin paper:
                // the folding page reflected across the fold, clipped to the flap.
                Box(Modifier.fillMaxSize().zIndex(4f).graphicsLayer { clip = true; shape = FlapShape(tip.value, t.corner) }) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val fold = Fold(t.corner, tip.value)
                                // Reflection across the fold: turn by twice its angle, then flip.
                                val angle = Math.toDegrees(kotlin.math.atan2(fold.n.x.toDouble(), -fold.n.y.toDouble())).toFloat()
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(fold.mid.x / w, fold.mid.y / h)
                                scaleY = -1f
                                rotationZ = 2 * angle
                                alpha = if (colors == PageColors.night) 0.10f else 0.13f
                            },
                    ) {
                        MushafPage(mushaf, layouts, t.top + 1, colors.copy(paper = Color.Transparent), null, {}, {}, modifier = pageModifier)
                    }
                }
                // The roll: light caught along the curve where the paper bends over.
                Canvas(Modifier.fillMaxSize().zIndex(5f)) { drawRoll(t.corner, tip.value, size.width, size.height, colors, density) }
            }
            // The edges of the pages either side, as a closed book shows them: the pages still to
            // read on the left, those read on the right, each stack as thick as its share.
            Canvas(Modifier.fillMaxSize().zIndex(6f)) { drawStacks(state.current, colors, density) }
        }
    }
}

private enum class Role { Flat, Folding, Beneath, Hidden }

/** The line the page folds along: halfway between the corner and the tip, square to the line joining them. */
private class Fold(corner: Offset, tip: Offset) {
    val mid = (corner + tip) / 2f
    private val d = tip - corner
    private val len = hypot(d.x, d.y).coerceAtLeast(0.001f)
    /** Unit normal, pointing from the corner towards the tip. */
    val n = Offset(d.x / len, d.y / len)

    /** Positive on the tip's side of the fold (the part of the page still lying flat), negative on the corner's. */
    fun side(p: Offset) = (p - mid).x * n.x + (p - mid).y * n.y

    fun reflect(p: Offset): Offset {
        val s = side(p)
        return p - n * (2 * s)
    }
}

/** The page rectangle cut by the fold: what lies on the chosen side (Sutherland–Hodgman against one line). */
private fun cut(fold: Fold, w: Float, h: Float, keepTipSide: Boolean): List<Offset> {
    val rect = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    val out = mutableListOf<Offset>()
    for (i in rect.indices) {
        val a = rect[i]
        val b = rect[(i + 1) % rect.size]
        val sa = fold.side(a) * (if (keepTipSide) 1 else -1)
        val sb = fold.side(b) * (if (keepTipSide) 1 else -1)
        if (sa >= 0) out += a
        if ((sa >= 0) != (sb >= 0)) out += a + (b - a) * (sa / (sa - sb))
    }
    return out
}

private fun path(points: List<Offset>) = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

/** The folding page, clipped to what still lies flat: the part beyond the fold from its taken corner. */
private class UnfoldedShape(private val tip: Offset, private val corner: Offset) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if ((tip - corner).getDistance() < 1f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        return Outline.Generic(path(cut(Fold(corner, tip), size.width, size.height, keepTipSide = true)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFold(corner: Offset, tip: Offset, w: Float, h: Float, colors: PageColors, density: Density) {
    if ((tip - corner).getDistance() < 1f) return
    val fold = Fold(corner, tip)
    val lifted = cut(fold, w, h, keepTipSide = false)
    if (lifted.size < 3) return
    val flap = lifted.map(fold::reflect)
    val night = colors == PageColors.night
    val shade = with(density) { 34.dp.toPx() }
    // How far through the turn: the shadows deepen as the page rises, and ease as it lands.
    val progress = (tip.x / (2 * w)).coerceIn(0f, 1f)
    val strength = 4 * progress * (1 - progress)

    // The shadow the lifted page casts on the page beneath, darkest at the fold.
    drawPath(
        path(lifted),
        Brush.linearGradient(
            0f to Color.Black.copy(alpha = (if (night) 0.55f else 0.32f) * (0.4f + strength)),
            1f to Color.Transparent,
            start = fold.mid, end = fold.mid - fold.n * shade * 1.6f,
        ),
    )
    // A soft shadow the flap throws onto the page still lying flat, just past the fold.
    drawPath(
        path(flap),
        Brush.linearGradient(
            0f to Color.Black.copy(alpha = if (night) 0.35f else 0.16f),
            1f to Color.Transparent,
            start = fold.mid, end = fold.mid + fold.n * shade,
        ),
    )
    // The back of the page: paper, a touch darker at the fold where it curves, lighter towards its edge.
    val back = if (night) Color(0xFF1B1E24) else lerpColor(colors.paper, Color(0xFFE9DFC8), 0.35f)
    drawPath(path(flap), back)
    drawPath(
        path(flap),
        Brush.linearGradient(
            0f to Color.Black.copy(alpha = if (night) 0.3f else 0.12f),
            0.35f to Color.Transparent,
            1f to Color.White.copy(alpha = if (night) 0.04f else 0.25f),
            start = fold.mid, end = tip,
        ),
    )
}

/** Where the flap lies: the lifted part of the page, reflected over the fold. */
private class FlapShape(private val tip: Offset, private val corner: Offset) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if ((tip - corner).getDistance() < 1f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        val fold = Fold(corner, tip)
        val lifted = cut(fold, size.width, size.height, keepTipSide = false)
        if (lifted.size < 3) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        return Outline.Generic(path(lifted.map(fold::reflect)))
    }
}

/**
 * The paper's roll along the fold: a band of shade where it turns under, a thin line of light on
 * the curve, then the flat of the back. It narrows as the page lies down or turns away.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoll(corner: Offset, tip: Offset, w: Float, h: Float, colors: PageColors, density: Density) {
    if ((tip - corner).getDistance() < 1f) return
    val fold = Fold(corner, tip)
    val lifted = cut(fold, w, h, keepTipSide = false)
    if (lifted.size < 3) return
    val flap = path(lifted.map(fold::reflect))
    val progress = (tip.x / (2 * w)).coerceIn(0f, 1f)
    val band = with(density) { (10 + 26 * 4 * progress * (1 - progress)).dp.toPx() }
    val night = colors == PageColors.night
    drawPath(
        flap,
        Brush.linearGradient(
            0f to Color.Black.copy(alpha = if (night) 0.35f else 0.18f),
            0.28f to Color.Transparent,
            0.5f to Color.White.copy(alpha = if (night) 0.08f else 0.45f),
            0.72f to Color.Transparent,
            1f to Color.Transparent,
            start = fold.mid, end = fold.mid + fold.n * band,
        ),
    )
}

/** The page edges down each side: a few fine lines, more on the side with more pages. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStacks(current: Int, colors: PageColors, density: Density) {
    val gap = with(density) { 1.4.dp.toPx() }
    val line = with(density) { 0.7.dp.toPx() }
    val tone = if (colors == PageColors.night) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val ahead = 1 + ((Quran.PAGES - 1 - current) * 5 / (Quran.PAGES - 1))
    val behind = 1 + (current * 5 / (Quran.PAGES - 1))
    repeat(ahead) { i -> drawRect(tone, Offset(i * gap, 0f), androidx.compose.ui.geometry.Size(line, size.height)) }
    repeat(behind) { i -> drawRect(tone, Offset(size.width - line - i * gap, 0f), androidx.compose.ui.geometry.Size(line, size.height)) }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
