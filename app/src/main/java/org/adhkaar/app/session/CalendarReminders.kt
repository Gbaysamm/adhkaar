package org.adhkaar.app.session

import android.content.Context
import org.adhkaar.app.R
import org.adhkaar.app.data.IslamicCalendar
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.eveBody
import org.adhkaar.app.data.title
import org.adhkaar.app.schedule.AlarmScheduler
import java.time.LocalDate

/** Friday and fasting-day reminders, driven by the (user-adjusted) Hijri date. */
object CalendarReminders {

    /** 20:00 each day: remind about tomorrow's fast or Eid, so there's time for the intention and suhur. */
    fun onEveningCheck(context: Context) {
        AlarmScheduler.scheduleCalendar(context)
        val settings = SettingsStore.get(context).current
        if (!settings.calendarReminders) return
        val tomorrow = LocalDate.now().plusDays(1)
        val event = IslamicCalendar.eveReminder(tomorrow, org.adhkaar.app.data.MoonSighting.hijriFor(context, tomorrow, settings)) ?: return
        Notifications.showCalendar(context, context.getString(R.string.reminder_eve_title, event.title(context)), event.eveBody(context))
    }

    /** Friday morning: Surah al-Kahf and salawat. */
    fun onFriday(context: Context) {
        AlarmScheduler.scheduleCalendar(context)
        if (!SettingsStore.get(context).current.calendarReminders) return
        Notifications.showCalendar(
            context, context.getString(R.string.reminder_friday_title),
            context.getString(R.string.reminder_friday_body),
        )
    }
}
