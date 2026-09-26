package org.adhkaar.app.session

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.adhkaar.app.R
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.enforce.OverlayOwner
import org.adhkaar.app.ui.MainActivity
import org.adhkaar.app.ui.SessionActivity
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/**
 * A card over whatever is on screen when a salah or collection reminder comes, so it isn't
 * lost in the shade. It doesn't block anything: a tap outside, or a few minutes, and it's gone.
 * Needs "display over other apps" (already granted for Lockdown); without it, the notification
 * alone carries the reminder.
 */
object ReminderPopup {
    sealed interface Kind {
        data class Salah(val prayer: Prayer) : Kind
        data class Collection(val id: String) : Kind
    }

    private const val SHOWN_FOR_MS = 3 * 60_000L
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var owner: OverlayOwner? = null

    fun show(context: Context, kind: Kind) = main.post { showNow(context.applicationContext, kind) }

    private fun showNow(context: Context, kind: Kind) {
        // Never over the adhkaar themselves.
        if (SessionActivity.isVisible || !Settings.canDrawOverlays(context)) return
        // Locked, the notification's full-screen card (ReminderActivity) shows instead: a window
        // over other apps would only appear after unlocking, on top of that card.
        if (context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        hide()
        val owner = OverlayOwner()
        val root = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                AdhkaarTheme(aura = if (kind is Kind.Collection && kind.id == "before_sleep") Auras.evening else Auras.dawn) {
                    PopupCard(kind, onDismiss = ::hide, onOpen = { open(context, kind) })
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        try {
            context.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            this.owner = owner
            main.postDelayed(::hide, SHOWN_FOR_MS)
        } catch (_: RuntimeException) {
            owner.destroy()
        }
    }

    fun hide() {
        main.removeCallbacksAndMessages(null)
        val v = view ?: return
        view = null
        runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
        owner?.destroy()
        owner = null
    }

    private fun open(context: Context, kind: Kind) {
        hide()
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (kind is Kind.Collection) intent.putExtra(Notifications.EXTRA_OPEN_COLLECTION, kind.id)
        runCatching { context.startActivity(intent) }
    }
}

@Composable
internal fun PopupCard(kind: ReminderPopup.Kind, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * rise.value))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            Modifier
                .padding(horizontal = Space.gutter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 60.dp.toPx()
                }
                // Taps on the card stay on the card.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            level = 2,
        ) {
            when (kind) {
                is ReminderPopup.Kind.Salah -> SalahContent(kind.prayer, onDismiss)
                is ReminderPopup.Kind.Collection -> CollectionContent(kind.id, onOpen, onDismiss)
            }
        }
    }
}

@Composable
private fun SalahContent(prayer: Prayer, onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        IconBadge(Icons.Rounded.Mosque, size = 56.dp)
        Spacer(Modifier.height(Space.m))
        Text(
            stringResource(R.string.notif_salah_title, stringResource(prayer.label)),
            style = Type.titleL, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.popup_salah_body), style = Type.bodyM, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.m))
        // Three small steps: what "getting ready" means right now.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            GlassPill(stringResource(R.string.popup_step_wudu), icon = Icons.Rounded.WaterDrop)
            GlassPill(stringResource(R.string.popup_step_masjid), icon = Icons.Rounded.Mosque)
            GlassPill(stringResource(R.string.popup_step_phone), icon = Icons.Rounded.PhonelinkErase)
        }
        Spacer(Modifier.height(Space.l))
        Text("الصَّلَاةُ عَلَى وَقْتِهَا", style = Type.arabicAccent, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(R.string.popup_salah_hadith), style = Type.bodyM.copy(color = Nur.textPrimary), textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(R.string.popup_salah_source).uppercase(),
            style = Type.overline.copy(color = LocalAura.current.accent), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.popup_ready), Modifier.fillMaxWidth(), icon = null, onClick = onDismiss)
    }
}

@Composable
private fun CollectionContent(id: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val sleep = id == "before_sleep"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        IconBadge(if (sleep) Icons.Rounded.Bedtime else Icons.Rounded.Mosque, size = 56.dp)
        Spacer(Modifier.height(Space.m))
        Text(
            stringResource(if (sleep) R.string.moment_title_before_sleep else R.string.moment_title_after_salah),
            style = Type.titleL, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            stringResource(if (sleep) R.string.reminder_before_sleep_text else R.string.reminder_after_salah_text),
            style = Type.bodyM, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.popup_open), Modifier.fillMaxWidth(), onClick = onOpen)
        Box(Modifier.fillMaxWidth().height(48.dp).pressable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.popup_later), style = Type.label.copy(color = Nur.textSecondary))
        }
    }
}
