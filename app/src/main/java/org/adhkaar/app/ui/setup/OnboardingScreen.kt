package org.adhkaar.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.sp
import org.adhkaar.app.ui.components.GlassPill
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.oem.OemAutostart
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import org.adhkaar.app.ui.components.rememberResumeTick
import org.adhkaar.app.setup.Requirement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.CompositionLocalProvider
import org.adhkaar.app.ui.theme.Aura
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import org.adhkaar.app.ui.components.OrbSymbol
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.settings.LocationRow
import org.adhkaar.app.ui.settings.RowDivider
import org.adhkaar.app.ui.settings.SettingsGroup
import org.adhkaar.app.ui.settings.SettingsRow
import org.adhkaar.app.ui.settings.StrictnessOptions
import org.adhkaar.app.ui.settings.updateSettings
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

private const val STEPS = 3

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally(Motion.enter()) { if (forward) it / 4 else -it / 4 } + fadeIn(Motion.enter())) togetherWith
                (slideOutHorizontally(Motion.enter()) { if (forward) -it / 6 else it / 6 } + fadeOut(Motion.exit()))
        },
        label = "onboarding",
    ) { current ->
        if (current == 0) {
            Welcome(onNext = { step = 1 })
        } else {
            // The last step can only finish once everything the chosen mode needs is allowed. It checks
            // again whenever the user comes back from a system settings screen.
            val settings by SettingsStore.get(context).flow.collectAsState()
            val resumeTick = rememberResumeTick()
            val ready = remember(resumeTick, settings.strictness, current) {
                current != STEPS || (
                    Requirement.missingRequired(context, settings.strictness).isEmpty() &&
                        OemAutostart.backgroundDone(context)
                    )
            }
            StepScaffold(
                step = current,
                buttonLabel = stringResource(if (current == STEPS) R.string.onboarding_finish else R.string.onboarding_continue),
                canContinue = ready,
                blockedHint = stringResource(R.string.onboarding_finish_blocked),
                onBack = { step = current - 1 },
                onNext = {
                    if (current < STEPS) {
                        step = current + 1
                    } else {
                        updateSettings(context) { it.copy(onboarded = true) }
                        onFinished()
                    }
                },
            ) {
                when (current) {
                    1 -> ModeStep()
                    2 -> TimesStep()
                    else -> {
                        SetupIntro()
                        Spacer(Modifier.height(Space.xl))
                        RequirementsList(settings.strictness)
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    // The day plays once, dawn to night, then rests on the crescent.
    val playing = remember { Animatable(0f) }
    LaunchedEffect(Unit) { playing.animateTo(0.6f, tween(4_500, easing = FastOutSlowInEasing)) }
    val phase = { welcomeScenePhase ?: playing.value }
    val day = dayAt(phase())
    // The page's colours follow the sky: warm by day, cool by night.
    val aura = Aura(
        base = Auras.evening.base,
        glows = Auras.dawn.glows.zip(Auras.evening.glows) { a, b -> lerp(a, b, day.night) },
        accent = lerp(Auras.dawn.accent, Auras.evening.accent, day.night),
        action = Auras.dawn.action.zip(Auras.evening.action) { a, b -> lerp(a, b, day.night) },
    )
    CompositionLocalProvider(LocalAura provides aura) { WelcomeContent(phase, onNext) }
}

@Composable
private fun WelcomeContent(phase: () -> Float, onNext: () -> Unit) {
    val accent = LocalAura.current.accent
    Box(Modifier.fillMaxSize()) {
    WelcomeScene(phase, Modifier.fillMaxSize())
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        // Brand lockup
        Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
            NurOrb(28.dp, float = false)
            Spacer(Modifier.width(Space.s))
            Text("Adhkaar", style = Type.titleM)
        }
        Spacer(Modifier.weight(1f))
        // Meaning first: the verse the whole app is built on.
        Text(
            "فَاذْكُرُونِي أَذْكُرْكُمْ",
            style = Type.arabicAccent.copy(color = accent, fontSize = 26.sp),
            textAlign = TextAlign.Left,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        // The translation carries a %1$s placeholder where the italic words go.
        val verse = stringResource(R.string.onboarding_verse)
        val emphasis = stringResource(R.string.onboarding_verse_emphasis)
        Text(
            buildAnnotatedString {
                val at = verse.indexOf("%1\$s")
                if (at < 0) {
                    append(verse)
                } else {
                    append(verse.substring(0, at))
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = accent)) { append(emphasis) }
                    append(verse.substring(at + 4))
                }
            },
            style = Type.displayL.copy(fontSize = 44.sp, lineHeight = 46.sp),
        )
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.onboarding_verse_reference), style = Type.caption)
        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            GlassPill(stringResource(R.string.onboarding_pill_on_time), icon = Icons.Rounded.Alarm, color = Nur.textSecondary)
            GlassPill(stringResource(R.string.onboarding_pill_focused), icon = Icons.Rounded.Lock, color = Nur.textSecondary)
            GlassPill(stringResource(R.string.onboarding_pill_private), icon = Icons.Rounded.Shield, color = Nur.textSecondary)
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(stringResource(R.string.onboarding_get_started), onClick = onNext)
        Spacer(Modifier.height(Space.l))
    }
    }
}

/** Fixes the welcome scene at one moment of its day (0–1) for screenshots; null plays it. */
@androidx.annotation.VisibleForTesting
internal var welcomeScenePhase: Float? = null

/** Where the welcome scene's day is: how high the sun is, and how far night has fallen. */
private class Day(val sunHeight: Float, val night: Float)

private fun dayAt(phase: Float) = Day(
    // Sun: rises through the first quarter, sinks through the second.
    sunHeight = if (phase < 0.25f) ease(phase / 0.25f) else ease((0.5f - phase) / 0.25f),
    // Night falls after sunset.
    night = ease((phase - 0.44f) / 0.14f),
)

/**
 * The day passing over still water: the sun rises in the orb, climbs to midday and sinks, and
 * becomes the crescent as the sky cools and the stars come out.
 * The orb sits on the horizon line; everything is drawn relative to it.
 * [phase] is read only while drawing, so as the day plays the scene is redrawn without being
 * recomposed; composition only follows which of the sun and the crescent are showing.
 */
@Composable
private fun WelcomeScene(phase: () -> Float, modifier: Modifier) {
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(8_000, easing = LinearEasing)), label = "twinkle",
    )
    val showSun by remember(phase) { derivedStateOf { dayAt(phase()).night < 1f } }
    val showCrescent by remember(phase) { derivedStateOf { dayAt(phase()).night > 0f } }
    val warm = Auras.dawn
    val cool = Auras.evening
    val stars = remember {
        val r = Random(21)
        List(46) { Triple(Offset(r.nextFloat(), r.nextFloat() * 0.6f), 0.5f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f) }
    }
    val orbSize = 172.dp
    BoxWithConstraints(modifier) {
        // The horizon sits just above the middle; the words lie on the water below it.
        val horizon = maxHeight * 0.46f
        // The sky behind the stars.
        Canvas(Modifier.fillMaxSize()) {
            val day = dayAt(phase())
            val night = day.night
            val hy = horizon.toPx()
            // Daylight: the sky warms from the horizon up, fading as night comes.
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF2A1636).copy(alpha = 0.55f * (1f - night)),
                    0.7f to Color(0xFF5A2A3A).copy(alpha = 0.55f * (1f - night)),
                    1f to warm.glows[0].copy(alpha = (0.3f + 0.2f * day.sunHeight) * (1f - night)),
                    startY = 0f, endY = hy,
                ),
                size = Size(size.width, hy),
            )
        }
        // The stars twinkle on every frame, so they have a layer of their own and the rest of the
        // scene is not redrawn with them.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val night = dayAt(phase()).night
            val w = size.width
            val hy = horizon.toPx()
            stars.forEach { (p, radius, offset) ->
                val a = (0.25f + 0.6f * ((sin(twinkle + offset) + 1f) / 2f)) * (0.1f + 0.9f * night)
                drawCircle(Color.White.copy(alpha = a * (1f - p.y)), radius.dp.toPx(), Offset(p.x * w, p.y * hy))
            }
        }
        // The water, in front of the stars.
        Canvas(Modifier.fillMaxSize()) {
            val night = dayAt(phase()).night
            val light = lerp(warm.accent, cool.accent, night)
            val glow = lerp(warm.glows[0], cool.glows[0], night)
            val w = size.width
            val hy = horizon.toPx()
            // Water: darker than the sky, so the horizon reads.
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF070B1F).copy(alpha = 0.6f), 0.35f to Color(0xFF04060F).copy(alpha = 0.92f), 1f to Nur.ink,
                    startY = hy, endY = size.height,
                ),
                topLeft = Offset(0f, hy), size = Size(w, size.height - hy),
            )
            // Reflection: a soft pool of light plus broken streaks, like light on a dark road.
            val cx = w / 2
            val orbR = orbSize.toPx() / 2
            // A round glow stretched sideways, so the pool has no edge.
            val pool = Offset(cx, hy + orbR * 0.18f)
            scale(scaleX = 3.2f, scaleY = 1f, pivot = pool) {
                drawCircle(
                    Brush.radialGradient(0f to glow.copy(alpha = 0.5f), 1f to Color.Transparent, center = pool, radius = orbR * 0.45f),
                    orbR * 0.45f, pool,
                )
            }
            // Streaks stay near the horizon, where a reflection would be.
            val streaks = listOf(0.03f to 0.9f, 0.075f to 0.7f, 0.13f to 0.52f, 0.2f to 0.36f)
            streaks.forEachIndexed { i, (dy, len) ->
                val y = hy + (size.height - hy) * dy
                val half = orbR * len
                drawLine(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to (if (i % 2 == 0) light else Color.White).copy(alpha = 0.55f - i * 0.08f),
                        1f to Color.Transparent, startX = cx - half, endX = cx + half,
                    ),
                    Offset(cx - half, y), Offset(cx + half, y), strokeWidth = (2.5f - i * 0.3f).dp.toPx(),
                )
            }
            // Horizon line, brightest under the orb.
            drawLine(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.6f), 1f to Color.Transparent,
                    startX = 0f, endX = w,
                ),
                Offset(0f, hy), Offset(w, hy), strokeWidth = 1.dp.toPx(),
            )
        }
        // The sun and the crescent share one place; one fades into the other. A faded layer clips
        // to its bounds, so each orb gets room for its halo.
        val halo = orbSize / 2
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = horizon - orbSize - Space.l - halo),
        ) {
            if (showSun) {
                Box(Modifier.graphicsLayer { alpha = 1f - dayAt(phase()).night }.padding(halo)) {
                    NurOrb(orbSize, aura = warm, symbol = OrbSymbol.Sunrise, sunHeight = { dayAt(phase()).sunHeight })
                }
            }
            if (showCrescent) {
                Box(Modifier.graphicsLayer { alpha = dayAt(phase()).night }.padding(halo)) {
                    NurOrb(orbSize, aura = cool, symbol = OrbSymbol.Crescent)
                }
            }
        }
    }
}

private fun ease(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

@Composable
private fun StepScaffold(
    step: Int,
    buttonLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    canContinue: Boolean = true,
    blockedHint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
            Spacer(Modifier.weight(1f))
            StepIndicator(step)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp)) // balances the back button so the indicator is centred
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.l))
            content()
            Spacer(Modifier.height(Space.xl))
        }
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.l)) {
            AnimatedVisibility(visible = !canContinue && blockedHint != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(
                    blockedHint.orEmpty(),
                    style = Type.caption.copy(color = Nur.textSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                )
            }
            PrimaryButton(buttonLabel, enabled = canContinue, onClick = onNext)
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val accent = LocalAura.current.accent
    Row {
        (1..STEPS).forEach { i ->
            val width by animateDpAsState(if (i == step) 24.dp else 8.dp, Motion.move(), label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(50))
                    .background(if (i <= step) accent else Color.White.copy(alpha = 0.14f)),
            )
        }
    }
}

@Composable
private fun ModeStep() {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    Text(stringResource(R.string.onboarding_mode_title), style = Type.displayL)
    Spacer(Modifier.height(Space.m))
    Text(stringResource(R.string.onboarding_mode_body), style = Type.bodyL)
    Spacer(Modifier.height(Space.xl))
    StrictnessOptions(settings.strictness) { s -> updateSettings(context) { it.copy(strictness = s) } }
}

@Composable
private fun TimesStep() {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    Text(stringResource(R.string.onboarding_times_title), style = Type.displayL)
    Spacer(Modifier.height(Space.m))
    Text(
        stringResource(R.string.onboarding_times_body),
        style = Type.bodyL,
    )
    Spacer(Modifier.height(Space.xl))
    SettingsGroup {
        LocationRow()
        RowDivider()
        SessionType.entries.forEach { type ->
            val next = AlarmScheduler.nextTime(context, type)
            val prayer = stringResource(if (type == SessionType.MORNING) R.string.settings_prayer_fajr else R.string.settings_prayer_asr)
            SettingsRow(
                stringResource(if (type == SessionType.MORNING) R.string.onboarding_morning else R.string.onboarding_evening),
                stringResource(R.string.onboarding_minutes_after_prayer, settings.schedule(type).offsetMinutes, prayer),
                if (type == SessionType.MORNING) Icons.Rounded.WbTwilight else Icons.Rounded.NightsStay,
                iconTint = Auras.of(type).accent,
                trailing = { Text(next?.let { formatTime(context, it) } ?: "—", style = Type.label, color = Nur.textSecondary) },
            )
        }
    }
    Spacer(Modifier.height(Space.m))
    Text(stringResource(R.string.onboarding_fine_tune), style = Type.caption)
}

