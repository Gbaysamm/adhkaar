package org.adhkaar.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Translations of the adhkaar' titles, meanings and virtues, one file per language in
 * assets/i18n/<language>.json. Anything missing falls back to English, so a partial
 * translation is safe to ship. The Arabic and the hadith references are never translated.
 *
 * These must come from trusted human translations, never machine translation.
 */
object ContentTranslations {
    @Serializable
    data class Text(val title: String? = null, val translation: String? = null, val virtue: String? = null)

    @Serializable
    data class CollectionText(val title: String? = null, val subtitle: String? = null)

    @Serializable
    data class File(
        val language: String,
        val source: String = "",
        val reviewedBy: String = "",
        val adhkaar: Map<String, Text> = emptyMap(),
        val collections: Map<String, CollectionText> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, File?>()

    fun parse(text: String): File = json.decodeFromString(text)

    /** The translation file for the app's current language, or null for English / none yet. */
    fun current(context: Context): File? {
        val language = context.resources.configuration.locales[0].language
        if (language == "en") return null
        return synchronized(cache) {
            cache.getOrPut(language) {
                runCatching {
                    context.assets.open("i18n/$language.json").bufferedReader().use { parse(it.readText()) }
                }.getOrNull()
            }
        }
    }

    /** Applies [file] to a dhikr, keeping English wherever a field isn't translated yet. */
    fun apply(file: File?, dhikr: SessionDhikr): SessionDhikr {
        val t = file?.adhkaar?.get(dhikr.id) ?: return dhikr
        return dhikr.copy(
            title = t.title ?: dhikr.title,
            translation = t.translation ?: dhikr.translation,
            virtue = t.virtue ?: dhikr.virtue,
        )
    }

    /**
     * Items reused in a collection (e.g. Ayat al-Kursi after salah) carry their own virtue, so they
     * look up "<collection>/<id>" first. The shared entry may supply the title and meaning, but
     * never the virtue, which would be the morning one.
     */
    fun apply(file: File?, collection: AdhkaarCollection): AdhkaarCollection {
        val t = file?.collections?.get(collection.id)
        return collection.copy(
            title = t?.title ?: collection.title,
            subtitle = t?.subtitle ?: collection.subtitle,
            items = collection.items.map { item ->
                val specific = file?.adhkaar?.get("${collection.id}/${item.id}")
                val shared = file?.adhkaar?.get(item.id)
                item.copy(
                    title = specific?.title ?: shared?.title ?: item.title,
                    translation = specific?.translation ?: shared?.translation ?: item.translation,
                    virtue = specific?.virtue ?: (if (item.reused) item.virtue else shared?.virtue ?: item.virtue),
                )
            },
        )
    }
}
