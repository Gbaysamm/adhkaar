package org.adhkaar.app.data.quran

import android.content.Context

/**
 * The English meaning of every ayah with its footnotes: QuranEnc's Rowwad Translation Center
 * edition, republished unmodified (built by tools/quran/build.py into assets/quran/en.txt).
 */
class Meanings private constructor(val credit: String, private val lines: List<String>) {
    data class Meaning(val text: String, val footnotes: List<String>)

    fun of(ayah: Int): Meaning {
        val line = lines.getOrNull(ayah).orEmpty()
        val notes = line.substringAfter('\t', "").split("\\n").map { it.trim() }.filter { it.isNotEmpty() }
        return Meaning(line.substringBefore('\t'), notes)
    }

    companion object {
        @Volatile private var cached: Meanings? = null

        fun get(context: Context): Meanings = cached ?: synchronized(this) {
            cached ?: context.applicationContext.assets.open("quran/en.txt").bufferedReader().useLines { all ->
                val list = all.toList()
                Meanings(list.first().removePrefix("# "), list.drop(1))
            }.also { cached = it }
        }
    }
}
