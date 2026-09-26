package org.adhkaar.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import org.adhkaar.app.ui.theme.Glass
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Type
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Tab(val label: String, val icon: ImageVector)

/**
 * Floating glass capsule, as wide as the page's content, so it never shifts as tabs change. The
 * active tab's slot widens to show its label, and it sits on a drop of liquid glass that travels
 * between tabs: the leading edge moves quickly and the trailing edge follows on a softer spring,
 * so the drop stretches while it moves, thins a little at full stretch, and settles with a small
 * bounce. The springs are integrated every frame, so the drop follows a slot that is still widening.
 */
@Composable
fun GlassTabBar(tabs: List<Tab>, selected: Int, hazeState: HazeState, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    val view = LocalView.current
    val accent = LocalAura.current.accent
    // Each tab's bounds inside the row; the drop reads them every frame, since the active tab widens.
    val currentSelected = remember { mutableIntStateOf(selected) }.apply { intValue = selected }
    val bounds = remember { mutableStateListOf<Pair<Float, Float>>().apply { repeat(tabs.size) { add(0f to 0f) } } }
    val drop = remember { DropEdges() }
    // Runs frame by frame only while the drop is moving, always chasing the active slot's latest
    // edges (the slot is still widening as the drop travels); at rest it waits for the next change.
    // When a drop sent by the finger comes to rest, a faint landing is felt, once per move.
    LaunchedEffect(Unit) {
        snapshotFlow { bounds.getOrNull(currentSelected.intValue) }.collect { first ->
            var target = first ?: return@collect
            var last = 0L
            while (target.second > target.first && !drop.settledAt(target)) {
                withFrameNanos { now ->
                    target = bounds.getOrNull(currentSelected.intValue) ?: target
                    drop.step(target, if (last == 0L) 1f / 60f else (now - last) / 1e9f)
                    last = now
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = Nur.ink,
                    tint = HazeTint(Color(0xFF0B1024).copy(alpha = 0.55f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.06f,
                ),
            )
            .glass(shape)
            .padding(6.dp),
    ) {
        // The drop, drawn behind the tabs.
        Canvas(Modifier.matchParentSize()) {
            if (!drop.placed) return@Canvas
            // Read here, not in composition: the bounds change every frame while the slots resize,
            // and only the drop needs them.
            val target = bounds.getOrNull(selected)
            val l = min(drop.left, drop.right)
            val r = max(drop.left, drop.right)
            val rest = target?.let { it.second - it.first } ?: (r - l)
            // How far past its resting width the drop is stretched, 0…1.
            val stretch = ((r - l - rest) / size.height).coerceIn(0f, 1f)
            val thin = size.height * 0.1f * stretch
            val top = thin / 2
            val h = size.height - thin
            val radius = CornerRadius(h / 2, h / 2)
            // A soft glow of the accent beneath, strongest while moving.
            drawRoundRect(
                Brush.radialGradient(
                    0f to accent.copy(alpha = 0.16f + 0.12f * stretch), 1f to Color.Transparent,
                    center = Offset((l + r) / 2, size.height), radius = max(r - l, size.height),
                ),
                topLeft = Offset(l, top), size = Size(r - l, h), cornerRadius = radius,
            )
            // Body: a little more fill than the resting glass.
            drawRoundRect(Glass.fill2.copy(alpha = 0.12f), Offset(l, top), Size(r - l, h), radius)
            // Rim: bright along the top, fading down, like light caught in the edge of a drop.
            drawRoundRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.34f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.16f),
                    startY = top, endY = top + h,
                ),
                Offset(l, top), Size(r - l, h), radius, style = Stroke(1.dp.toPx()),
            )
            // Specular streak along the top, which slides slightly behind the motion.
            val streakWidth = (r - l) * 0.46f
            val lag = (drop.left - (target?.first ?: drop.left)).coerceIn(-streakWidth, streakWidth) * 0.3f
            val sx = l + (r - l - streakWidth) / 2 + lag
            drawRoundRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.22f), 1f to Color.Transparent,
                    startX = sx, endX = sx + streakWidth,
                ),
                Offset(sx, top + h * 0.1f), Size(streakWidth, h * 0.16f), CornerRadius(h, h),
            )
        }

        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, tab ->
                TabSlot(
                    tab = tab,
                    active = i == selected,
                    accent = accent,
                    dropPlaced = { drop.placed },
                    onBounds = { left, right -> bounds[i] = left to right },
                    onClick = {
                        if (i != selected) {
                            Haptics.tick(view)
                        }
                        onSelect(i)
                    },
                )
            }
        }
    }
}

/**
 * One tab. Its own composable, so its animations (width, tint, label) recompose only this slot
 * frame by frame, not the whole bar.
 */
@Composable
private fun RowScope.TabSlot(
    tab: Tab,
    active: Boolean,
    accent: Color,
    dropPlaced: () -> Boolean,
    onBounds: (left: Float, right: Float) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (active) accent else Nur.textTertiary, tween(360), label = "tabTint")
    // The icon gives a small spring when its tab is chosen, and dips while pressed.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active && dropPlaced()) {
            pop.snapTo(0.82f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.press(), label = "tabPress")
    // The active slot is wider, to hold its label.
    val weight by animateFloatAsState(if (active) 1.9f else 1f, spring(dampingRatio = 0.85f, stiffness = 180f), label = "tabWeight")
    Row(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .onGloballyPositioned { onBounds(it.boundsInParent().left, it.boundsInParent().right) }
            .clip(shape)
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tab.icon, tab.label, tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = pop.value * pressScale
                    scaleX = s
                    scaleY = s
                },
        )
        // The label opens from the icon's side: the outgoing one fades almost at once, the
        // incoming one fades in just behind the drop, so no word is seen half-cut.
        AnimatedVisibility(
            visible = active,
            enter = expandHorizontally(tween(560, easing = Motion.emphasized), expandFrom = Alignment.Start) + fadeIn(tween(320, delayMillis = 200)),
            exit = shrinkHorizontally(tween(560, easing = Motion.emphasized), shrinkTowards = Alignment.Start) + fadeOut(tween(140)),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(tab.label, style = Type.label, color = Nur.textPrimary, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The drop's two edges, each a damped spring pulled towards the active slot's edge. Whichever edge
 * leads the motion gets the stiffer spring; the other trails, which stretches the drop.
 */
private class DropEdges {
    var left by mutableFloatStateOf(0f)
    var right by mutableFloatStateOf(0f)
    var placed by mutableStateOf(false)
    private var leftVelocity = 0f
    private var rightVelocity = 0f

    fun step(target: Pair<Float, Float>, dt: Float) {
        if (!placed) {
            left = target.first
            right = target.second
            placed = true
            return
        }
        val movingRight = (target.first + target.second) / 2 > (left + right) / 2
        // Small fixed steps keep the springs stable whatever the frame rate.
        var remaining = dt.coerceAtMost(0.05f)
        while (remaining > 0f) {
            val h = min(remaining, 1f / 240f)
            leftVelocity += spring(left, leftVelocity, target.first, lead = !movingRight) * h
            rightVelocity += spring(right, rightVelocity, target.second, lead = movingRight) * h
            left += leftVelocity * h
            right += rightVelocity * h
            remaining -= h
        }
    }

    fun settledAt(target: Pair<Float, Float>): Boolean =
        placed && abs(left - target.first) < 0.5f && abs(right - target.second) < 0.5f &&
            abs(leftVelocity) < 5f && abs(rightVelocity) < 5f

    // Lead: stiffness 260, damping ratio 0.75. Trail: stiffness 110, damping ratio 0.66.
    // Unhurried on purpose: the stretch should be seen, and the drop settles in about 0.6 s.
    private fun spring(x: Float, v: Float, target: Float, lead: Boolean): Float {
        val k = if (lead) 260f else 110f
        val c = 2f * (if (lead) 0.75f else 0.66f) * kotlin.math.sqrt(k)
        return -k * (x - target) - c * v
    }
}
