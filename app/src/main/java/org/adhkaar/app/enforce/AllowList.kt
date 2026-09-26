package org.adhkaar.app.enforce

/**
 * Apps that are never blocked during Lockdown: calls, emergency, SMS, system UI and
 * permission dialogs. Pure Kotlin so it can be unit tested.
 */
object AllowList {
    val ALWAYS: Set<String> = setOf(
        "android",
        "com.android.systemui",
        // Calls and emergency
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.dialer",
        "com.android.incallui",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.google.android.apps.safetyhub",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
        "com.sec.android.app.safetyassurance",
        "com.android.contacts",
        "com.samsung.android.contacts",
        // Permission dialogs
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /**
     * [foreground] is null when the detector hasn't seen any app yet; we never block on a guess.
     * [extra] holds the phone's default dialer and SMS apps, which vary by brand.
     */
    fun isAllowed(foreground: String?, ownPackage: String, extra: Set<String>): Boolean =
        foreground == null || foreground == ownPackage || foreground in ALWAYS || foreground in extra
}
