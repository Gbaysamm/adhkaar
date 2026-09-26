package org.adhkaar.app.oem

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.adhkaar.app.R

/**
 * Many brands kill background apps unless the user allows "auto-start" in a brand-specific
 * screen. There's no API to check it, so we deep-link there and explain what to switch on.
 * See https://dontkillmyapp.com for details on each brand.
 */
data class OemGuide(
    val brand: String,
    val slug: String,
    /** String resource ids, one per step. */
    val steps: List<Int>,
    val components: List<ComponentName>,
)

object OemAutostart {
    private val guides = listOf(
        OemGuide(
            "Xiaomi / Redmi / POCO", "xiaomi",
            listOf(
                R.string.oem_xiaomi_step_autostart,
                R.string.oem_xiaomi_step_other_permissions,
                R.string.oem_xiaomi_step_battery,
            ),
            listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ),
        ),
        OemGuide(
            "Oppo / Realme", "oppo",
            listOf(R.string.oem_oppo_step_autolaunch, R.string.oem_oppo_step_battery),
            listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ),
        ),
        OemGuide(
            "Vivo / iQOO", "vivo",
            listOf(R.string.oem_vivo_step_autostart, R.string.oem_vivo_step_battery),
            listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ),
        ),
        OemGuide(
            "Huawei / Honor", "huawei",
            listOf(R.string.oem_huawei_step_app_launch),
            listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ),
        ),
        OemGuide(
            "Samsung", "samsung",
            listOf(R.string.oem_samsung_step_sleeping, R.string.oem_samsung_step_never_sleeping),
            listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ),
        ),
        OemGuide(
            "OnePlus", "oneplus",
            listOf(R.string.oem_oneplus_step_autolaunch, R.string.oem_oneplus_step_battery),
            listOf(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        ),
        OemGuide(
            "Tecno / Infinix / itel", "tecno",
            listOf(R.string.oem_tecno_step_autostart, R.string.oem_tecno_step_power_saving),
            listOf(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
        ),
        OemGuide(
            "Asus", "asus",
            listOf(R.string.oem_asus_step_autostart),
            listOf(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
        ),
    )

    /** The guide for this phone, or null on brands that don't need an extra step (e.g. Pixel). */
    fun guide(): OemGuide? {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> guides[0]
            listOf("oppo", "realme").any { it in m } -> guides[1]
            listOf("vivo", "iqoo").any { it in m } -> guides[2]
            listOf("huawei", "honor").any { it in m } -> guides[3]
            "samsung" in m -> guides[4]
            "oneplus" in m -> guides[5]
            listOf("tecno", "infinix", "itel", "transsion").any { it in m } -> guides[6]
            "asus" in m -> guides[7]
            else -> null
        }
    }

    /** Opens the brand's auto-start screen, falling back to the app's system settings page. */
    fun open(context: Context, guide: OemGuide) {
        for (component in guide.components) {
            try {
                context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: RuntimeException) {
                // Not present on this firmware version; try the next one.
            }
        }
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun helpUrl(guide: OemGuide) = "https://dontkillmyapp.com/${guide.slug}"

    /**
     * Xiaomi, Redmi and POCO (MIUI / HyperOS) keep their own switches as extra app-op codes, which
     * can be read: autostart, starting screens from the background, and showing on the lock
     * screen. Returns null when they can't be read (other brands, or firmware that hides them),
     * and the user confirms by hand instead.
     */
    fun xiaomiBackgroundAllowed(context: Context): Boolean? {
        if (guide()?.slug != "xiaomi") return null
        return try {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            XIAOMI_OPS.all { op -> check.invoke(ops, op, Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED }
        } catch (_: Exception) {
            null
        }
    }

    /** Autostart, background pop-up windows, show on lock screen. */
    private val XIAOMI_OPS = listOf(10008, 10021, 10020)

    /** Whether the brand's background step is done: checked where possible, else as the user confirmed. */
    /**
     * Whether the phone lets Adhkaar run in the background: Xiaomi's switches where they can be
     * read, otherwise whether Android runs it without battery restrictions.
     */
    fun backgroundDone(context: Context): Boolean {
        if (guide() == null) return true
        return xiaomiBackgroundAllowed(context)
            ?: context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
}
