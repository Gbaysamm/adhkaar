package org.adhkaar.app

import android.app.Application
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.session.SessionLauncher

class AdhkaarApp : Application() {
    // Android 12 and below: apply the in-app language to the application context (receivers,
    // notifications, the widget). Android 13+ does this itself.
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(org.adhkaar.app.data.Languages.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Re-arm on every process start: alarms and the enforcement service are lost after an
        // update, a force-stop or an OEM "cleanup", and the process only comes back when the app
        // is opened (or something else starts it).
        AlarmScheduler.scheduleAll(this)
        SessionLauncher.resumePending(this)
    }
}
