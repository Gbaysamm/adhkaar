package org.adhkaar.app.setup

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Android 13's "restricted settings": for an app installed from a downloaded file, some special
 * permissions stay greyed out until the user allows restricted settings in its App info. Android
 * records that choice as an app op, which an app may read about itself.
 */
object RestrictedSettings {
    private const val OP = "android:access_restricted_settings"

    /** Android 13+ and not installed from a store (a store install is never restricted). */
    fun applies(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        return installer !in STORES
    }

    /** True once allowed, or when the phone can't tell us (so nothing is asked for in vain). */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val ops = context.getSystemService(AppOpsManager::class.java)
        return runCatching {
            ops.unsafeCheckOpNoThrow(OP, Process.myUid(), context.packageName) != AppOpsManager.MODE_ERRORED
        }.getOrDefault(true)
    }

    private val STORES = setOf("com.android.vending", "com.google.android.feedback", "com.sec.android.app.samsungapps", "com.xiaomi.market")
}
