package org.adhkaar.app.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.adhkaar.app.R
import org.adhkaar.app.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import org.adhkaar.app.data.AppSettings
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.widget.AdhkaarWidget
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassSurface
import org.adhkaar.app.ui.components.Haptics
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.home.strictnessDescription
import org.adhkaar.app.ui.home.strictnessLabel
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.util.Locale

/** Updates settings and re-arms the alarms, since almost every setting affects them. */
fun updateSettings(context: Context, transform: (AppSettings) -> AppSettings) {
    SettingsStore.get(context).update(transform)
    AlarmScheduler.scheduleAll(context)
    AdhkaarWidget.refresh(context)
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text.uppercase(), style = Type.overline,
        modifier = Modifier.padding(top = Space.xxl, bottom = Space.m),
    )
}

/** Inset-grouped glass list. Rows inside are separated by hairlines. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.xs), content = content)
}

@Composable
fun RowDivider(inset: Boolean = true) {
    Box(
        Modifier
            // Text starts at 20 (inset) + 36 (badge) + 16 (gap) = 72.
            .padding(start = if (inset) 72.dp else Space.gutter, end = Space.gutter)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f)),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LocalAura.current.accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = { if (onClick != null) Chevron() },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.pressable(scale = 0.985f, onClick = onClick) else Modifier)
            .padding(horizontal = Space.gutter, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, iconTint, size = 36.dp)
            Spacer(Modifier.width(Space.l))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.label)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.caption)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.m))
            trailing()
        }
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nur.textTertiary, modifier = Modifier.size(20.dp))
}

@Composable
fun ValueText(text: String) {
    Text(text, style = Type.label, color = LocalAura.current.accent)
}

fun strictnessIcon(s: Strictness) = when (s) {
    Strictness.GENTLE -> Icons.Rounded.NotificationsNone
    Strictness.FULL_SCREEN -> Icons.Rounded.OpenInFull
    Strictness.LOCKDOWN -> Icons.Rounded.Lock
}

/** Three selectable glass cards. Choosing a new one ticks like a selector; the current one just taps. */
@Composable
fun StrictnessOptions(selected: Strictness, enabled: Boolean = true, onSelect: (Strictness) -> Unit) {
    val accent = LocalAura.current.accent
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Strictness.entries.forEach { s ->
            val isSelected = s == selected
            val edge by animateColorAsState(if (isSelected) accent.copy(alpha = 0.7f) else Color.Transparent, tween(220), label = "edge")
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, edge, RoundedCornerShape(Radius.card)),
                level = if (isSelected) 2 else 1,
                onClick = if (enabled) ({
                    if (isSelected) Haptics.tap(view) else Haptics.tick(view)
                    onSelect(s)
                }) else null,
                haptic = false,
            ) {
                Row(Modifier.padding(Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(strictnessIcon(s), if (isSelected) accent else Nur.textTertiary)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strictnessLabel(s), style = Type.titleM)
                            if (s == Strictness.LOCKDOWN) {
                                Spacer(Modifier.width(Space.s))
                                Text(stringResource(R.string.mode_recommended), style = Type.overline.copy(color = accent))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(strictnessDescription(s), style = Type.caption)
                    }
                    Spacer(Modifier.width(Space.m))
                    RadioDot(isSelected)
                }
            }
        }
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val accent = LocalAura.current.accent
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.5.dp, if (selected) accent else Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Nur.ink, modifier = Modifier.size(16.dp))
    }
}

/**
 * Glass circles for − and +, with the value between them. Each step ticks like a detent; at a
 * limit the button still takes the press and answers with a soft "no", so the end is felt.
 */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean = true, canPlus: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, stringResource(R.string.settings_decrease), canMinus, onMinus)
        Text(
            value, style = Type.label.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        StepButton(Icons.Rounded.Add, stringResource(R.string.settings_increase), canPlus, onPlus)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(36.dp)
            .glass(CircleShape, level = 2)
            .semantics { if (!enabled) disabled() }
            // At a limit it gives only a little, as if against a stop.
            .pressable(scale = if (enabled) 0.9f else 0.96f, haptic = false) {
                if (enabled) {
                    Haptics.tick(view)
                    onClick()
                } else {
                    Haptics.reject(view)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) Nur.textPrimary else Nur.textDisabled, modifier = Modifier.size(18.dp))
    }
}

/** A dialog drawn as a dark glass panel. */
@Composable
fun GlassDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val accent = LocalAura.current.accent
    // It rises in: a small lift and scale, and a light that blooms across the glass and settles.
    val shown = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 320f)) }
        bloom.animateTo(1f, tween(260))
        bloom.animateTo(0.35f, tween(700, easing = Motion.emphasized))
    }
    // Own width and margins, so the dialog sits the same on every phone.
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = Space.xl)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    val scale = 0.94f + 0.06f * shown.value
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - shown.value) * 24.dp.toPx()
                }
                .clip(RoundedCornerShape(Radius.hero))
                .background(Color(0xFF0C1022))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to accent.copy(alpha = 0.22f * bloom.value), 1f to Color.Transparent,
                            center = Offset(size.width / 2, 0f), radius = size.width * 0.9f,
                        ),
                    )
                }
                .glass(RoundedCornerShape(Radius.hero), level = 2)
                .padding(Space.gutter),
        ) {
            Text(title, style = Type.titleL)
            Spacer(Modifier.height(Space.l))
            content()
        }
    }
}

/** "Use my location" for prayer times. Coarse location only, used once. */
@Composable
fun LocationRow() {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun fetch() {
        busy = true
        error = null
        currentLocation(context) { location ->
            busy = false
            if (location == null) {
                error = R.string.settings_location_error
            } else {
                updateSettings(context) { it.copy(latitude = location.latitude, longitude = location.longitude) }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetch() else error = R.string.settings_location_denied
    }

    SettingsRow(
        title = stringResource(if (settings.hasLocation) R.string.settings_location_set else R.string.settings_use_location),
        subtitle = error?.let { stringResource(it) } ?: if (settings.hasLocation) {
            stringResource(
                R.string.settings_location_coords,
                String.format(Locale.US, "%.2f", settings.latitude),
                String.format(Locale.US, "%.2f", settings.longitude),
            )
        } else {
            stringResource(R.string.settings_location_hint)
        },
        icon = Icons.Rounded.LocationOn,
        iconTint = if (error != null) Nur.danger else LocalAura.current.accent,
        onClick = {
            if (busy) return@SettingsRow
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fetch()
            } else {
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        trailing = {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalAura.current.accent)
                settings.hasLocation -> Icon(Icons.Rounded.Check, null, tint = Nur.success, modifier = Modifier.size(20.dp))
                else -> Chevron()
            }
        },
    )
}

@SuppressLint("MissingPermission")
private fun currentLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(LocationManager::class.java)
    val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (last != null) return onResult(last)
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null)
    LocationManagerCompat.getCurrentLocation(
        lm, provider, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(context),
    ) { onResult(it) }
}

/** A label with a switch, for use inside cards (outside SettingsGroup rows). */
@Composable
fun GlassSwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.bodyM, modifier = Modifier.weight(1f))
        org.adhkaar.app.ui.components.GlassSwitch(checked, onCheckedChange)
    }
}
