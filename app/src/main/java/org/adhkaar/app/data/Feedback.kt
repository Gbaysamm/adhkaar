package org.adhkaar.app.data

import android.content.Context
import android.os.Build
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.adhkaar.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/** What a message from the Contact us screen is about. [id] is what the server receives. */
enum class FeedbackTopic(val id: String) {
    PROBLEM("problem"),
    SUGGESTION("suggestion"),
    CORRECTION("correction"),
    OTHER("other"),
}

/**
 * A message from the Contact us screen, sent as JSON to a small web service
 * (tools/report-worker) that files it for the maintainers. Device details carry nothing personal;
 * [contact] is only what the user chose to type.
 */
object Feedback {
    const val MIN_MESSAGE = 10
    const val MAX_MESSAGE = 5000

    fun canSend(message: String) = message.trim().length >= MIN_MESSAGE

    fun json(topic: FeedbackTopic, message: String, contact: String, device: DeviceReport): String = buildJsonObject {
        put("topic", topic.id)
        put("message", message.trim().take(MAX_MESSAGE))
        put("contact", contact.trim())
        put("device", device.device)
        put("android", device.android)
        put("appVersion", device.appVersion)
        put("mode", device.mode)
        put("language", device.language)
    }.toString()

    /** Posts [body] to [url]; true when the server accepted it. Never throws. */
    suspend fun send(url: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}

/** What we need to find a problem on a given phone; nothing personal. */
data class DeviceReport(val device: String, val android: String, val appVersion: String, val mode: String, val language: String) {
    /** Shown on the Contact us screen, so the user sees exactly what is sent. */
    fun lines() = listOf("Phone: $device", "Android: $android", "App: $appVersion", "Mode: $mode", "Language: $language")

    companion object {
        /**
         * "Xiaomi POCO X7 Pro (24117RK2CG)": the name people know their phone by, from the
         * phone's own device name (no permission needed), with the model number that tells us
         * the exact variant.
         */
        fun deviceName(context: Context): String {
            val model = Build.MODEL.trim()
            val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val known = runCatching {
                android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
            }.getOrNull()?.trim().orEmpty()
            return when {
                known.isEmpty() || known.equals(model, ignoreCase = true) -> "$brand $model"
                known.startsWith(brand, ignoreCase = true) -> "$known ($model)"
                else -> "$brand $known ($model)"
            }
        }

        fun of(context: Context, settings: AppSettings) = DeviceReport(
            device = deviceName(context),
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            mode = when (settings.strictness) {
                Strictness.GENTLE -> "Gentle"
                Strictness.FULL_SCREEN -> "Full screen"
                Strictness.LOCKDOWN -> "Lockdown"
            },
            language = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.toLanguageTag() ?: "",
        )
    }
}
