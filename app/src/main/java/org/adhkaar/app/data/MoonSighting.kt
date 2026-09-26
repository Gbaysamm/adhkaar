package org.adhkaar.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.adhkaar.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Hijri months as announced in Nigeria by the National Moonsighting Committee (NSCIA, under
 * the Sultan of Sokoto). Only the first day of each month is needed; every other date follows.
 *
 * The list is app/src/main/assets/calendar/ng.json, updated by hand after each announcement
 * (docs/MOON_SIGHTING.md). The app ships that copy and downloads the latest from the repository
 * once a day. Nothing is sent; it is a plain download of a public file.
 */
object MoonSighting {
    @Serializable
    data class Month(
        /** "1448-04" for Rabi' al-Thani 1448. */
        val hijri: String,
        /** The Gregorian date of the 1st, "2026-09-13". */
        val start: String,
        /** Where the announcement was made, for anyone checking the file. */
        val source: String = "",
    )

    @Serializable
    data class Announcements(val authority: String = "", val months: List<Month> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    private const val CACHE = "moonsighting-ng.json"
    private const val ASSET = "calendar/ng.json"
    private const val REFRESH_EVERY_MS = 12 * 60 * 60 * 1000L

    @Volatile private var loaded: Announcements? = null

    fun parse(text: String): Announcements = json.decodeFromString(text)

    /** The downloaded list when there is one, else the copy shipped with the app. */
    fun current(context: Context): Announcements =
        loaded ?: run {
            val cache = File(context.filesDir, CACHE)
            val downloaded = runCatching { parse(cache.readText()) }.getOrNull()
            val bundled = runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrNull() ?: Announcements()
            // A fresh install can ship newer months than an old download; use whichever knows more.
            val best = if (downloaded != null && downloaded.months.size >= bundled.months.size) downloaded else bundled
            best.also { loaded = it }
        }

    /**
     * The announced Hijri date for [date], or null when it isn't covered: before the first entry,
     * or more than 30 days after the last one (the next month's announcement isn't in yet).
     */
    fun hijri(file: Announcements, date: LocalDate): HijriDay? {
        val month = file.months
            .mapNotNull { m -> runCatching { LocalDate.parse(m.start) to m.hijri }.getOrNull() }
            .filter { (start, _) -> !start.isAfter(date) }
            .maxByOrNull { (start, _) -> start }
            ?: return null
        val day = ChronoUnit.DAYS.between(month.first, date).toInt() + 1
        if (day > 30) return null
        val (year, number) = month.second.split("-").map { it.toInt() }
        return HijriDay(day, number, year)
    }

    /**
     * The Hijri date shown everywhere: as announced in Nigeria when the user follows the moon
     * sighting and the day is covered, otherwise calculated with the user's manual offset.
     */
    fun hijriFor(context: Context, date: LocalDate, settings: AppSettings): HijriDay =
        (if (settings.followMoonSighting) hijri(current(context), date) else null)
            ?: IslamicCalendar.hijri(date, settings.hijriOffset)

    /** Downloads the latest list if the last try was over 12 hours ago. Call off the main thread. */
    fun refreshIfDue(context: Context) {
        val url = BuildConfig.MOON_SIGHTING_URL.ifBlank { return }
        val cache = File(context.filesDir, CACHE)
        val stamp = File(context.filesDir, "$CACHE.checked")
        if (System.currentTimeMillis() - stamp.lastModified() < REFRESH_EVERY_MS) return
        stamp.writeText("")
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            // Only keep a download that parses and has months in it.
            if (parse(text).months.isNotEmpty()) {
                cache.writeText(text)
                loaded = null
            }
        }
    }
}
