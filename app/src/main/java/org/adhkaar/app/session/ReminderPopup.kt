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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
        /** The morning or evening adhkaar's time ended unread. */
        data class Missed(val type: org.adhkaar.app.data.SessionType) : Kind
    }

    private const val SHOWN_FOR_MS = 3 * 60_000L
    const val BLUR_RADIUS = 48
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
                AdhkaarTheme(aura = auraFor(kind)) {
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
        ).apply {
            // Android 12+ can blur the app behind the window: the frost the card sits on.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = BLUR_RADIUS
            }
        }
        try {
            context.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            this.owner = owner
            main.postDelayed(::hide, SHOWN_FOR_MS)
            // Shown here, it needn't be shown again as a card in the app.
            if (kind is Kind.Missed) MissedAdhkaar.markSeen(context, MissedAdhkaar.Missed(kind.type, java.time.LocalDate.now()))
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
        // Missed: "read them anyway" opens that session's list in the Adhkaar tab.
        if (kind is Kind.Missed) intent.putExtra(Notifications.EXTRA_OPEN_COLLECTION, kind.type.key)
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
            // A deeper dim than in-app dialogs: behind it may be any app, bright or busy.
            .background(Color.Black.copy(alpha = 0.6f * rise.value))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Frosted rather than liquid glass: over another app or the lock screen a see-through card
        // lets whatever is behind it show through the text, so this one is nearly solid, in the
        // aura's own colours, with the glass edge and a soft light at the top.
        val aura = LocalAura.current
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(horizontal = Space.gutter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 60.dp.toPx()
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(aura.base[0].copy(alpha = 0.97f), aura.base.last().copy(alpha = 0.97f))))
                .background(Brush.radialGradient(listOf(aura.glows[0].copy(alpha = 0.22f), Color.Transparent), radius = 900f, center = Offset(0f, 0f)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), shape)
                // Taps on the card stay on the card.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(Space.gutter),
        ) {
            when (kind) {
                is ReminderPopup.Kind.Salah -> SalahContent(kind.prayer, onDismiss)
                is ReminderPopup.Kind.Collection -> CollectionContent(kind.id, onOpen, onDismiss)
                is ReminderPopup.Kind.Missed -> MissedContent(kind.type, onOpen, onDismiss)
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

/** Before sleep and a missed evening are night; everything else is dawn. */
internal fun auraFor(kind: ReminderPopup.Kind) = when {
    kind is ReminderPopup.Kind.Collection && kind.id == "before_sleep" -> Auras.evening
    kind is ReminderPopup.Kind.Missed && kind.type == org.adhkaar.app.data.SessionType.EVENING -> Auras.evening
    else -> Auras.dawn
}

@Composable
private fun MissedContent(type: org.adhkaar.app.data.SessionType, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val morning = type == org.adhkaar.app.data.SessionType.MORNING
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        IconBadge(if (morning) Icons.Rounded.WbTwilight else Icons.Rounded.Bedtime, size = 56.dp)
        Spacer(Modifier.height(Space.m))
        Text(
            stringResource(if (morning) R.string.missed_title_morning else R.string.missed_title_evening),
            style = Type.titleL, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.missed_body), style = Type.bodyM, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.l))
        Text("أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ", style = Type.arabicAccent, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(R.string.missed_hadith), style = Type.bodyM.copy(color = Nur.textPrimary), textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(R.string.missed_hadith_source).uppercase(),
            style = Type.overline.copy(color = LocalAura.current.accent), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.missed_ok), Modifier.fillMaxWidth(), icon = null, onClick = onDismiss)
        Box(Modifier.fillMaxWidth().height(48.dp).pressable(onClick = onOpen), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.missed_read), style = Type.label.copy(color = Nur.textSecondary))
        }
    }
}
