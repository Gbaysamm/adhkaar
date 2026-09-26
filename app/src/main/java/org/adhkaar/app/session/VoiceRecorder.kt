package org.adhkaar.app.session

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * The user's own recordings of their duas, kept privately in the app's files
 * (files/recordings/<dua id>.m4a). A new take is written to "<id>.pending.m4a" and only
 * replaces the saved one when the dua is saved.
 */
object Recordings {
    private fun dir(context: Context) = File(context.filesDir, "recordings").apply { mkdirs() }

    fun saved(context: Context, id: String) = File(dir(context), "$id.m4a")
    fun pending(context: Context, id: String) = File(dir(context), "$id.pending.m4a")

    /** The take to show in the editor: a new one if recorded, else the saved one. */
    fun current(context: Context, id: String): File? =
        pending(context, id).takeIf { it.exists() } ?: saved(context, id).takeIf { it.exists() }

    fun commit(context: Context, id: String) {
        val p = pending(context, id)
        if (p.exists()) {
            saved(context, id).delete()
            p.renameTo(saved(context, id))
        }
    }

    fun discardPending(context: Context, id: String) {
        pending(context, id).delete()
    }

    fun delete(context: Context, id: String) {
        pending(context, id).delete()
        saved(context, id).delete()
    }
}

/** Records one take at a time: AAC in MP4, mono, speech quality. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    val isRecording get() = recorder != null

    fun start(output: File): Boolean {
        stop()
        output.delete()
        return runCatching {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            true
        }.getOrElse {
            recorder?.release()
            recorder = null
            output.delete()
            false
        }
    }

    /** Stops and finalises the file. A take shorter than the encoder's minimum is discarded. */
    fun stop() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
    }
}
