package org.adhkaar.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.adhkaar.app.R

enum class Strictness { GENTLE, FULL_SCREEN, LOCKDOWN }

enum class TimeMode { PRAYER, FIXED }

/** What rings when a session starts: the app's chime, or the user's own recorded alert. */
enum class AlertSound { CHIME, RECORDING }

enum class CalcMethod(@StringRes val label: Int) {
    MUSLIM_WORLD_LEAGUE(R.string.calc_muslim_world_league),
    EGYPTIAN(R.string.calc_egyptian),
    KARACHI(R.string.calc_karachi),
    UMM_AL_QURA(R.string.calc_umm_al_qura),
    DUBAI(R.string.calc_dubai),
    QATAR(R.string.calc_qatar),
    KUWAIT(R.string.calc_kuwait),
    MOON_SIGHTING_COMMITTEE(R.string.calc_moonsighting_committee),
    SINGAPORE(R.string.calc_singapore),
    NORTH_AMERICA(R.string.calc_north_america),
}

/**
 * The five prayers and sunrise, in the order of the day. [id] is the name used in
 * [org.adhkaar.app.schedule.PrayerTime]; [label] is the name shown.
 *
 * [defaultMinute] (minutes after midnight) is the time the app uses until the user sets their own.
 * These are times to get ready for salah in Ibadan, Lagos and Ile-Ife, a little before most masjids
 * there pray, not the moment each time begins: calculated times plus margins gave odd times like
 * 3:47 or 4:12 that matched no masjid. Sunrise closes the best time for the morning adhkaar.
 */
enum class Prayer(val id: String, @StringRes val label: Int, val defaultMinute: Int) {
    FAJR("Fajr", R.string.prayer_fajr, 5 * 60 + 30),
    SUNRISE("Sunrise", R.string.prayer_sunrise, 6 * 60 + 30),
    DHUHR("Dhuhr", R.string.prayer_dhuhr, 13 * 60),
    ASR("Asr", R.string.prayer_asr, 15 * 60 + 55),
    MAGHRIB("Maghrib", R.string.prayer_maghrib, 18 * 60 + 35),
    ISHA("Isha", R.string.prayer_isha, 19 * 60 + 50),
    ;

    companion object {
        /** The five prayers; sunrise isn't one, so it has no reminder and no congregation. */
        val five = Prayer.entries - SUNRISE
    }
}

data class SessionSchedule(
    val enabled: Boolean,
    val mode: TimeMode,
    /** Minutes after midnight, used in FIXED mode or when no location is set. */
    val fixedMinuteOfDay: Int,
    /** Minutes after the prayer (Fajr for morning, Asr for evening) in PRAYER mode. */
    val offsetMinutes: Int,
)

data class AppSettings(
    val morning: SessionSchedule,
    val evening: SessionSchedule,
    val strictness: Strictness,
    val latitude: Double?,
    val longitude: Double?,
    val calcMethod: CalcMethod,
    val hanafiAsr: Boolean,
    val lockdownMaxMinutes: Int,
    val onboarded: Boolean,
    val showTransliteration: Boolean,
    val showTranslation: Boolean,
    /** The user confirmed they allowed auto-start in their phone brand's settings (we can't check it). */
    val oemAutostartDone: Boolean,
    /** Multiplier for Arabic text size in sessions (0.85–1.45). */
    val arabicScale: Float = 1f,
    /** Move to the next dhikr automatically when a count is finished. */
    val autoAdvance: Boolean = true,
    val haptics: Boolean = true,
    /** Minutes before a session to send a gentle heads-up; 0 = off. */
    val preReminderMinutes: Int = 0,
    /** Use the dates announced in Nigeria (MoonSighting) instead of the calculated ones. */
    val followMoonSighting: Boolean = true,
    /** Days added to the calculated Hijri date to match local moon sighting (−2…+2). */
    val hijriOffset: Int = 0,
    /** Friday, white days, Ramadan, Dhul Hijjah, Arafah and Ashura reminders. */
    val calendarReminders: Boolean = true,
    /** Minutes after midnight for the Friday (al-Kahf) reminder. */
    val fridayReminderMinute: Int = 10 * 60,
    /** Nudge for the after-salah adhkaar a little after each of the five congregations (see Congregation). */
    val afterSalahReminder: Boolean = true,
    /** Minutes after midnight for the before-sleep reminder; -1 = off. */
    val bedtimeMinute: Int = SettingsStore.DEFAULT_BEDTIME,
    /**
     * How firmly each collection's reminder holds you, by collection id. Gentle (a notification and
     * a card) unless chosen; after salah can go up to Full screen, before sleep up to Lockdown.
     */
    val collectionModes: Map<String, Strictness> = emptyMap(),
    /** Play the recitation (when one exists) as each dhikr comes up. */
    val autoPlayRecitation: Boolean = false,
    val alertSound: AlertSound = AlertSound.CHIME,
    /**
     * Times the user set (usually their masjid's), in minutes after midnight. Each replaces that
     * prayer's [Prayer.defaultMinute] entirely; a prayer not in the map uses its default.
     */
    val prayerMinutes: Map<Prayer, Int> = emptyMap(),
    /** Prayers among [Prayer.five] whose reminder is turned off; the rest ring at their time. */
    val salahRemindersOff: Set<Prayer> = emptySet(),
) {
    fun schedule(type: SessionType) = if (type == SessionType.MORNING) morning else evening

    fun collectionMode(id: String): Strictness = collectionModes[id] ?: Strictness.GENTLE
    val hasLocation get() = latitude != null && longitude != null
    /** The one time the whole app uses for [prayer], in minutes after midnight. */
    fun prayerMinute(prayer: Prayer) = prayerMinutes[prayer] ?: prayer.defaultMinute
    fun salahReminderOn(prayer: Prayer) = prayer in Prayer.five && prayer !in salahRemindersOff
    /** The prayers whose reminder is on, each with the minute it rings. */
    val salahReminderMinutes: Map<Prayer, Int> get() = Prayer.five.filter { salahReminderOn(it) }.associateWith { prayerMinute(it) }
    /** The user set at least one time of their own. */
    val prayerTimesSet get() = prayerMinutes.isNotEmpty()
}

/**
 * Settings live in SharedPreferences so broadcast receivers and the enforcement
 * service can read them synchronously; [flow] feeds the Compose UI.
 */
class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current)
        write(next)
        state.value = next
    }

    private fun read() = AppSettings(
        morning = readSchedule("morning", defaultFixed = 6 * 60, defaultOffset = MORNING_OFFSET),
        evening = readSchedule("evening", defaultFixed = 17 * 60, defaultOffset = EVENING_OFFSET),
        strictness = enumOr(prefs.getString("strictness", null), Strictness.LOCKDOWN),
        latitude = if (prefs.contains("lat")) prefs.getFloat("lat", 0f).toDouble() else null,
        longitude = if (prefs.contains("lng")) prefs.getFloat("lng", 0f).toDouble() else null,
        calcMethod = enumOr(prefs.getString("calc_method", null), CalcMethod.MUSLIM_WORLD_LEAGUE),
        hanafiAsr = prefs.getBoolean("hanafi_asr", false),
        // No longer a setting: the lock lifts when the adhkaar time ends. This only guards against
        // a lock that never lifts, so it sits past the longest window (Asr to Isha).
        lockdownMaxMinutes = LOCKDOWN_CAP_MINUTES,
        onboarded = prefs.getBoolean("onboarded", false),
        showTransliteration = prefs.getBoolean("show_translit", true),
        showTranslation = prefs.getBoolean("show_translation", true),
        oemAutostartDone = prefs.getBoolean("oem_autostart_done", false),
        arabicScale = prefs.getFloat("arabic_scale", 1f),
        autoAdvance = prefs.getBoolean("auto_advance", true),
        haptics = prefs.getBoolean("haptics", true),
        preReminderMinutes = prefs.getInt("pre_reminder", 0),
        followMoonSighting = prefs.getBoolean("follow_moon_sighting", true),
        hijriOffset = prefs.getInt("hijri_offset", 0),
        calendarReminders = prefs.getBoolean("calendar_reminders", true),
        fridayReminderMinute = prefs.getInt("friday_minute", 10 * 60),
        afterSalahReminder = !prefs.getBoolean(REMINDERS_ON, false) || prefs.getBoolean("after_salah_reminder", true),
        bedtimeMinute = prefs.getInt("bedtime_minute", -1).let { if (it < 0 && !prefs.getBoolean(REMINDERS_ON, false)) DEFAULT_BEDTIME else it },
        collectionModes = ENFORCEABLE.associateWith { enumOr(prefs.getString("collection_mode_$it", null), Strictness.GENTLE) },
        autoPlayRecitation = prefs.getBoolean("auto_play", false),
        alertSound = enumOr(prefs.getString("alert_sound", null), AlertSound.CHIME),
        prayerMinutes = Prayer.entries.mapNotNull { prayer -> readPrayerMinute(prayer)?.let { prayer to it } }.toMap(),
        salahRemindersOff = Prayer.five.filterNot { prefs.getBoolean("${salahPrefix(it)}_enabled", true) }.toSet(),
    )

    /**
     * The user's own time for [prayer], or null for the default. Before one time per prayer, a
     * prayer was calculated with a margin ("_mode" AUTO, "_adjust") or fixed ("_mode" FIXED,
     * "_fixed"); a fixed time was the user's masjid, so it carries over, and the margins are dropped.
     * The old keys are removed on the next write.
     */
    private fun readPrayerMinute(prayer: Prayer): Int? {
        val key = prayerKey(prayer)
        if (prefs.contains(key)) return prefs.getInt(key, prayer.defaultMinute)
        val old = "prayer_${prayer.name.lowercase()}"
        return if (prefs.getString("${old}_mode", null) == "FIXED" && prefs.contains("${old}_fixed")) prefs.getInt("${old}_fixed", 0) else null
    }

    private fun readSchedule(prefix: String, defaultFixed: Int, defaultOffset: Int) = SessionSchedule(
        enabled = prefs.getBoolean("${prefix}_enabled", true),
        mode = enumOr(prefs.getString("${prefix}_mode", null), TimeMode.PRAYER),
        fixedMinuteOfDay = prefs.getInt("${prefix}_fixed", defaultFixed),
        offsetMinutes = prefs.getInt("${prefix}_offset", defaultOffset).let { saved ->
            // The defaults moved from 15 minutes after the prayer to 10 (morning, about 6:00) and 30
            // (evening, about 4:45); a session still on the old default follows the new one.
            when {
                prefs.getBoolean(OFFSETS_V2, false) || saved != 15 -> saved
                prefix == "evening" -> EVENING_OFFSET
                else -> MORNING_OFFSET
            }
        },
    )

    private fun write(s: AppSettings) {
        prefs.edit().apply {
            writeSchedule(this, "morning", s.morning)
            writeSchedule(this, "evening", s.evening)
            putBoolean(OFFSETS_V2, true)
            putBoolean(REMINDERS_ON, true)
            putString("strictness", s.strictness.name)
            if (s.latitude != null && s.longitude != null) {
                putFloat("lat", s.latitude.toFloat())
                putFloat("lng", s.longitude.toFloat())
            } else {
                remove("lat"); remove("lng")
            }
            putString("calc_method", s.calcMethod.name)
            putBoolean("hanafi_asr", s.hanafiAsr)
            putInt("lockdown_max_minutes", s.lockdownMaxMinutes)
            putBoolean("onboarded", s.onboarded)
            putBoolean("show_translit", s.showTransliteration)
            putBoolean("show_translation", s.showTranslation)
            putBoolean("oem_autostart_done", s.oemAutostartDone)
            putFloat("arabic_scale", s.arabicScale)
            putBoolean("auto_advance", s.autoAdvance)
            putBoolean("haptics", s.haptics)
            putInt("pre_reminder", s.preReminderMinutes)
            putBoolean("follow_moon_sighting", s.followMoonSighting)
            putInt("hijri_offset", s.hijriOffset)
            putBoolean("calendar_reminders", s.calendarReminders)
            putInt("friday_minute", s.fridayReminderMinute)
            putBoolean("after_salah_reminder", s.afterSalahReminder)
            putInt("bedtime_minute", s.bedtimeMinute)
            s.collectionModes.forEach { (id, mode) -> putString("collection_mode_$id", mode.name) }
            putBoolean("auto_play", s.autoPlayRecitation)
            putString("alert_sound", s.alertSound.name)
            Prayer.entries.forEach { prayer ->
                val key = prayerKey(prayer)
                val minute = s.prayerMinutes[prayer]
                if (minute != null) putInt(key, minute) else remove(key)
                // The keys of the old calculated/fixed model, read once by readPrayerMinute.
                val old = "prayer_${prayer.name.lowercase()}"
                remove("${old}_mode"); remove("${old}_adjust"); remove("${old}_fixed")
            }
            Prayer.five.forEach { prayer ->
                putBoolean("${salahPrefix(prayer)}_enabled", s.salahReminderOn(prayer))
                // The old separate reminder time: a reminder now rings at the prayer's time.
                remove("${salahPrefix(prayer)}_minute")
            }
        }.apply()
    }

    private fun salahPrefix(prayer: Prayer) = "salah_reminder_${prayer.name.lowercase()}"

    private fun prayerKey(prayer: Prayer) = "prayer_time_${prayer.name.lowercase()}"

    private fun writeSchedule(e: SharedPreferences.Editor, prefix: String, s: SessionSchedule) {
        e.putBoolean("${prefix}_enabled", s.enabled)
        e.putString("${prefix}_mode", s.mode.name)
        e.putInt("${prefix}_fixed", s.fixedMinuteOfDay)
        e.putInt("${prefix}_offset", s.offsetMinutes)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val LOCKDOWN_CAP_MINUTES = 300
        private const val MORNING_OFFSET = 10
        private const val EVENING_OFFSET = 30
        /** Set once settings are saved after the defaults moved; see readSchedule. */
        private const val OFFSETS_V2 = "offsets_v2"
        /** Set once settings are saved after every reminder became on by default; see read. */
        private const val REMINDERS_ON = "reminders_on_v1"
        /** Collections whose reminder can be made firmer than a notification. */
        val ENFORCEABLE = listOf("after_salah", "before_sleep")

        /** The before-sleep reminder's default, 10:00 PM. */
        const val DEFAULT_BEDTIME = 22 * 60

        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context).also { instance = it }
        }
    }
}
