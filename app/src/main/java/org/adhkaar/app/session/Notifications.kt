package org.adhkaar.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.adhkaar.app.R
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.schedule.SessionAlarmReceiver
import org.adhkaar.app.ui.MainActivity
import org.adhkaar.app.ui.SessionActivity
import org.adhkaar.app.ui.components.formatTime
import java.time.Instant
import java.time.ZoneId

object Notifications {
    /**
     * Silent: the session alert rings through [AlertPlayer]. A channel's sound can't be changed
     * once created, hence a new id; [LEGACY_CHANNEL_SESSION] is the old one that played a sound.
     */
    const val CHANNEL_SESSION = "adhkaar_session_quiet"
    const val CHANNEL_GENTLE = "adhkaar_gentle"
    /** Its own channel, so salah reminders can be silenced without the adhkaar nudges. */
    const val CHANNEL_SALAH = "adhkaar_salah_ring"
    /**
     * Reminders the user asked for (after salah, before sleep, the heads-up before the adhkaar):
     * they pop up, chime and vibrate, so they aren't missed in the shade. The calendar's daily
     * notes stay on the quiet [CHANNEL_GENTLE].
     */
    const val CHANNEL_REMINDER = "adhkaar_reminder_ring"
    /** Earlier salah channels; a channel's sound and vibration can't change once created. */
    private val LEGACY_CHANNELS = listOf("adhkaar_session", "adhkaar_salah", "adhkaar_salah_alert")
    const val SESSION_ID = 1001
    const val PRE_ID = 1002
    const val CALENDAR_ID = 1003
    const val COLLECTION_ID = 1004
    const val SALAH_ID = 1005
    const val MISSED_ID = 1006
    const val EXTRA_OPEN_COLLECTION = "open_collection"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_SESSION, context.getString(R.string.channel_session_name), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_session_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(true)
        }
        val gentle = NotificationChannel(
            CHANNEL_GENTLE, context.getString(R.string.channel_gentle_name), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_gentle_description) }
        // High, so it pops up over whatever is open, with the app's chime on the alarm volume and a
        // vibration you feel in a pocket: a quiet line in the shade is easily missed.
        val salah = ringing(CHANNEL_SALAH, context.getString(R.string.channel_salah_name), context.getString(R.string.channel_salah_description), context)
        val reminder = ringing(CHANNEL_REMINDER, context.getString(R.string.channel_reminder_name), context.getString(R.string.channel_reminder_description), context)
        val manager = context.getSystemService(NotificationManager::class.java)
        LEGACY_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannels(listOf(channel, gentle, salah, reminder))
    }

    private val VIBRATION = longArrayOf(0, 500, 250, 500, 250, 900)

    private fun ringing(id: String, name: String, description: String, context: Context) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.adhkaar_chime}"),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = VIBRATION
            enableLights(true)
        }

    fun showPreReminder(context: Context, type: SessionType, minutes: Int) {
        post(context, PRE_ID, NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notif_pre_reminder_title, minutes, title(context, type), minutes))
            .setContentText(context.getString(R.string.notif_pre_reminder_text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 300, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .build())
    }

    fun showCalendar(context: Context, title: String, body: String) {
        post(context, CALENDAR_ID, NotificationCompat.Builder(context, CHANNEL_GENTLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 301, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .build())
    }

    /** A gentle nudge that opens a collection (after salah, before sleep). */
    fun showCollection(context: Context, collectionId: String) {
        val (title, text) = when (collectionId) {
            "after_salah" -> context.getString(R.string.reminder_after_salah_title) to context.getString(R.string.reminder_after_salah_text)
            "before_sleep" -> context.getString(R.string.reminder_before_sleep_title) to context.getString(R.string.reminder_before_sleep_text)
            else -> return
        }
        ReminderPopup.show(context, ReminderPopup.Kind.Collection(collectionId))
        post(context, COLLECTION_ID, NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // With the phone locked, the card itself comes up over the lock screen.
            .setFullScreenIntent(reminderCard(context, 304, ReminderPopup.Kind.Collection(collectionId)), true)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 302,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_OPEN_COLLECTION, collectionId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .build())
    }

    /** "Time to get ready for Dhuhr": a reminder the user set, worded so it can't pass for the adhan. */
    fun showSalahReminder(context: Context, prayer: Prayer) {
        ReminderPopup.show(context, ReminderPopup.Kind.Salah(prayer))
        val text = context.getString(R.string.notif_salah_text)
        post(context, SALAH_ID, NotificationCompat.Builder(context, CHANNEL_SALAH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_salah_title, context.getString(prayer.label)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF6B8CFF.toInt())
            .setColorized(true)
            .setFullScreenIntent(reminderCard(context, 305, ReminderPopup.Kind.Salah(prayer)), true)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 303, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .build())
    }

    /** "Morning adhkaar missed": said once, kindly, when their time ends unread. Opens the app's card. */
    fun showMissed(context: Context, type: SessionType) {
        // A card over whatever is open, or over the lock screen, as well as the notification.
        ReminderPopup.show(context, ReminderPopup.Kind.Missed(type))
        val title = context.getString(if (type == SessionType.MORNING) R.string.missed_notif_morning else R.string.missed_notif_evening)
        val text = context.getString(R.string.missed_notif_text)
        post(context, MISSED_ID, NotificationCompat.Builder(context, CHANNEL_GENTLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setFullScreenIntent(reminderCard(context, 307, ReminderPopup.Kind.Missed(type)), true)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 306, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .build())
    }

    private fun reminderCard(context: Context, requestCode: Int, kind: ReminderPopup.Kind): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, org.adhkaar.app.ui.ReminderActivity.intent(context, kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun title(context: Context, type: SessionType): String {
        collectionTitle(context)?.let { return it }
        return context.getString(if (type == SessionType.MORNING) R.string.notif_title_morning else R.string.notif_title_evening)
    }

    /** The waiting collection's name, when a collection (not morning or evening) is what's waiting. */
    private fun collectionTitle(context: Context): String? = when (SessionState.get(context).pending?.collection) {
        "after_salah" -> context.getString(R.string.moment_title_after_salah)
        "before_sleep" -> context.getString(R.string.moment_title_before_sleep)
        else -> null
    }

    /** On a break: when it ends and when the adhkaar must be done by. Otherwise what the session is. */
    private fun text(context: Context, type: SessionType): String {
        val pending = SessionState.get(context).pending
        if (pending != null && AlertPolicy.isPaused(pending, System.currentTimeMillis())) {
            val windowEnd = AlertPolicy.windowEndMillis(pending, SessionLauncher.windowMinutes(context, pending))
            return context.getString(R.string.break_notification, clock(context, pending.pausedUntilMillis), clock(context, windowEnd))
        }
        if (pending?.collection != null) return context.getString(R.string.notif_collection_text)
        return context.getString(if (type == SessionType.MORNING) R.string.notif_session_text_morning else R.string.notif_session_text_evening)
    }

    private fun clock(context: Context, millis: Long): String =
        formatTime(context, Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** [ringing] adds a Stop button for the alert. */
    fun build(context: Context, type: SessionType, fullScreen: Boolean, ringing: Boolean = AlertPlayer.isRinging): Notification {
        val open = sessionPendingIntent(context, type)
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(context, type))
            .setContentText(text(context, type))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (fullScreen) setFullScreenIntent(open, true) }
            .apply {
                if (ringing) {
                    addAction(0, context.getString(R.string.alert_stop), PendingIntent.getBroadcast(
                        context, 400,
                        Intent(context, SessionAlarmReceiver::class.java).setAction(SessionAlarmReceiver.ACTION_SILENCE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ))
                }
            }
            .build()
    }

    fun show(context: Context, type: SessionType, fullScreen: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(SESSION_ID, build(context, type, fullScreen))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked; the health check on the home screen reports this.
        }
    }

    /** Re-posts the pending session's notification quietly, e.g. to drop the Stop button once the alert ends. */
    fun refresh(context: Context) {
        SessionState.get(context).pending?.let { show(context, it.type, fullScreen = false) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(SESSION_ID)
    }

    fun sessionIntent(context: Context, type: SessionType): Intent =
        Intent(context, SessionActivity::class.java)
            .putExtra(SessionActivity.EXTRA_TYPE, type.key)
            .apply { SessionState.get(context).pending?.collection?.let { putExtra(SessionActivity.EXTRA_COLLECTION, it) } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    private fun sessionPendingIntent(context: Context, type: SessionType): PendingIntent =
        PendingIntent.getActivity(
            context, 200 + type.ordinal, sessionIntent(context, type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
