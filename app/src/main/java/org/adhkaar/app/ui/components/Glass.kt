package org.adhkaar.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.ui.theme.Glass
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/** Glass fill, light-catching edge, and the 1dp top highlight (DESIGN.md → Glass). */
fun Modifier.glass(shape: Shape, level: Int = 1, pressed: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        when {
            pressed -> Glass.fillPressed
            level >= 2 -> Glass.fill2
            else -> Glass.fill1
        },
        shape,
    )
    .border(1.dp, if (level >= 2) Glass.edge2 else Glass.edge1, shape)
    .drawWithContent {
        drawContent()
        val y = 1.dp.toPx()
        drawLine(
            Brush.horizontalGradient(
                0f to Color.Transparent, 0.5f to Glass.highlight, 1f to Color.Transparent,
                startX = size.width * 0.08f, endX = size.width * 0.92f,
            ),
            Offset(size.width * 0.08f, y), Offset(size.width * 0.92f, y), strokeWidth = 1.dp.toPx(),
        )
    }

/** A soft coloured glow beneath an element (used under the primary button). */
fun Modifier.glowBelow(color: Color, radius: Dp, blur: Dp = 22.dp, offsetY: Dp = 10.dp): Modifier = drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val inset = 12.dp.toPx()
        canvas.nativeCanvas.drawRoundRect(
            inset, offsetY.toPx(), size.width - inset, size.height + offsetY.toPx() * 0.4f,
            radius.toPx(), radius.toPx(), paint,
        )
    }
}

/** Whether taps give haptic feedback; follows the Haptics setting (provided by AdhkaarTheme). */
val LocalHaptics = staticCompositionLocalOf { true }

/**
 * Press feedback, never a Material ripple: the element shrinks on a spring, a soft light blooms
 * under the finger and follows it, and one crisp click confirms the tap (a single haptic per
 * touch; a separate landing tick felt doubled). On the click, rings of light spread from where
 * the finger landed, like a drop falling into water.
 *
 * [shape] clips the light to the element (pass it when the element isn't already clipped).
 * [haptic]: null = light tap, true = firm (main actions), false = none (the caller handles it).
 * [ripple]: false for elements whose own animation already answers the tap.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.97f,
    haptic: Boolean? = null,
    interaction: MutableInteractionSource? = null,
    shape: Shape? = null,
    ripple: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interaction ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, Motion.press(), label = "press")
    // The light: quick to appear, slow to fade, and it opens outward from the finger.
    val glow by animateFloatAsState(if (pressed && enabled) 1f else 0f, if (pressed) tween(120) else tween(480), label = "pressGlow")
    val spread by animateFloatAsState(if (pressed && enabled) 1f else 0.4f, if (pressed) tween(520, easing = Motion.emphasized) else tween(480), label = "pressSpread")
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    // Where the finger first landed; the ripple starts here even if the finger drifted before lifting.
    var landedAt by remember { mutableStateOf(Offset.Unspecified) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    val scope = rememberCoroutineScope()
    // Mostly white, with just enough of the aura that the water seems to catch the sky's colour.
    val rippleTint = lerp(Color.White, LocalAura.current.accent, 0.3f)
    val view = LocalView.current
    // Keyed on the press state rather than the raw touch, so a scroll that starts on the element
    // (which never becomes a press) stays silent.
    graphicsLayer { scaleX = s; scaleY = s }
        // Watches the finger without taking the touch from the click below.
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch = down.position
                landedAt = down.position
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    touch = change.position
                }
            }
        }
        .drawWithCache {
            // Built once per size, so a frame only reads the animations and draws.
            val clip = shape?.let { Path().apply { addOutline(it.createOutline(size, layoutDirection, this@drawWithCache)) } }
            val strokes = RippleStrokes(
                wide = Stroke(14.dp.toPx()),
                mid = Stroke(6.dp.toPx()),
                edge = Stroke(1.dp.toPx()),
                arc = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                trail = 5.dp.toPx(),
            )
            onDrawWithContent {
                drawContent()
                // Everything below is read here, in the draw phase, so animating never recomposes.
                if (glow <= 0f && ripples.isEmpty()) return@onDrawWithContent
                val lights: DrawScope.() -> Unit = {
                    if (glow > 0f) {
                        val at = if (touch.isSpecified) touch else center
                        val radius = size.maxDimension * (0.35f + 0.5f * spread)
                        val light = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.16f * glow), 0.55f to Color.White.copy(alpha = 0.06f * glow), 1f to Color.Transparent,
                            center = at, radius = radius,
                        )
                        drawCircle(light, radius, at)
                    }
                    ripples.forEach { drawRipple(it, rippleTint, strokes) }
                }
                if (clip != null) clipPath(clip, block = lights) else clipRect(block = lights)
            }
        }
        .clickable(source, indication = null, enabled = enabled) {
            when (haptic) {
                true -> Haptics.confirm(view)
                null -> Haptics.tap(view)
                false -> Unit
            }
            if (ripple) {
                // A click with no finger yet (keyboard, accessibility services) rises from the middle.
                val drop = Ripple(landedAt)
                // Quick taps each get their own ripple; beyond a few, the oldest gives way.
                if (ripples.size >= MAX_RIPPLES) ripples.removeAt(0).job?.cancel()
                ripples += drop
                drop.job = scope.launch {
                    drop.progress.animateTo(1f, tween(RIPPLE_MILLIS, easing = LinearEasing))
                    ripples -= drop
                }
            }
            onClick()
        }
}

private const val MAX_RIPPLES = 3
private const val RIPPLE_MILLIS = 720

// The second ring follows the first after this share of the ripple's run, and is fainter.
private const val SECOND_RING_DELAY = 0.16f
private const val SECOND_RING_STRENGTH = 0.5f

/** One tap's ripple: where it began, and how far through its run it is (linear in time). */
private class Ripple(val at: Offset) {
    val progress = Animatable(0f)
    var job: Job? = null
}

private class RippleStrokes(val wide: Stroke, val mid: Stroke, val edge: Stroke, val arc: Stroke, val trail: Float)

private fun DrawScope.drawRipple(ripple: Ripple, tint: Color, strokes: RippleStrokes) {
    val p = ripple.progress.value
    val at = if (ripple.at.isSpecified) ripple.at else center
    // Far enough to pass the farthest corner, so the ring leaves the element rather than stopping in it.
    val reach = hypot(max(at.x, size.width - at.x), max(at.y, size.height - at.y)) * 1.05f
    drawRing(at, reach, p, 1f, tint, strokes)
    val second = (p - SECOND_RING_DELAY) / (1f - SECOND_RING_DELAY)
    if (second > 0f) drawRing(at, reach, second, SECOND_RING_STRENGTH, tint, strokes)
}

/**
 * A ring at [t] (0..1) of its run: a wide soft glow trailing a thin bright edge, a faint dark line
 * just inside it where the light bends, and a brighter arc on top, where light from above catches
 * the crest.
 */
private fun DrawScope.drawRing(at: Offset, reach: Float, t: Float, strength: Float, tint: Color, strokes: RippleStrokes) {
    // Spreads fast then slows, as a ring on water does; fades a little behind that, so it thins out
    // as it reaches the edges. A short fade-in keeps the first frame from popping.
    val radius = reach * (0.06f + 0.94f * Motion.emphasized.transform(t))
    val fade = (1f - t) * (1f - t) * (t / 0.06f).coerceAtMost(1f) * strength
    if (fade <= 0.004f) return
    // The wide glow sits wholly inside the edge, so the ring's front stays crisp.
    drawCircle(tint, (radius - strokes.trail * 1.4f).coerceAtLeast(0f), at, alpha = 0.06f * fade, style = strokes.wide)
    drawCircle(tint, (radius - strokes.trail * 0.4f).coerceAtLeast(0f), at, alpha = 0.08f * fade, style = strokes.mid)
    drawCircle(Color.Black, (radius - strokes.trail * 0.5f).coerceAtLeast(0f), at, alpha = 0.08f * fade, style = strokes.edge)
    drawCircle(Color.White, radius, at, alpha = 0.3f * fade, style = strokes.edge)
    drawArc(
        Color.White, startAngle = -145f, sweepAngle = 110f, useCenter = false,
        topLeft = Offset(at.x - radius, at.y - radius), size = Size(radius * 2f, radius * 2f),
        alpha = 0.42f * fade, style = strokes.arc,
    )
}

/**
 * Cards on a page settle in one after another when the page opens. A page provides an
 * [Entrances]; every glass surface composed in its first moments takes the next place in line.
 */
class Entrances {
    private val opened = System.nanoTime()
    private var next = 0

    /** This card's place in line, or null when the page has been open a while (e.g. scrolled to). */
    fun claim(): Int? = if (System.nanoTime() - opened > 400_000_000L) null else next++.coerceAtMost(8)
}

val LocalEntrances = staticCompositionLocalOf<Entrances?> { null }

/** Fades and lifts a card into place, [order] steps after the page opens. */
fun Modifier.entrance(order: Int?): Modifier = if (order == null) this else composed {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(order * 45L)
        shown.animateTo(1f, tween(520, easing = Motion.emphasized))
    }
    graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 16.dp.toPx()
        val s = 0.985f + 0.015f * shown.value
        scaleX = s
        scaleY = s
    }
}

/** [haptic] is passed to [pressable] when the surface is clickable. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),
    level: Int = 1,
    onClick: (() -> Unit)? = null,
    haptic: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val order = LocalEntrances.current?.let { remember { it.claim() } }
    Box(
        modifier
            .entrance(order)
            .then(if (onClick != null) Modifier.pressable(haptic = haptic, interaction = interaction, shape = shape, onClick = onClick) else Modifier)
            .glass(shape, level, pressed = onClick != null && pressed),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.card,
    padding: PaddingValues = PaddingValues(Space.gutter),
    level: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier, RoundedCornerShape(radius), level, onClick) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** 44dp glass circle inside a 48dp touch target. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier, tint: Color = Nur.textPrimary, onClick: () -> Unit) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.size(44.dp), CircleShape, level = 2, onClick = onClick) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
    }
}

/** The one solid, coloured element on a screen. 56dp, gradient, trailing icon circle, soft glow. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.AutoMirrored.Rounded.ArrowForward,
    colors: List<Color> = LocalAura.current.action,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    // Disabled: the colour drains and the glow goes out, so it reads as "not yet".
    val strength by animateFloatAsState(if (enabled) 1f else 0f, tween(320), label = "primaryEnabled")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { alpha = 0.4f + 0.6f * strength }
            .glowBelow(colors.first().copy(alpha = 0.45f * strength), 28.dp)
            .pressable(enabled, haptic = true, interaction = interaction, shape = RoundedCornerShape(50), onClick = onClick)
            .clip(shape)
            .background(Brush.horizontalGradient(colors.map { lerp(Color(0xFF2A3148), it, strength) }))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label.copy(fontSize = 16.sp), color = Color.White)
        if (icon != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(50), level = 2, onClick = onClick) {
        Text(text, style = Type.label.copy(fontSize = 16.sp), modifier = Modifier.align(Alignment.Center))
    }
}

/** Small status chip. */
@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, color: Color = Nur.textSecondary, icon: ImageVector? = null, dot: Color? = null) {
    Row(
        modifier
            .height(28.dp)
            .glass(RoundedCornerShape(50), level = 2)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

/** A glass track with a sliding glass thumb. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .glass(RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, Motion.move(), label = "thumb")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .glass(RoundedCornerShape(50), level = 2)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(if (i == selected) Nur.textPrimary else Nur.textTertiary, tween(200), label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) Haptics.tick(view)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Type.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val aura = LocalAura.current
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, Motion.move(), label = "switch")
    val trackAlpha by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "track")
    val view = LocalView.current
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .background(Brush.horizontalGradient(aura.action), alpha = trackAlpha)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                Haptics.tick(view)
                onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

data class Stat(val value: String, val label: String)

/** Values separated by hairlines, like the Waze trip summary. */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        stats.forEachIndexed { i, stat ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stat.value, style = Type.stat, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(stat.label, style = Type.caption, maxLines = 1)
            }
            if (i != stats.lastIndex) {
                Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.1f)))
            }
        }
    }
}

/** One segment per dhikr. */
@Composable
fun SegmentedProgress(done: List<Boolean>, current: Int, modifier: Modifier = Modifier) {
    val accent = LocalAura.current.accent
    Row(modifier.height(3.dp)) {
        done.forEachIndexed { i, isDone ->
            val fill by animateFloatAsState(if (isDone) 1f else if (i == current) 0.4f else 0f, Motion.enter(), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(RoundedCornerShape(50)).background(accent))
            }
            if (i != done.lastIndex) Spacer(Modifier.width(3.dp))
        }
    }
}

/** A round glass badge holding an icon, used at the start of rows. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = LocalAura.current.accent, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .glass(RoundedCornerShape(size * 0.36f), level = 2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun RowScope.Hairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))
}
