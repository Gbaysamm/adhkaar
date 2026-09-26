package org.adhkaar.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile: the code the app runs on its common paths, which Android then
 * compiles ahead of time at install, so those paths are smooth from the first run.
 *
 * Run with the phone connected over USB (Android 13+, or a rooted Android 9+):
 *   ./gradlew :app:generateBaselineProfile
 * The profile is written to app/src/main/generated/baselineProfiles/baseline-prof.txt; commit it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        allowEverything()
        // Cold start to Today, reading and scrolling it.
        pressHome()
        startActivityAndWait()
        finishOnboardingIfShown()
        waitForToday()
        scrollPage()
        // Every tab, Settings included.
        visitAllTabs()
        // A collection session, counting on the orb.
        countInCollection()
    }
}
