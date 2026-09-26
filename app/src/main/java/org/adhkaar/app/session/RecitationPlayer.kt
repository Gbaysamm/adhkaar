package org.adhkaar.app.session

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Recitations live in assets/audio/<dhikr id>.<ext>, one recitation per file (the app repeats
 * nothing: the counter is yours). Missing files simply mean no play button.
 */
object AudioLibrary {
    private val extensions = setOf("m4a", "mp3", "ogg", "opus", "wav")
    @Volatile private var index: Map<String, String>? = null

    /** For screenshot tests, which have no recordings. */
    @Volatile var testOverride: Set<String>? = null

    fun has(context: Context, id: String): Boolean =
        testOverride?.contains(id) ?: (id in index(context) || Recordings.saved(context, id).exists())

    fun path(context: Context, id: String): String? = index(context)[id]

    private fun index(context: Context): Map<String, String> = index ?: synchronized(this) {
        index ?: (runCatching { context.assets.list("audio")?.toList() }.getOrNull() ?: emptyList())
            .mapNotNull { name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in extensions) name.substringBeforeLast('.') to "audio/$name" else null
            }
            .toMap()
            .also { index = it }
    }
}

/** Where playback is, for the mini player. */
data class Playback(val id: String, val isPlaying: Boolean, val positionMs: Int, val durationMs: Int)

/**
 * One recitation at a time, with the controls of the mini player: play/pause, seek, speed and
 * repeat. [playback] ticks while playing so the progress bar can follow.
 */
object RecitationPlayer {
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)

    private var player: MediaPlayer? = null
    private val state = MutableStateFlow<Playback?>(null)
    private val speedState = MutableStateFlow(1f)
    private val repeatState = MutableStateFlow(false)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    private var ticker: kotlinx.coroutines.Job? = null

    val playback: StateFlow<Playback?> = state.asStateFlow()
    val speed: StateFlow<Float> = speedState.asStateFlow()
    val repeat: StateFlow<Boolean> = repeatState.asStateFlow()

    /** The id currently loaded (playing or paused), for simple play/pause buttons. */
    val playing: StateFlow<String?> get() = playingIds
    private val playingIds = MutableStateFlow<String?>(null)

    fun toggle(context: Context, id: String) {
        val p = player
        val current = state.value
        when {
            current?.id == id && p != null && p.isPlaying -> pause()
            current?.id == id && p != null -> resume()
            else -> play(context, id)
        }
    }

    fun play(context: Context, id: String) {
        val own = Recordings.saved(context, id)
        if (own.exists()) return playFile(own, id)
        stop()
        val path = AudioLibrary.path(context, id) ?: return
        start(id) {
            val fd = context.assets.openFd(path)
            setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            fd.close()
        }
    }

    /** Plays a recording file; [key] is what [playback] reports (the editor uses the file path). */
    fun playFile(file: java.io.File, key: String = file.path) {
        stop()
        start(key) { setDataSource(file.absolutePath) }
    }

    /** Plays a sound bundled in res/raw, such as the alert chime previewed in Settings. */
    fun playRaw(context: Context, resId: Int, key: String) {
        stop()
        start(key) {
            context.resources.openRawResourceFd(resId).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
    }

    fun pause() {
        player?.runCatching { pause() }
        publish()
        ticker?.cancel()
    }

    fun resume() {
        player?.runCatching { start() }
        applySpeed()
        publish()
        tick()
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt())
        publish()
    }

    /** Cycles ×0.75 → ×1 → ×1.25 → ×1.5. */
    fun cycleSpeed() {
        speedState.value = speeds[(speeds.indexOf(speedState.value) + 1) % speeds.size]
        applySpeed()
    }

    fun toggleRepeat() {
        repeatState.value = !repeatState.value
        player?.isLooping = repeatState.value
    }

    fun stop() {
        ticker?.cancel()
        player?.runCatching { stop(); release() }
        player = null
        state.value = null
        playingIds.value = null
    }

    private fun start(id: String, source: MediaPlayer.() -> Unit) {
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                source()
                isLooping = repeatState.value
                setOnCompletionListener { finished(id) }
                prepare()
                start()
            }
            applySpeed()
            state.value = Playback(id, true, 0, player?.duration ?: 0)
            playingIds.value = id
            tick()
        }.onFailure { stop() }
    }

    /** At the end, stay loaded at 0 so the bar resets and play starts again from the top. */
    private fun finished(id: String) {
        ticker?.cancel()
        player?.runCatching { seekTo(0) }
        state.value = state.value?.copy(isPlaying = false, positionMs = 0)
        if (state.value?.id == id) playingIds.value = null
    }

    private fun applySpeed() {
        val p = player ?: return
        runCatching {
            val wasPlaying = p.isPlaying
            p.playbackParams = p.playbackParams.setSpeed(speedState.value)
            if (!wasPlaying) p.pause() // setting params starts playback on some versions
        }
    }

    private fun publish() {
        val p = player ?: return
        val id = state.value?.id ?: return
        val playingNow = runCatching { p.isPlaying }.getOrDefault(false)
        state.value = Playback(id, playingNow, runCatching { p.currentPosition }.getOrDefault(0), runCatching { p.duration }.getOrDefault(0))
        playingIds.value = if (playingNow) id else null
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                publish()
                kotlinx.coroutines.delay(100)
            }
        }
    }
}
