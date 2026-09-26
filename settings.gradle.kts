pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Collects the Baseline Profile (app and :baselineprofile). 1.3.x supports AGP 8.x.
        id("androidx.baselineprofile") version "1.3.4"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AdhkaarReminder"
include(":app")
include(":baselineprofile")
