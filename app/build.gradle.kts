plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Baseline Profile: ./gradlew :app:generateBaselineProfile with a phone connected (see baselineprofile/).
    id("androidx.baselineprofile")
}

android {
    namespace = "org.adhkaar.app"
    compileSdk = 36

    defaultConfig {
        // Where the app downloads the moon-sighting list; blank uses only the copy shipped in the app.
        buildConfigField("String", "MOON_SIGHTING_URL", "\"${project.findProperty("adhkaar.moonSightingUrl") ?: ""}\"")
        // Where "Contact us" posts messages (tools/report-worker); blank falls back to email.
        buildConfigField("String", "REPORT_URL", "\"${project.findProperty("adhkaar.reportUrl") ?: ""}\"")
        // The project's repository (links in the app), and an optional support email (blank hides it).
        buildConfigField("String", "REPO_URL", "\"${project.findProperty("adhkaar.repoUrl") ?: "https://github.com/adhkaar-app/adhkaar"}\"")
        // Where a sideloaded build learns a newer one is out (site/version.json); blank for store builds.
        buildConfigField("String", "UPDATE_URL", "\"${project.findProperty("adhkaar.updateUrl") ?: ""}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${project.findProperty("adhkaar.contactEmail") ?: ""}\"")
        applicationId = "org.adhkaar.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Also drops the resources that only the code R8 removed was using.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                // ./gradlew testDebugUnitTest -PnoScreens runs only the logic tests, in seconds.
                if (project.hasProperty("noScreens")) it.exclude("**/ui/**")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

baselineProfile {
    // One profile for the whole app, in src/main/generated/baselineProfiles, committed with the code.
    mergeIntoMain = true
    // Collected only when asked for, not on every release build.
    automaticGenerationDuringBuild = false
}

androidComponents {
    // The Baseline Profile plugin adds copies of release (nonMinifiedRelease to collect the profile,
    // benchmarkRelease to measure it) that run on the phone. Release has no signing key in this
    // build, so these two are signed with the debug key to be installable.
    finalizeDsl { extension ->
        listOf("nonMinifiedRelease", "benchmarkRelease").forEach { name ->
            extension.buildTypes.findByName(name)?.signingConfig = extension.signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Page turns in the mushaf: a real page curl that follows the finger (Apache-2.0).
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")
    // Backdrop blur for glass surfaces that content scrolls under (tab bar).
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    // Home-screen widget.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    // Applies the shipped Baseline Profile on install and after updates, so Android compiles the
    // startup and scrolling code ahead of time instead of interpreting it at first.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    // Screenshot tests: render Compose screens on the JVM (no emulator needed).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Installs the debug app on the connected phone and opens it. With --continuous, it does this
// again every time a source file changes:  ./gradlew :app:runOnPhone --continuous
tasks.register<Exec>("runOnPhone") {
    group = "adhkaar"
    description = "Installs the debug app on the connected phone and opens it."
    dependsOn("installDebug")
    val adb = android.sdkDirectory.resolve("platform-tools/adb")
    commandLine(adb.absolutePath, "shell", "am", "start", "-S", "-n", "org.adhkaar.app/.ui.MainActivity")
}
