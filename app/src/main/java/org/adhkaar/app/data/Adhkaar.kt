package org.adhkaar.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SessionType(val key: String) {
    MORNING("morning"),
    EVENING("evening");

    companion object {
        fun fromKey(key: String?): SessionType? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class Dhikr(
    val id: String,
    val title: String,
    val sessions: List<String>,
    val count: Int,
    val arabic: String,
    val arabicEvening: String? = null,
    val transliteration: String,
    val transliterationEvening: String? = null,
    val translation: String,
    val translationEvening: String? = null,
    val reference: String,
    val virtue: String? = null,
)

@Serializable
private data class AdhkaarFile(val version: Int, val adhkaar: List<Dhikr>)

/** A dhikr resolved for one session (morning/evening wording already applied). */
data class SessionDhikr(
    val id: String,
    val title: String,
    val count: Int,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val reference: String,
    val virtue: String?,
    /** Recording to play. Differs from [id] where the evening wording differs, so the words always match. */
    val audioId: String = id,
    /** True when a collection reuses a morning/evening entry (with its own count, virtue and source). */
    val reused: Boolean = false,
)

object AdhkaarRepository {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: List<Dhikr>? = null

    fun all(context: Context): List<Dhikr> = cache ?: synchronized(this) {
        cache ?: parse(context.assets.open("adhkaar.json").bufferedReader().use { it.readText() })
            .also { cache = it }
    }

    fun parse(text: String): List<Dhikr> = json.decodeFromString<AdhkaarFile>(text).adhkaar

    /** The session's adhkaar, with titles and meanings in the app's language where translated. */
    fun forSession(context: Context, type: SessionType): List<SessionDhikr> {
        val translation = ContentTranslations.current(context)
        return resolve(all(context), type).map { ContentTranslations.apply(translation, it) }
    }

    fun resolve(all: List<Dhikr>, type: SessionType): List<SessionDhikr> =
        all.filter { type.key in it.sessions }.map { d ->
            val evening = type == SessionType.EVENING
            SessionDhikr(
                id = d.id,
                title = d.title,
                count = d.count,
                arabic = if (evening) d.arabicEvening ?: d.arabic else d.arabic,
                transliteration = if (evening) d.transliterationEvening ?: d.transliteration else d.transliteration,
                translation = if (evening) d.translationEvening ?: d.translation else d.translation,
                reference = d.reference,
                virtue = d.virtue,
                audioId = if (evening && d.arabicEvening != null) "${d.id}_evening" else d.id,
            )
        }
}
