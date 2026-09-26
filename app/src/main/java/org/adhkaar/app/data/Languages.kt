package org.adhkaar.app.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language, independent of the phone's.
 * Android 13+ has per-app languages built in (LocaleManager); older phones, still common, get
 * the same result by wrapping each component's context in [wrap].
 */
object Languages {
    data class Language(val tag: String, val nativeName: String, val englishName: String)

    val all = listOf(
        Language("en", "English", "English"),
        Language("ar", "العربية", "Arabic"),
        Language("ur", "اردو", "Urdu"),
        Language("ha", "Hausa", "Hausa"),
        Language("yo", "Yorùbá", "Yoruba"),
        Language("ig", "Igbo", "Igbo"),
    )

    private const val PREFS = "language"
    private const val KEY = "tag"

    /** The chosen language tag, or null to follow the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /**
     * Sets the language. On Android 13+ the system restarts the screens itself; on older phones
     * the caller should recreate the activity (and notifications follow on the next app start).
     */
    fun set(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
        }
    }

    /** For attachBaseContext on Android 12 and below: applies the chosen language to a component. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale) // java.time weekday and month names follow it too
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
