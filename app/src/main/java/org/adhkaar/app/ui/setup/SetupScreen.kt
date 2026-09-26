package org.adhkaar.app.ui.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.oem.OemAutostart
import org.adhkaar.app.setup.Requirement
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.components.rememberResumeTick
import org.adhkaar.app.ui.settings.GlassSwitchRow
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

private fun Requirement.icon(): ImageVector = when (this) {
    Requirement.NOTIFICATIONS -> Icons.Rounded.Notifications
    Requirement.EXACT_ALARMS -> Icons.Rounded.Alarm
    Requirement.FULL_SCREEN -> Icons.Rounded.OpenInFull
    Requirement.OVERLAY -> Icons.Rounded.Layers
    Requirement.USAGE_ACCESS -> Icons.Rounded.QueryStats
    Requirement.BATTERY -> Icons.Rounded.BatteryChargingFull
}

/** Pushed screen: header + checklist. */
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(Space.s))
            SetupIntro()
            Spacer(Modifier.height(Space.xl))
            RequirementsList(settings.strictness)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
fun SetupIntro() {
    Text(stringResource(R.string.setup_title), style = Type.displayL)
    Spacer(Modifier.height(Space.s))
    Text(
        stringResource(R.string.setup_intro),
        style = Type.bodyL,
    )
}

@Composable
fun RequirementsList(strictness: Strictness) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val resumeTick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val items = Requirement.relevantFor(strictness)
    val granted = remember(resumeTick, refresh, strictness) { items.associateWith { it.isGranted(context) } }
    val guide = remember { OemAutostart.guide() }
    // On Xiaomi phones the background switches can be read; elsewhere the user confirms by hand.
    val backgroundChecked = remember(resumeTick, refresh) { OemAutostart.xiaomiBackgroundAllowed(context) }
    val backgroundOk = backgroundChecked ?: settings.oemAutostartDone

    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (!ok) Requirement.open(context, Requirement.NOTIFICATIONS)
    }

    val total = items.size + if (guide != null) 1 else 0
    val done = granted.count { it.value } + if (guide != null && backgroundOk) 1 else 0
    val progress by animateFloatAsState(if (total == 0) 1f else done.toFloat() / total, Motion.move(), label = "setup")
    val accent = LocalAura.current.accent

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (done == total) stringResource(R.string.setup_all_set) else pluralStringResource(R.plurals.setup_ready_count, done, done, total), style = Type.label, color = accent)
        Spacer(Modifier.width(Space.m))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(LocalAura.current.action)),
            )
        }
    }
    Spacer(Modifier.height(Space.l))
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        items.forEach { req ->
            StepCard(
                icon = req.icon(),
                title = stringResource(req.title),
                body = stringResource(req.why),
                required = strictness in req.requiredFor,
                done = granted[req] == true,
            ) {
                if (req == Requirement.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                    notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Requirement.open(context, req)
                }
            }
        }
        if (guide != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.PhoneAndroid, if (backgroundOk) Nur.success else accent)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        if (!backgroundOk) {
                            Text(stringResource(R.string.setup_required), style = Type.overline.copy(color = Nur.danger))
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(stringResource(R.string.setup_oem_title, guide.brand), style = Type.label)
                        Text(stringResource(R.string.setup_oem_body), style = Type.caption)
                    }
                }
                Spacer(Modifier.height(Space.m))
                guide.steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(start = 56.dp, bottom = Space.s)) {
                        Text("${i + 1}", style = Type.caption.copy(color = accent), modifier = Modifier.width(16.dp))
                        Text(stringResource(step), style = Type.caption.copy(color = Nur.textSecondary))
                    }
                }
                Spacer(Modifier.height(Space.s))
                Row(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    CompactButton(stringResource(R.string.setup_open_settings), filled = true) { OemAutostart.open(context, guide) }
                    CompactButton(stringResource(R.string.setup_guide), filled = false) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(OemAutostart.helpUrl(guide))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
                if (backgroundChecked != null) {
                    // Read from the phone itself, so there is nothing to confirm by hand.
                    Row(Modifier.padding(start = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (backgroundChecked) Icons.Rounded.Check else Icons.Rounded.PhoneAndroid, null,
                            tint = if (backgroundChecked) Nur.success else Nur.danger, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            stringResource(if (backgroundChecked) R.string.setup_oem_checked_ok else R.string.setup_oem_checked_missing),
                            style = Type.caption.copy(color = if (backgroundChecked) Nur.success else Nur.textSecondary),
                        )
                    }
                } else {
                    GlassSwitchRow(stringResource(R.string.setup_oem_done), settings.oemAutostartDone, Modifier.padding(start = 56.dp)) { v ->
                        SettingsStore.get(context).update { it.copy(oemAutostartDone = v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(icon: ImageVector, title: String, body: String, required: Boolean, done: Boolean, onAllow: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (done) Nur.success else LocalAura.current.accent)
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                if (!done) {
                    Text(
                        stringResource(if (required) R.string.setup_required else R.string.setup_recommended),
                        style = Type.overline.copy(color = if (required) Nur.danger else Nur.textTertiary),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(title, style = Type.label)
                Spacer(Modifier.height(2.dp))
                Text(body, style = Type.caption)
            }
            Spacer(Modifier.width(Space.m))
            AnimatedContent(done, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "step") { isDone ->
                if (isDone) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Nur.success.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.setup_allowed), tint = Nur.success, modifier = Modifier.size(18.dp))
                    }
                } else {
                    CompactButton(stringResource(R.string.setup_allow), filled = true, onClick = onAllow)
                }
            }
        }
    }
}

/** 36dp pill for actions inside cards. The filled one is the card's main action, so it clicks firmly. */
@Composable
fun CompactButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(36.dp)
            .pressable(scale = 0.95f, haptic = if (filled) true else null, onClick = onClick)
            .then(if (filled) Modifier.clip(shape).background(Brush.horizontalGradient(LocalAura.current.action)) else Modifier.glass(shape, 2))
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.label.copy(fontSize = 13.sp), color = Color.White)
    }
}
