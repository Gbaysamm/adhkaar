// Drives the app on a connected phone to collect its Baseline Profile, and to measure cold start.
//   Collect:  ./gradlew :app:generateBaselineProfile
//   Measure:  ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "org.adhkaar.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Collecting a profile needs Android 9 or later (13 or later on a phone that isn't rooted).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

baselineProfile {
    // The phone plugged in over USB, not a Gradle-managed emulator.
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}
