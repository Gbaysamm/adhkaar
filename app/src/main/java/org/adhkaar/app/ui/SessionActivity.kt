package org.adhkaar.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.session.SessionScreen
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import java.time.LocalTime

/**
 * Full-screen session. Opens over the lock screen and keeps the screen on. It shares the app's
 * task, so the launcher icon returns to it, and it reopens on the same page after the process is
 * killed (see [org.adhkaar.app.data.ResumeState]).
 */
class SessionActivity : ComponentActivity() {
    private var type by mutableStateOf(SessionType.MORNING)
    /** A collection held by its reminder (before sleep, after salah), shown instead of morning/evening. */
    private var collection by mutableStateOf<String?>(null)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(org.adhkaar.app.data.Languages.wrap(base))
    }

    // Any touch on the session answers the alert, so the ringing stops.
    override fun onUserInteraction() {
        super.onUserInteraction()
        SessionLauncher.silence(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        type = resolveType(intent)
        collection = intent?.getStringExtra(EXTRA_COLLECTION)

        onBackPressedDispatcher.addCallback(this) {
            if (SessionLauncher.isLockdownActive(this@SessionActivity)) {
                Toast.makeText(this@SessionActivity, getString(R.string.session_lockdown_toast), Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }

        setContent {
            val held = collection
            if (held != null) {
                // Read or closed (with the usual confirmation), the reminder lets go.
                org.adhkaar.app.ui.session.CollectionSessionScreen(held, onFinish = {
                    SessionLauncher.endCollection(this, held)
                    finish()
                })
            } else {
                AdhkaarTheme(aura = Auras.of(type)) {
                    SessionScreen(type = type, onFinish = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        type = resolveType(intent)
        collection = intent.getStringExtra(EXTRA_COLLECTION)
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    private fun resolveType(intent: Intent?): SessionType =
        SessionType.fromKey(intent?.getStringExtra(EXTRA_TYPE))
            ?: SessionState.get(this).pending?.type
            ?: if (LocalTime.now().hour < 12) SessionType.MORNING else SessionType.EVENING

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_COLLECTION = "collection"

        /** Read by the enforcement service: while this is on screen, nothing needs covering. */
        @Volatile
        var isVisible = false
            private set
    }
}
