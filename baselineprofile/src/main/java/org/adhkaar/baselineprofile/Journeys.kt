package org.adhkaar.baselineprofile

import android.content.res.Resources
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test (it has no application id suffix, so every build type shares it). */
const val PACKAGE = "org.adhkaar.app"

/** A collection opened straight from its launch intent, the way a reminder notification opens it. */
private const val COLLECTION = "after_salah"
private const val EXTRA_OPEN_COLLECTION = "open_collection"

private const val TIMEOUT = 10_000L

/**
 * The app's own resources, so the journeys find buttons by the same words the phone shows, in
 * whatever language it is set to. Run with the app following the phone's language (the default).
 */
private val appResources: Resources by lazy {
    InstrumentationRegistry.getInstrumentation().context.packageManager.getResourcesForApplication(PACKAGE)
}

private fun appString(name: String): String {
    val id = appResources.getIdentifier(name, "string", PACKAGE)
    check(id != 0) { "No string resource \"$name\" in $PACKAGE" }
    return appResources.getString(id)
}

/**
 * Grants, from the shell, everything the default mode (Lockdown) asks for, so onboarding can be
 * finished without visiting system settings. Android versions that don't know a permission or an
 * app-op ignore that line. The last three are Xiaomi's own switches (autostart, pop-ups from the
 * background, lock screen), which the app checks on those phones.
 */
fun MacrobenchmarkScope.allowEverything() {
    listOf(
        "pm grant $PACKAGE android.permission.POST_NOTIFICATIONS",
        "appops set $PACKAGE SCHEDULE_EXACT_ALARM allow",
        "appops set $PACKAGE USE_FULL_SCREEN_INTENT allow",
        "appops set $PACKAGE SYSTEM_ALERT_WINDOW allow",
        "appops set $PACKAGE GET_USAGE_STATS allow",
        "dumpsys deviceidle whitelist +$PACKAGE",
        "appops set $PACKAGE 10008 allow",
        "appops set $PACKAGE 10020 allow",
        "appops set $PACKAGE 10021 allow",
    ).forEach { device.executeShellCommand(it) }
}

/**
 * A fresh install opens on onboarding. Walks through it once (welcome, mode, times, permissions),
 * confirming the phone-brand step if the brand has one. Returns straight away if the app is past it.
 */
fun MacrobenchmarkScope.finishOnboardingIfShown() {
    val getStarted = device.wait(Until.findObject(By.text(appString("onboarding_get_started"))), 3_000) ?: return
    getStarted.click()
    settle()
    tapText(appString("onboarding_continue"))
    tapText(appString("onboarding_continue"))
    val finish = appString("onboarding_finish")
    if (device.wait(Until.findObject(By.text(finish)), TIMEOUT)?.isEnabled != true) {
        // Brands that stop background apps get a step the user confirms by hand.
        val confirm = By.text(appString("setup_oem_done"))
        device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }
            ?.scrollUntil(Direction.DOWN, Until.findObject(confirm))
        device.wait(Until.findObject(confirm), TIMEOUT)?.click()
        settle()
    }
    tapText(finish)
    check(device.wait(Until.hasObject(By.desc(appString("tab_today"))), TIMEOUT)) {
        "Onboarding could not be finished automatically. Finish it by hand on the phone, then run again " +
            "with -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true so the app keeps its data."
    }
}

/** Waits for the Today tab after a cold start. */
fun MacrobenchmarkScope.waitForToday() {
    device.wait(Until.hasObject(By.desc(appString("tab_today"))), TIMEOUT)
}

/** Flings the current page down and back up, as a reader would. */
fun MacrobenchmarkScope.scrollPage() {
    // The tallest scrollable on screen is the page; smaller ones are rows inside it.
    val page = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: return
    // Keeps the gesture clear of the status bar and the tab bar, so it only scrolls the page.
    page.setGestureMargin(device.displayHeight / 5)
    page.fling(Direction.DOWN)
    settle()
    page.fling(Direction.UP)
    settle()
}

/** Opens each tab in turn (Adhkaar, Insights, Settings) and scrolls it, then returns to Today. */
fun MacrobenchmarkScope.visitAllTabs() {
    listOf("tab_adhkaar", "tab_insights", "tab_settings", "tab_today").forEach { tab ->
        device.wait(Until.findObject(By.desc(appString(tab))), TIMEOUT)?.click() ?: error("Tab \"$tab\" not found")
        settle()
        scrollPage()
    }
}

/** Opens a collection session and counts a few times on the orb, waiting out the pacing between counts. */
fun MacrobenchmarkScope.countInCollection() {
    startActivityAndWait { it.putExtra(EXTRA_OPEN_COLLECTION, COLLECTION) }
    // The orb shows "of 3" (in the phone's language) under the count; tapping it counts.
    val (before, after) = appString("counter_of_target").split("%1\$d", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val ofTarget = By.text(Pattern.compile(Pattern.quote(before) + "\\d+" + Pattern.quote(after)))
    repeat(5) {
        // Found again each time: the number is redrawn as it rolls up.
        device.wait(Until.findObject(ofTarget), TIMEOUT)?.click() ?: return
        Thread.sleep(1_200)
    }
}

private fun MacrobenchmarkScope.tapText(text: String) {
    device.wait(Until.findObject(By.text(text)), TIMEOUT)?.click() ?: error("\"$text\" not found on screen")
    settle()
}

/** Lets a page transition or a fling finish before the next step looks at the screen. */
private fun MacrobenchmarkScope.settle() {
    device.waitForIdle()
    Thread.sleep(800)
}
