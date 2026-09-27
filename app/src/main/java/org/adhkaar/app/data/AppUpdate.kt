package org.adhkaar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.adhkaar.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells people who installed the APK from the website that a newer one is out, since nothing
 * updates a sideloaded app by itself. The app reads one small public file (site/version.json in
 * the repository) at most twice a day; nothing is sent. Store builds leave adhkaar.updateUrl blank,
 * and the store updates them instead.
 */
object AppUpdate {
    @Serializable
    data class Latest(
        val versionCode: Int,
        val versionName: String,
        /** Where the APK is downloaded from. Only https is followed. */
        val url: String,
        /** One line on what the update brings, in English. */
        val notes: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }
    private const val PREFS = "app_update"
    private const val REFRESH_EVERY_MS = 12 * 60 * 60 * 1000L
    /** "Later" puts the card away for a day, then it asks again. */
    const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    private val state = MutableStateFlow<Latest?>(null)
    private var read = false

    fun parse(text: String): Latest = json.decodeFromString(text)

    /** The update worth showing, if any. Pure, so it can be unit tested. */
    fun toShow(latest: Latest?, installedCode: Int, snoozedCode: Int, snoozedAt: Long, now: Long): Latest? =
        latest?.takeIf {
            it.versionCode > installedCode && it.url.startsWith("https://") &&
                !(snoozedCode == it.versionCode && now - snoozedAt < SNOOZE_MS)
        }

    /** The newest version known, whether or not it is newer than this one; see [toShow]. */
    fun latest(context: Context): StateFlow<Latest?> {
        if (!read) {
            read = true
            state.value = prefs(context).getString("latest", null)?.let { runCatching { parse(it) }.getOrNull() }
        }
        return state
    }

    fun shouldShow(context: Context, latest: Latest?): Latest? {
        val prefs = prefs(context)
        return toShow(latest, BuildConfig.VERSION_CODE, prefs.getInt("snoozed_code", 0), prefs.getLong("snoozed_at", 0L), System.currentTimeMillis())
    }

    fun snooze(context: Context, latest: Latest) {
        prefs(context).edit().putInt("snoozed_code", latest.versionCode).putLong("snoozed_at", System.currentTimeMillis()).apply()
    }

    /** Called off the main thread when the app opens. */
    fun refreshIfDue(context: Context) {
        val url = BuildConfig.UPDATE_URL.ifBlank { return }
        val prefs = prefs(context)
        if (System.currentTimeMillis() - prefs.getLong("checked_at", 0L) < REFRESH_EVERY_MS) return
        prefs.edit().putLong("checked_at", System.currentTimeMillis()).apply()
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val latest = parse(text)
            prefs.edit().putString("latest", text).apply()
            latest(context)
            state.value = latest
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
