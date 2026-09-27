package org.adhkaar.app.setup

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import org.adhkaar.app.R
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.enforce.ForegroundAppDetector
import org.adhkaar.app.schedule.AlarmScheduler

enum class Requirement(
    @StringRes val title: Int,
    @StringRes val why: Int,
    /** Levels that don't work properly without it. */
    val requiredFor: Set<Strictness>,
    /** Levels that work without it but work better with it. */
    val recommendedFor: Set<Strictness> = emptySet(),
) {
    NOTIFICATIONS(
        R.string.req_notifications,
        R.string.req_notifications_why,
        Strictness.entries.toSet(),
    ),
    EXACT_ALARMS(
        R.string.req_exact_alarms,
        R.string.req_exact_alarms_why,
        Strictness.entries.toSet(),
    ),
    FULL_SCREEN(
        R.string.req_full_screen,
        R.string.req_full_screen_why,
        setOf(Strictness.FULL_SCREEN, Strictness.LOCKDOWN),
    ),
    OVERLAY(
        R.string.req_overlay,
        R.string.req_overlay_why,
        setOf(Strictness.LOCKDOWN),
        recommendedFor = setOf(Strictness.FULL_SCREEN),
    ),
    /**
     * Android 13+ keeps some switches (Usage access among them) greyed out for apps installed
     * from a downloaded file rather than a store, until "Allow restricted settings" is chosen in
     * the app's App info. Only shown where that applies.
     */
    RESTRICTED(
        R.string.req_restricted,
        R.string.req_restricted_why,
        setOf(Strictness.LOCKDOWN),
    ),
    USAGE_ACCESS(
        R.string.req_usage_access,
        R.string.req_usage_access_why,
        setOf(Strictness.LOCKDOWN),
    ),
    BATTERY(
        R.string.req_battery,
        R.string.req_battery_why,
        // Without it, many phones stop the app in the background and sessions don't open on time.
        Strictness.entries.toSet(),
    ),
    ;

    /** Whether it applies to this phone at all; the restriction only exists for some installs. */
    fun applies(context: Context): Boolean = this != RESTRICTED || RestrictedSettings.applies(context)

    fun isGranted(context: Context): Boolean = when (this) {
        RESTRICTED -> RestrictedSettings.allowed(context)
        NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        EXACT_ALARMS -> AlarmScheduler.canScheduleExact(context)
        FULL_SCREEN -> Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        OVERLAY -> Settings.canDrawOverlays(context)
        USAGE_ACCESS -> ForegroundAppDetector.hasPermission(context)
        BATTERY -> context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Settings screen to grant it. Notifications on Android 13+ use the runtime prompt instead. */
    @SuppressLint("BatteryLife", "InlinedApi")
    fun settingsIntent(context: Context): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (this) {
            NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else appDetails(pkg)
            FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else appDetails(pkg)
            OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            // The list of all apps: phones differ on whether they open one app's page, so the
            // card's steps say how to find Adhkaar in the list.
            USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            RESTRICTED -> appDetails(pkg)
            BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }
    }

    private fun appDetails(pkg: Uri) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)

    companion object {
        /** Requirements that matter for the chosen level, in the order they should be granted. */
        fun relevantFor(strictness: Strictness, context: Context) =
            entries.filter { (strictness in it.requiredFor || strictness in it.recommendedFor) && it.applies(context) }

        fun missingRequired(context: Context, strictness: Strictness) =
            entries.filter { strictness in it.requiredFor && it.applies(context) && !it.isGranted(context) }

        /** Opens the settings screen, retrying without the package URI (some OEMs reject it). */
        /** Adhkaar's App info screen, where "Allow restricted settings" lives (in its ⋮ menu). */
        fun openAppInfo(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        fun open(context: Context, requirement: Requirement) {
            // Some phones (Samsung among them) open the whole list for these two rather than
            // Adhkaar's own switch, so say what to look for as the list opens.
            val hint = when (requirement) {
                OVERLAY -> R.string.req_overlay_find
                else -> null
            }
            hint?.let { android.widget.Toast.makeText(context, context.getString(it), android.widget.Toast.LENGTH_LONG).show() }
            val intent = requirement.settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: RuntimeException) {
                try {
                    context.startActivity(Intent(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: RuntimeException) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}
