package org.adhkaar.app.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Serializable
enum class ReminderKind {
    @SerialName("verse") VERSE,
    @SerialName("hadith") HADITH,
    @SerialName("fact") FACT,
}

/**
 * One reminder of the day. The special-day items in assets/reminders.json are the app's own
 * wording; the everyday ones in assets/reminders/ are built by tools/reminders/build.py from the
 * sources in docs/REMINDERS.md and must be shown unchanged. The meaning is English in every
 * language for now; the Arabic and the reference are never translated.
 */
@Serializable
data class DayReminder(
    val id: String,
    val kind: ReminderKind,
    val arabic: String? = null,
    val meaning: String,
    /** Where it is found; the everyday items end with the site to credit ("· HadeethEnc.com"). */
    val reference: String,
    val tags: List<String> = emptyList(),
    /** Shown only on days that carry this event, and preferred on them. */
    val event: EventKind? = null,
)

@Serializable
private data class RemindersFile(val version: Int, val reminders: List<DayReminder>)

@Serializable
private data class LibraryIndex(val count: Int, val chunkSize: Int)

/**
 * All the reminders: the few special-day items, held in memory, and the everyday rotation, which
 * is years long and so read from its asset files one at a time, only when a day from that file
 * is shown. The files are already in the order of the days.
 *
 * It is also the list of every item (special ones first), but walking all of it reads every
 * file: fine for tests and tools, too slow for a screen.
 */
class ReminderLibrary(
    val special: List<DayReminder>,
    /** Days before the everyday rotation starts again. */
    val everydayCount: Int,
    private val chunkSize: Int,
    private val readChunk: (Int) -> List<DayReminder>,
) : AbstractList<DayReminder>() {
    override val size get() = special.size + everydayCount

    override fun get(index: Int): DayReminder =
        if (index < special.size) special[index] else everyday(index - special.size)

    // Today and the calendar mostly ask for nearby days, which share a file.
    @Volatile private var loaded: Pair<Int, List<DayReminder>>? = null

    /** The [position]th day of the everyday rotation. */
    fun everyday(position: Int): DayReminder {
        val chunk = position / chunkSize
        val items = loaded?.takeIf { it.first == chunk }?.second
            ?: readChunk(chunk).also { loaded = chunk to it }
        return items[position % chunkSize]
    }
}

/** Picks the reminder for a date. Pure apart from [all], so the choice is unit tested. */
object DayReminders {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: ReminderLibrary? = null

    /**
     * Reads only the small files (the special days and the rotation's index), so it is cheap
     * enough for the first frame; each everyday file (about 60 KB) is parsed when first needed.
     */
    fun all(context: Context): ReminderLibrary = cache ?: synchronized(this) {
        cache ?: library(
            special = context.assets.open("reminders.json").bufferedReader().use { it.readText() },
            index = context.assets.open("reminders/library.json").bufferedReader().use { it.readText() },
        ) { n -> context.assets.open(chunkPath(n)).bufferedReader().use { it.readText() } }
            .also { cache = it }
    }

    fun chunkPath(n: Int) = "reminders/everyday-%03d.json".format(Locale.ROOT, n)

    fun library(special: String, index: String, chunk: (Int) -> String): ReminderLibrary {
        val library = json.decodeFromString<LibraryIndex>(index)
        return ReminderLibrary(parse(special), library.count, library.chunkSize) { parse(chunk(it)) }
    }

    fun parse(text: String): List<DayReminder> = json.decodeFromString<RemindersFile>(text).reminders

    /**
     * When a day carries several events, the rarer one speaks: a Friday in the last ten nights
     * is about Laylat al-Qadr, and 'Ashura on a Friday is about 'Ashura.
     */
    val precedence = listOf(
        EventKind.ARAFAH, EventKind.EID_AL_ADHA, EventKind.EID_AL_FITR, EventKind.ASHURA, EventKind.TASUA,
        EventKind.LAST_TEN_NIGHTS, EventKind.DHUL_HIJJAH_TEN, EventKind.RAMADAN, EventKind.WHITE_DAYS, EventKind.JUMUAH,
    )

    /**
     * The same date always gives the same reminder. A special day shows one of its own items;
     * any other day takes its day in the everyday rotation, which runs through every item once
     * before any repeats.
     */
    fun forDate(all: ReminderLibrary, date: LocalDate, hijri: HijriDay): DayReminder {
        val day = date.toEpochDay()
        IslamicCalendar.eventsFor(date, hijri).sortedBy { precedence.indexOf(it.kind) }.forEach { event ->
            val pool = all.special.filter { it.event == event.kind }
            // Fridays are a week apart, so counting weeks walks through the Friday items in turn;
            // the other events fall on consecutive days, so counting days does.
            val turn = if (event.kind == EventKind.JUMUAH) Math.floorDiv(day, 7L) else day
            if (pool.isNotEmpty()) return pool[Math.floorMod(turn, pool.size.toLong()).toInt()]
        }
        // Without the everyday library (e.g. its assets failed to load) fall back to a special-day
        // item rather than dividing by zero; an earlier build crashed the calendar exactly so.
        if (all.everydayCount == 0) return all.special[Math.floorMod(day, all.special.size.toLong()).toInt()]
        return all.everyday(Math.floorMod(day, all.everydayCount.toLong()).toInt())
    }
}

/** A Hijri month laid over the Gregorian calendar: its first day, and its length when known. */
data class HijriMonthSpan(val start: LocalDate, val length: Int?)

object HijriMonths {
    /**
     * The month holding [date], whose Hijri date is [hijri]. With [announced] moon-sighting
     * months, the length is only known once the next month's start has been announced; before
     * that it is null (it will be 29 or 30). Without them, the calculated calendar knows it.
     */
    fun span(date: LocalDate, hijri: HijriDay, announced: MoonSighting.Announcements?, offsetDays: Int): HijriMonthSpan {
        val start = date.minusDays(hijri.day - 1L)
        if (announced == null) {
            return HijriMonthSpan(start, HijrahDate.from(date.plusDays(offsetDays.toLong())).lengthOfMonth())
        }
        val next = announced.months
            .mapNotNull { runCatching { LocalDate.parse(it.start) }.getOrNull() }
            .filter { it.isAfter(start) }
            .minOrNull()
        // A gap in the file (a month never entered) would make a "month" of 59 days; treat it as unknown.
        val length = next?.let { ChronoUnit.DAYS.between(start, it).toInt() }?.takeIf { it in 29..30 }
            ?: if (hijri.day == 30) 30 else null
        return HijriMonthSpan(start, length)
    }
}
