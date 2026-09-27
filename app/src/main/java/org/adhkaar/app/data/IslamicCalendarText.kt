package org.adhkaar.app.data

import android.content.Context
import androidx.annotation.StringRes
import org.adhkaar.app.R

// Android-side text for IslamicCalendar, which stays pure Kotlin so it can be unit tested.

@StringRes
fun EventKind.titleRes(): Int = when (this) {
    EventKind.JUMUAH -> R.string.event_jumuah_title
    EventKind.WHITE_DAYS -> R.string.event_white_days_title
    EventKind.RAMADAN -> R.string.event_ramadan_title
    EventKind.LAST_TEN_NIGHTS -> R.string.event_last_ten_nights_title
    EventKind.DHUL_HIJJAH_TEN -> R.string.event_dhul_hijjah_ten_title
    EventKind.ARAFAH -> R.string.event_arafah_title
    EventKind.EID_AL_FITR -> R.string.event_eid_al_fitr_title
    EventKind.EID_AL_ADHA -> R.string.event_eid_al_adha_title
    EventKind.TASUA -> R.string.event_tasua_title
    EventKind.ASHURA -> R.string.event_ashura_title
}

@StringRes
fun EventKind.bodyRes(): Int = when (this) {
    EventKind.JUMUAH -> R.string.event_jumuah_body
    EventKind.WHITE_DAYS -> R.string.event_white_days_body
    EventKind.RAMADAN -> R.string.event_ramadan_body
    EventKind.LAST_TEN_NIGHTS -> R.string.event_last_ten_nights_body
    EventKind.DHUL_HIJJAH_TEN -> R.string.event_dhul_hijjah_ten_body
    EventKind.ARAFAH -> R.string.event_arafah_body
    EventKind.EID_AL_FITR -> R.string.event_eid_al_fitr_body
    EventKind.EID_AL_ADHA -> R.string.event_eid_al_adha_body
    EventKind.TASUA -> R.string.event_tasua_body
    EventKind.ASHURA -> R.string.event_ashura_body
}

fun DayEvent.title(context: Context): String = context.getString(kind.titleRes())

fun DayEvent.body(context: Context): String = context.getString(kind.bodyRes())

/** The body for the evening before, when "today" would be wrong: the fast is tomorrow. */
fun DayEvent.eveBody(context: Context): String = context.getString(
    when (kind) {
        EventKind.ARAFAH -> R.string.event_arafah_eve_body
        EventKind.TASUA -> R.string.event_tasua_eve_body
        EventKind.ASHURA -> R.string.event_ashura_eve_body
        else -> kind.bodyRes()
    },
)

private val hijriMonths = intArrayOf(
    R.string.hijri_month_muharram, R.string.hijri_month_safar, R.string.hijri_month_rabi_al_awwal,
    R.string.hijri_month_rabi_al_thani, R.string.hijri_month_jumada_al_ula, R.string.hijri_month_jumada_al_akhirah,
    R.string.hijri_month_rajab, R.string.hijri_month_shaban, R.string.hijri_month_ramadan,
    R.string.hijri_month_shawwal, R.string.hijri_month_dhu_al_qadah, R.string.hijri_month_dhu_al_hijjah,
)

/** Hijri month name, [month] is 1-based. */
fun IslamicCalendar.monthName(context: Context, month: Int): String = context.getString(hijriMonths[month - 1])

/** "14 Rabiʿ al-Thani 1448". */
fun IslamicCalendar.label(context: Context, h: HijriDay): String =
    context.getString(R.string.hijri_month_date_format, h.day, monthName(context, h.month), h.year)
