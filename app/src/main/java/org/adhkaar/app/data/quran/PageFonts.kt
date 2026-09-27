package org.adhkaar.app.data.quran

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Complex's page fonts (QCF2001–QCF2604), one per page, each drawing that page's words at their
 * printed widths. They aren't in the app, which stays small: a page's font (about 330 KB) is
 * downloaded the first time the page is opened and kept, and the next pages are fetched ahead.
 * They come unchanged from the project's release, as the Complex's terms allow.
 */
object PageFonts {
    private const val BASE = "https://github.com/Gbaysamm/adhkaar/releases/download/mushaf-v2/"
    private val lock = Mutex()
    private val families = object : LinkedHashMap<Int, FontFamily>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, FontFamily>) = size > 12
    }

    private fun dir(context: Context) = File(context.filesDir, "mushaf/v2").apply { mkdirs() }

    fun file(context: Context, page: Int) = File(dir(context), "QCF2%03d.ttf".format(page))

    fun isReady(context: Context, page: Int) = file(context, page).length() > 10_000

    /** How many pages are on the phone, of 604. */
    fun downloaded(context: Context) = (1..Quran.PAGES).count { isReady(context, it) }

    /** The page's font, downloading it first if needed; null when there's no connection. */
    suspend fun family(context: Context, page: Int): FontFamily? {
        synchronized(families) { families[page] }?.let { return it }
        if (!ensure(context, page)) return null
        val family = FontFamily(Font(file(context, page)))
        synchronized(families) { families[page] = family }
        return family
    }

    /** Downloads the page's font if it isn't here yet. True when it is here. */
    suspend fun ensure(context: Context, page: Int): Boolean = withContext(Dispatchers.IO) {
        if (isReady(context, page)) return@withContext true
        lock.withLock {
            if (isReady(context, page)) return@withLock true
            runCatching {
                val name = "QCF2%03d.ttf".format(page)
                val tmp = File(dir(context), "$name.part")
                val connection = (URL(BASE + name).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.length() > 10_000 && tmp.renameTo(file(context, page))
            }.getOrDefault(false)
        }
    }

    /** Fetches the next [count] pages after [page], so they open at once, even without a connection later. */
    suspend fun prefetch(context: Context, page: Int, count: Int = 6) {
        for (p in page + 1..minOf(Quran.PAGES, page + count)) if (!ensure(context, p)) return
    }
}
