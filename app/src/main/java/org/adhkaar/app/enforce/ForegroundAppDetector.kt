package org.adhkaar.app.enforce

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Finds the app in front using Usage Access (instead of an Accessibility Service, which Play restricts). */
class ForegroundAppDetector(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private var lastForeground: String? = null
    private var lastQueryAt = System.currentTimeMillis() - 60 * 60_000L

    fun current(): String? {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(lastQueryAt - 2_000L, now) ?: return lastForeground
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED (API 29) has the same value as the older MOVE_TO_FOREGROUND.
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) lastForeground = event.packageName
        }
        lastQueryAt = now
        return lastForeground
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
