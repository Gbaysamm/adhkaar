package org.adhkaar.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to Today, without and with the Baseline Profile, to see what the profile is worth.
 * Runs on the release-like benchmarkRelease build (R8 on, profileable):
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * Compare timeToInitialDisplayMs between the two tests in the results.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Everything interpreted or just-in-time compiled: a first launch with no profile. */
    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    /** The shipped Baseline Profile compiled ahead of time, as after a Play Store install. */
    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            // A fresh install opens on onboarding; get past it once, outside the measurement.
            if (!onboarded) {
                allowEverything()
                startActivityAndWait()
                finishOnboardingIfShown()
                onboarded = true
            }
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForToday()
    }

    private companion object {
        var onboarded = false
    }
}
