package org.adhkaar.app.enforce

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.data.UserDuaStore
import org.adhkaar.app.session.AlertPlayer
import org.adhkaar.app.session.AlertPolicy
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.SessionActivity

/**
 * Runs only while an alarm-started session is pending, and only as much as it has to:
 *
 * - It rings the alert ([AlertPlayer]) and keeps the process alive while it does.
 * - For a short while after the alert starts, and on every unlock, it brings the session to the
 *   front (Full screen and Lockdown). Holding a visible overlay is what lets Android 15+ open an
 *   activity from the background.
 * - In Lockdown it covers any app not on the allow list with [BlockOverlay], which stays until
 *   the user returns to the session.
 *
 * With the screen off it does nothing at all: every check stops on SCREEN_OFF and starts again on
 * SCREEN_ON / USER_PRESENT. It ends with the session, when the adhkaar window closes, or when
 * the user takes a break (it starts again when the break ends).
 */
class EnforcementService : Service() {
    private enum class Mode {
        /** Gentle, or no overlay permission: ring, then stop. The notification stays. */
        RING,
        /** Full screen (or Lockdown without Usage Access): bring the session up after the alert and on unlock. */
        PROMPT,
        /** As PROMPT, and cover every app that isn't allowed. */
        LOCKDOWN,
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var detector: ForegroundAppDetector
    private lateinit var overlay: BlockOverlay
    private lateinit var extraAllowed: Set<String>
    private var mode = Mode.RING
    private var active = false
    /** Until then the session is brought to the front: after the alert starts, and after an unlock. */
    private var promptUntil = 0L
    private var lastLaunchAt = 0L
    /** The user chose "Call or emergency": the phone and messaging apps are open to them, nothing else. */
    private var phoneRequested = false
    private var phoneRequestedAt = 0L
    /** Session progress when the alert started; any change means the user is counting. */
    private var progressAtRing: Map<String, Int> = emptyMap()

    private val tick = Runnable { check() }

    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(tick)
                    overlay.hide()
                }
                Intent.ACTION_SCREEN_ON -> check()
                Intent.ACTION_USER_PRESENT -> onUnlocked()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(org.adhkaar.app.data.Languages.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(this)
        overlay = BlockOverlay(this, onReturn = { returnToSession() }, onCall = { openDialer() })
        extraAllowed = defaultPhoneApps()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Screen broadcasts only reach receivers registered at runtime; no permission needed.
        ContextCompat.registerReceiver(this, screenEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pending = SessionState.get(this).pending
        if (pending == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val strictness = SettingsStore.get(this).current.strictness
        val ring = intent?.getBooleanExtra(EXTRA_RING, false) == true
        // Ring first, so the first notification already carries the Stop button.
        if (ring) startRinging()
        try {
            ServiceCompat.startForeground(
                this, Notifications.SESSION_ID,
                Notifications.build(this, pending.type, fullScreen = ring && strictness != Strictness.GENTLE),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else 0,
            )
        } catch (_: RuntimeException) {
            // Android refused a foreground start (e.g. background start not allowed). The alert
            // keeps ringing while the process lives; the watchdog retries Lockdown.
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        mode = modeFor(strictness)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun modeFor(strictness: Strictness): Mode = when {
        strictness == Strictness.LOCKDOWN && SessionLauncher.isLockdownActive(this) &&
            ForegroundAppDetector.hasPermission(this) -> Mode.LOCKDOWN
        // Without Usage Access we can't tell which app is open, so Lockdown falls back to bringing the session up.
        AlertPolicy.opensOnUnlock(strictness) && Settings.canDrawOverlays(this) -> Mode.PROMPT
        else -> Mode.RING
    }

    private fun startRinging() {
        progressAtRing = SessionState.get(this).progress()
        promptUntil = System.currentTimeMillis() + PROMPT_TIMEOUT_MS
        AlertPlayer.start(this) {
            Notifications.refresh(this)
            handler.post(tick)
        }
    }

    private fun check() {
        handler.removeCallbacks(tick)
        if (!active) return
        val now = System.currentTimeMillis()
        val state = SessionState.get(this)
        val pending = state.pending ?: return finish(removeNotification = true)

        if (!AlertPolicy.isWindowOpen(pending, SessionLauncher.windowMinutes(this, pending), now)) {
            SessionLauncher.onWindowClosed(this)
            return finish(removeNotification = false)
        }
        // On a break the user is free: no ringing, no prompting, no cover. The alarm at the break's
        // end starts the service again; its notification ("On a break until…") stays meanwhile.
        if (AlertPolicy.isPaused(pending, now)) return finish(removeNotification = false)
        if (AlertPlayer.isRinging && state.progress() != progressAtRing) SessionLauncher.silence(this)
        val ringing = AlertPlayer.isRinging
        if (mode == Mode.RING && !ringing) return finish(removeNotification = false)

        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive) {
            // Nothing to watch in a pocket. SCREEN_ON starts the checks again.
            overlay.hide()
            return
        }
        if (keyguard.isKeyguardLocked) {
            // The session shows over the lock screen by itself; while ringing, watch for the first count.
            overlay.hide()
            if (ringing) schedule(RINGING_POLL_MS)
            return
        }
        if (isInCall()) {
            overlay.hide()
            // No broadcast says when a call ends, so keep looking.
            return schedule(RINGING_POLL_MS)
        }
        val current = detector.current()
        if (phoneRequested) {
            // Stay out of the way only while the phone or messaging app is in front; the moment the
            // user steps anywhere else, the adhkaar comes back. Without Usage Access the app in
            // front can't be seen, so the phone gets a fixed minute instead.
            val opening = now - phoneRequestedAt < PHONE_OPENING_MS
            val inPhoneApp = opening || if (current != null) {
                current in extraAllowed || current in AllowList.ALWAYS
            } else {
                now - phoneRequestedAt < EMERGENCY_FALLBACK_MS
            }
            if (inPhoneApp) {
                overlay.hide()
                return schedule(RINGING_POLL_MS)
            }
            phoneRequested = false
            if (mode != Mode.RING) promptUntil = now + PROMPT_TIMEOUT_MS
        }

        val prompting = mode != Mode.RING && now < promptUntil
        if (prompting) {
            if (SessionActivity.isVisible) {
                promptUntil = 0L
            } else {
                // Over whatever app is open, the waiting screen asks; the session opens only when the
                // user taps Start or Return on it.
                showOverlay(pending.type)
                return schedule(PROMPT_POLL_MS)
            }
        }

        if (mode == Mode.LOCKDOWN) {
            // The cover stays until the user taps Return (or moves to an allowed app): no relaunching behind it.
            val allowed = SessionActivity.isVisible || AllowList.isAllowed(current, packageName, extraAllowed)
            if (allowed) overlay.hide() else showOverlay(pending.type)
            return schedule(LOCKDOWN_POLL_MS)
        }
        overlay.hide()
        if (ringing) schedule(RINGING_POLL_MS)
    }

    /** The user is back at the phone: bring the session up (Full screen, Lockdown); Gentle just stops ringing. */
    private fun onUnlocked() {
        if (mode == Mode.RING) {
            AlertPlayer.stop()
        } else {
            SessionLauncher.silence(this)
            promptUntil = System.currentTimeMillis() + PROMPT_TIMEOUT_MS
        }
        check()
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun showOverlay(type: SessionType) {
        if (overlay.isShowing) return
        val targets = (AdhkaarRepository.forSession(this, type) + UserDuaStore.get(this).forSession(type)).map { it.id to it.count }
        val progress = SessionState.get(this).progress()
        overlay.show(type, AlertPolicy.remaining(targets, progress), started = progress.values.any { it > 0 })
    }

    private fun returnToSession() {
        SessionLauncher.silence(this)
        launchSession(force = true)
    }

    private fun launchSession(force: Boolean) {
        val pending = SessionState.get(this).pending ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastLaunchAt < RELAUNCH_INTERVAL_MS) return
        lastLaunchAt = now
        try {
            startActivity(Notifications.sessionIntent(this, pending.type))
        } catch (_: RuntimeException) {
        }
    }

    private fun openDialer() {
        phoneRequested = true
        phoneRequestedAt = System.currentTimeMillis()
        overlay.hide()
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
        }
    }

    /** Any call, including WhatsApp/Telegram, puts audio into call or communication mode. No permission needed. */
    private fun isInCall(): Boolean {
        val audioMode = getSystemService(AudioManager::class.java).mode
        return audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION ||
            audioMode == AudioManager.MODE_RINGTONE
    }

    private fun defaultPhoneApps(): Set<String> = setOfNotNull(
        runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
        runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull(),
    )

    private fun finish(removeNotification: Boolean) {
        active = false
        handler.removeCallbacks(tick)
        overlay.hide()
        ServiceCompat.stopForeground(
            this,
            if (removeNotification) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH,
        )
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacks(tick)
        overlay.hide()
        unregisterReceiver(screenEvents)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RING = "ring"
        private const val PROMPT_TIMEOUT_MS = 20_000L
        private const val RELAUNCH_INTERVAL_MS = 2_500L
        /** Without Usage Access, how long the phone app gets before the waiting screen returns. */
        private const val EMERGENCY_FALLBACK_MS = 60_000L
        /** Time for the phone app to come to the front before we look at what's in front. */
        private const val PHONE_OPENING_MS = 8_000L
        private const val PROMPT_POLL_MS = 500L
        private const val LOCKDOWN_POLL_MS = 600L
        private const val RINGING_POLL_MS = 1_000L

        /**
         * Starts (or refreshes) the service; [ring] also rings the alert. Returns false when
         * Android refused the start, so the caller can ring without it.
         */
        fun start(context: Context, ring: Boolean = false): Boolean = try {
            ContextCompat.startForegroundService(
                context, Intent(context, EnforcementService::class.java).putExtra(EXTRA_RING, ring),
            )
            true
        } catch (_: RuntimeException) {
            // ForegroundServiceStartNotAllowedException: the watchdog or the next app open will retry.
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EnforcementService::class.java))
        }
    }
}
