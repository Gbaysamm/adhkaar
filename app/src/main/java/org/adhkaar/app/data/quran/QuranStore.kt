package org.adhkaar.app.data.quran

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** How the pages look. AUTO is sepia by day and night after Maghrib. */
enum class PageTheme { AUTO, SEPIA, NIGHT, WHITE }

/** What the daily reading opens on. */
enum class ReadingOrder {
    /** Where you stopped, on through the whole Qur'an. */
    CONTINUE,
    /** Through your chosen surahs or juz, in the order you put them. */
    CHOSEN,
    /** A page at random from your choice, or from the whole Qur'an. */
    RANDOM,
}

data class QuranSettings(
    /** Pages to read each day; at least one. */
    val dailyPages: Int = 1,
    /** How long a page must be open to count as read. */
    val secondsPerPage: Int = 90,
    val theme: PageTheme = PageTheme.AUTO,
    val order: ReadingOrder = ReadingOrder.CONTINUE,
    /** Chosen surahs (1–114) for [ReadingOrder.CHOSEN] and RANDOM, in the order to read them; empty = all. */
    val chosenSurahs: List<Int> = emptyList(),
    /** Daily reminder on, at [reminderMinute] (minutes after midnight). */
    val reminderOn: Boolean = false,
    val reminderMinute: Int = 6 * 60 + 15,
    /** A larger target through Ramadan (pages a day), 0 = off. */
    val ramadanPages: Int = 20,
    /** al-Kahf on Fridays and al-Mulk at night, offered on the Qur'an tab. */
    val sunnahSurahs: Boolean = true,
)

/**
 * Everything the Qur'an keeps on the phone: its settings, where you stopped, the pages read each
 * day, your pass through the whole Qur'an, and saved ayahs. Nothing leaves the phone.
 */
class QuranStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("quran", Context.MODE_PRIVATE)

    private val settingsState = MutableStateFlow(readSettings())
    val settings: StateFlow<QuranSettings> = settingsState.asStateFlow()

    private val lastPageState = MutableStateFlow(prefs.getInt(KEY_LAST_PAGE, 1))
    /** The page you were last on; the reading continues from it. */
    val lastPage: StateFlow<Int> = lastPageState.asStateFlow()

    private val logState = MutableStateFlow(prefs.getStringSet(KEY_LOG, emptySet())!!.toSet())
    /** Pages read, as "2026-09-27|590". */
    val log: StateFlow<Set<String>> = logState.asStateFlow()

    private val passState = MutableStateFlow(readPass())
    /** Pages read in the current pass through the whole Qur'an. */
    val pass: StateFlow<Set<Int>> = passState.asStateFlow()

    private val savedState = MutableStateFlow(prefs.getStringSet(KEY_SAVED, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet())
    /** Saved ayahs, by 0-based index. */
    val saved: StateFlow<Set<Int>> = savedState.asStateFlow()

    fun update(change: (QuranSettings) -> QuranSettings) {
        val next = change(settingsState.value)
        prefs.edit()
            .putInt("daily_pages", next.dailyPages.coerceIn(1, 604))
            .putInt("seconds_per_page", next.secondsPerPage)
            .putString("theme", next.theme.name)
            .putString("order", next.order.name)
            .putString("chosen_surahs", next.chosenSurahs.joinToString(","))
            .putBoolean("reminder_on", next.reminderOn)
            .putInt("reminder_minute", next.reminderMinute)
            .putInt("ramadan_pages", next.ramadanPages)
            .putBoolean("sunnah_surahs", next.sunnahSurahs)
            .apply()
        settingsState.value = next
    }

    fun setLastPage(page: Int) {
        prefs.edit().putInt(KEY_LAST_PAGE, page).apply()
        lastPageState.value = page
    }

    /** A page was open long enough: it counts for today and for the current pass. */
    fun markRead(page: Int, today: LocalDate = LocalDate.now()) {
        val key = "$today|$page"
        if (key !in logState.value) {
            val next = (logState.value + key).sortedBy { it.substringBefore('|') }.takeLast(8000).toSet()
            prefs.edit().putStringSet(KEY_LOG, next).apply()
            logState.value = next
        }
        if (page !in passState.value) {
            val next = passState.value + page
            prefs.edit().putString(KEY_PASS, next.sorted().joinToString(",")).apply()
            passState.value = next
            if (prefs.getString(KEY_PASS_STARTED, null) == null) prefs.edit().putString(KEY_PASS_STARTED, today.toString()).apply()
        }
    }

    fun passStarted(): LocalDate? = prefs.getString(KEY_PASS_STARTED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** How many times the whole Qur'an has been read here. */
    fun completedPasses(): Int = prefs.getInt(KEY_PASSES, 0)

    /** The whole Qur'an has been read: count it and begin a new pass. */
    fun finishPass() {
        prefs.edit().putInt(KEY_PASSES, completedPasses() + 1).remove(KEY_PASS).remove(KEY_PASS_STARTED).apply()
        passState.value = emptySet()
    }

    fun toggleSaved(ayah: Int) {
        val next = savedState.value.let { if (ayah in it) it - ayah else it + ayah }
        prefs.edit().putStringSet(KEY_SAVED, next.map(Int::toString).toSet()).apply()
        savedState.value = next
    }

    private fun readSettings() = QuranSettings(
        dailyPages = prefs.getInt("daily_pages", 1),
        secondsPerPage = prefs.getInt("seconds_per_page", 90),
        theme = runCatching { PageTheme.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(PageTheme.AUTO),
        order = runCatching { ReadingOrder.valueOf(prefs.getString("order", null)!!) }.getOrDefault(ReadingOrder.CONTINUE),
        chosenSurahs = prefs.getString("chosen_surahs", "")!!.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..114 },
        reminderOn = prefs.getBoolean("reminder_on", false),
        reminderMinute = prefs.getInt("reminder_minute", 6 * 60 + 15),
        ramadanPages = prefs.getInt("ramadan_pages", 20),
        sunnahSurahs = prefs.getBoolean("sunnah_surahs", true),
    )

    private fun readPass(): Set<Int> = prefs.getString(KEY_PASS, "")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    companion object {
        private const val KEY_LAST_PAGE = "last_page"
        private const val KEY_LOG = "read_log"
        private const val KEY_PASS = "pass_pages"
        private const val KEY_PASS_STARTED = "pass_started"
        private const val KEY_PASSES = "passes"
        private const val KEY_SAVED = "saved_ayahs"

        @Volatile private var instance: QuranStore? = null
        fun get(context: Context): QuranStore = instance ?: synchronized(this) {
            instance ?: QuranStore(context).also { instance = it }
        }
    }
}

/** The day's reading. Pure, so it can be unit tested. */
object QuranPlan {
    fun pagesReadOn(log: Set<String>, date: LocalDate): Int = log.count { it.startsWith("$date|") }

    /** Today's target: the daily pages, or the Ramadan target while Ramadan lasts. */
    fun target(settings: QuranSettings, ramadan: Boolean): Int =
        if (ramadan && settings.ramadanPages > 0) maxOf(settings.dailyPages, settings.ramadanPages) else settings.dailyPages

    /**
     * The page today's reading opens on. CONTINUE: the page after the last one read (or the last
     * one, if it isn't read yet). CHOSEN: the first unread page of the chosen surahs, in order.
     * RANDOM: a page from the choice, the same one all day.
     */
    fun startPage(
        settings: QuranSettings,
        lastPage: Int,
        pass: Set<Int>,
        today: LocalDate,
        pagesOfSurah: (Int) -> IntRange,
    ): Int {
        val pool = settings.chosenSurahs.flatMap { pagesOfSurah(it).toList() }.distinct()
        return when (settings.order) {
            ReadingOrder.CONTINUE -> if (lastPage in pass) (lastPage % Quran.PAGES) + 1 else lastPage
            ReadingOrder.CHOSEN -> pool.firstOrNull { it !in pass } ?: pool.firstOrNull() ?: lastPage
            ReadingOrder.RANDOM -> {
                val from = pool.ifEmpty { (1..Quran.PAGES).toList() }
                from[Math.floorMod(today.toEpochDay() * 31 + 7, from.size.toLong()).toInt()]
            }
        }
    }

    /**
     * When the current pass through the Qur'an will end at today's pace: the pages left over the
     * average read per day since the pass began, or null before there is a pace.
     */
    fun finishDate(pass: Set<Int>, started: LocalDate?, today: LocalDate): LocalDate? {
        started ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(started, today) + 1
        if (pass.isEmpty() || days <= 0) return null
        val perDay = pass.size.toDouble() / days
        val left = Quran.PAGES - pass.size
        return today.plusDays(kotlin.math.ceil(left / perDay).toLong())
    }
}
