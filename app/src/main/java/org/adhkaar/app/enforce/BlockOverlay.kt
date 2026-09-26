package org.adhkaar.app.enforce

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/**
 * Full-screen window over any app opened while a Lockdown session is pending. It stays until the
 * user taps Return (or switches to an allowed app such as the dialer), so it never flashes away.
 * Drawn with the app's own Compose theme; a service window has no lifecycle or saved-state owner,
 * so each showing gets its own [OverlayOwner].
 */
class BlockOverlay(
    private val context: Context,
    private val onReturn: () -> Unit,
    private val onCall: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var owner: OverlayOwner? = null

    val isShowing get() = view != null

    /** [remaining] = adhkaar not yet finished in this session. */
    /** [started]: the user has counted before, so the button says Return rather than Start. */
    fun show(type: SessionType, remaining: Int, started: Boolean) {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val owner = OverlayOwner()
        val root = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                AdhkaarTheme(aura = Auras.evening) {
                    BlockScreen(type, remaining, started, onReturn, onCall)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Windows added from a service aren't hardware accelerated unless asked; the glow and blur need it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply {
            // Reach under the status and navigation bars so nothing of the app below shows at the edges.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager.addView(root, params)
            view = root
            this.owner = owner
        } catch (_: RuntimeException) {
            // Permission revoked between the check and the call.
            owner.destroy()
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        try {
            windowManager.removeView(v)
        } catch (_: RuntimeException) {
        }
        owner?.destroy()
        owner = null
    }
}

/** What Compose needs from its view tree: a resumed lifecycle and a (never persisted) saved state. */
internal class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
internal fun BlockScreen(type: SessionType, remaining: Int, started: Boolean, onReturn: () -> Unit, onCall: () -> Unit) {
    val sessionAura = Auras.of(type)
    AuraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Space.xl, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            NurOrb(128.dp, aura = sessionAura)
            Spacer(Modifier.height(Space.xxl))
            Text(
                stringResource(if (type == SessionType.MORNING) R.string.block_title_morning else R.string.block_title_evening),
                style = Type.displayM, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.m))
            Text(stringResource(R.string.block_body), style = Type.bodyL, textAlign = TextAlign.Center)
            if (remaining > 0) {
                Spacer(Modifier.height(Space.xl))
                GlassPill(pluralStringResource(R.plurals.block_remaining, remaining, remaining), dot = sessionAura.accent)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(if (started) R.string.block_return else R.string.block_start), onClick = onReturn)
            Spacer(Modifier.height(Space.s))
            // Calls and emergencies always stay one tap away.
            Row(
                Modifier
                    .pressable(shape = RoundedCornerShape(50), onClick = onCall)
                    .padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Call, null, tint = Nur.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.block_call), style = Type.label.copy(color = Nur.textSecondary))
            }
        }
    }
}
