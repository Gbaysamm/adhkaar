package org.adhkaar.app.data

import android.content.Context
import org.adhkaar.app.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CollectionMode { SESSION, REFERENCE }

@Serializable
data class CompletionText(val headline: String, val quote: String, val reference: String)

@Serializable
private data class RawItem(
    val ref: String? = null,
    val id: String? = null,
    val title: String? = null,
    val count: Int? = null,
    val arabic: String? = null,
    val transliteration: String? = null,
    val translation: String? = null,
    val reference: String? = null,
    val virtue: String? = null,
)

@Serializable
private data class RawCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val mode: String,
    val completion: CompletionText? = null,
    val items: List<RawItem>,
)

@Serializable
private data class CollectionsFile(val version: Int, val collections: List<RawCollection>)

/** A named set of adhkaar beyond morning and evening: after salah, before sleep, on waking, everyday duas. */
data class AdhkaarCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    /** SESSION: said in order with counts. REFERENCE: a list to look up when the moment comes. */
    val mode: CollectionMode,
    val completion: CompletionText?,
    val items: List<SessionDhikr>,
)

object CollectionsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: List<AdhkaarCollection>? = null

    /** All collections, with titles and meanings in the app's language where translated. */
    fun all(context: Context): List<AdhkaarCollection> {
        val translation = ContentTranslations.current(context)
        return base(context).map { ContentTranslations.apply(translation, it).let { c -> named(context, c) } }
    }

    /** The collections' names are app strings, so they follow the app's language like the rest of the UI. */
    private fun named(context: Context, c: AdhkaarCollection): AdhkaarCollection {
        val (title, subtitle) = when (c.id) {
            "after_salah" -> R.string.collection_after_salah to R.string.collection_after_salah_subtitle
            "before_sleep" -> R.string.collection_before_sleep to R.string.collection_before_sleep_subtitle
            "waking" -> R.string.collection_waking to R.string.collection_waking_subtitle
            "daily" -> R.string.collection_daily to R.string.collection_daily_subtitle
            else -> return c
        }
        return c.copy(title = context.getString(title), subtitle = context.getString(subtitle))
    }

    private fun base(context: Context): List<AdhkaarCollection> = cache ?: synchronized(this) {
        cache ?: parse(
            context.assets.open("collections.json").bufferedReader().use { it.readText() },
            AdhkaarRepository.all(context),
        ).also { cache = it }
    }

    fun get(context: Context, id: String): AdhkaarCollection? = all(context).firstOrNull { it.id == id }

    /** Items with "ref" reuse a morning/evening entry, with their own count, virtue and source. */
    fun parse(text: String, base: List<Dhikr>): List<AdhkaarCollection> {
        val shared = (AdhkaarRepository.resolve(base, SessionType.MORNING) + AdhkaarRepository.resolve(base, SessionType.EVENING))
            .distinctBy { it.id }
            .associateBy { it.id }
        return json.decodeFromString<CollectionsFile>(text).collections.map { raw ->
            AdhkaarCollection(
                id = raw.id,
                title = raw.title,
                subtitle = raw.subtitle,
                mode = if (raw.mode == "reference") CollectionMode.REFERENCE else CollectionMode.SESSION,
                completion = raw.completion,
                items = raw.items.map { item ->
                    if (item.ref != null) {
                        val from = shared[item.ref] ?: error("Unknown ref '${item.ref}' in collection ${raw.id}")
                        from.copy(
                            reused = true,
                            count = item.count ?: from.count,
                            virtue = item.virtue ?: from.virtue,
                            reference = item.reference ?: from.reference,
                        )
                    } else {
                        SessionDhikr(
                            id = requireNotNull(item.id) { "Item without id or ref in ${raw.id}" },
                            title = requireNotNull(item.title),
                            count = item.count ?: 1,
                            arabic = requireNotNull(item.arabic),
                            transliteration = requireNotNull(item.transliteration),
                            translation = requireNotNull(item.translation),
                            reference = requireNotNull(item.reference),
                            virtue = item.virtue,
                        )
                    }
                },
            )
        }
    }
}
