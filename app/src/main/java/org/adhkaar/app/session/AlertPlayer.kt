package org.adhkaar.app.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.adhkaar.app.R
import org.adhkaar.app.data.AlertSound
import org.adhkaar.app.data.SettingsStore
import java.io.File

/**
 * Rings the session alert: the app's chime, or the user's own recorded alert, repeated with a
 * short pause for [AlertPolicy.RING_MS]. It plays as an alarm (USAGE_ALARM), so it follows the
 * alarm volume and Do Not Disturb treats it like any other alarm.
 */
object AlertPlayer {
    /** The user's recorded alert lives with their other recordings (see [Recordings]). */
    const val RECORDING_ID = "_session_alert"

    private val handler = Handler(Looper.getMainLooper())
    private val ringingState = MutableStateFlow(false)
    private var player: MediaPlayer? = null
    private var audio: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var onStopped: (() -> Unit)? = null

    /** For the session screen's Stop button (see [SessionLauncher.silence]). */
    val ringing: StateFlow<Boolean> = ringingState.asStateFlow()
    val isRinging: Boolean get() = ringingState.value

    fun recording(context: Context): File? = Recordings.saved(context, RECORDING_ID).takeIf { it.exists() }

    /**
     * Starts ringing; [onStopped] runs once when it stops, by itself or through [stop].
     * [once] plays the sound a single time, like a notification (Gentle mode).
     */
    fun start(context: Context, once: Boolean = false, onStopped: () -> Unit = {}) {
        stop()
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        // Never ring over a phone or video call.
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return
        val own = recording(app)?.takeIf { SettingsStore.get(app).current.alertSound == AlertSound.RECORDING }
        // A recording that won't play (e.g. a take too short to encode) falls back to the chime.
        val speech = own?.let { load(app, it) }
        val mp = speech ?: load(app, null) ?: return
        val attributes = attributes(spoken = speech != null)
        val gap = if (speech != null) AlertPolicy.RECORDING_GAP_MS else AlertPolicy.CHIME_GAP_MS
        mp.setOnCompletionListener { done ->
            if (once) stop() else handler.postDelayed({ runCatching { done.seekTo(0); done.start() } }, gap)
        }

        // Keeps the CPU up through the pauses between repeats, where the player holds no lock.
        wakeLock = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adhkaar:alert")
            .apply { setReferenceCounted(false); acquire(AlertPolicy.RING_MS + 5_000L) }
        // Transient focus, like an alarm clock: music pauses and resumes afterwards.
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
            .also { audio.requestAudioFocus(it) }
        this.audio = audio
        this.onStopped = onStopped
        player = mp
        ringingState.value = true
        mp.start()
        handler.postDelayed({ stop() }, AlertPolicy.RING_MS)
    }

    /** [file] = the user's recording; null = the chime. */
    private fun load(context: Context, file: File?): MediaPlayer? {
        val mp = MediaPlayer()
        return runCatching {
            mp.setAudioAttributes(attributes(spoken = file != null))
            if (file != null) {
                mp.setDataSource(file.absolutePath)
            } else {
                context.resources.openRawResourceFd(R.raw.adhkaar_chime).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            mp.prepare()
            mp
        }.getOrElse {
            mp.release()
            null
        }
    }

    private fun attributes(spoken: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(if (spoken) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun stop() {
        if (!ringingState.value) return
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ringingState.value = false
        onStopped?.also { onStopped = null }?.invoke()
    }
}
