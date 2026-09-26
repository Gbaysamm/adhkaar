# Why native Kotlin and Compose

Adhkaar is written in Kotlin with Jetpack Compose, not Flutter or React Native. This page records why, so the question doesn't have to be reopened for every new contributor.

## The short answer

The app's core promise is "it opens on time, on any phone, and (in Lockdown) keeps other apps covered until you're done". Almost every part of that promise is an Android system API with no cross-platform equivalent. A cross-platform framework would put a bridge between the app and the only code that matters, and the UI, which is what those frameworks save time on, is the easy part.

## What the app needs from Android

| Need | Android API | Where in the code |
|---|---|---|
| Fire at an exact time, even in Doze | `AlarmManager.setAlarmClock`, `SCHEDULE_EXACT_ALARM` | `schedule/AlarmScheduler.kt` |
| Open over the lock screen and turn the screen on | Full-screen intent, `USE_FULL_SCREEN_INTENT`, `showWhenLocked` / `turnScreenOn` | `session/Notifications.kt`, `ui/SessionActivity.kt` |
| Know which app is in front (Lockdown) | Usage Access (`UsageStatsManager`) | `enforce/ForegroundAppDetector.kt` |
| Cover other apps (Lockdown) | `SYSTEM_ALERT_WINDOW` overlay | `enforce/BlockOverlay.kt` |
| Stay alive while a Lockdown session is pending | Foreground service, type `specialUse` | `enforce/EnforcementService.kt` |
| Come back after reboot, update, time or time-zone change | Boot and system broadcast receivers | `schedule/Receivers.kt` |
| Survive aggressive battery managers | Deep links to OEM auto-start screens, battery-optimisation exemption | `oem/OemAutostart.kt`, `setup/Requirements.kt` |
| Home-screen widgets | Jetpack Glance (`AppWidget`) | `widget/` |

Each of these works differently across Android versions (exact-alarm permission on 12+, full-screen intent permission on 14+, background activity starts on 15+) and each has to be tested on the phone. In Flutter or React Native, every one would be a hand-written native plugin anyway, plus a bridge, plus the framework's own lifecycle to reason about. The time a background broadcast takes to reach Dart or JavaScript is also not something we want between an alarm and the screen turning on.

## Battery managers (HyperOS and friends)

Xiaomi's HyperOS/MIUI, Oppo/Realme ColorOS, Vivo, Tecno/Infinix (Transsion), Huawei and even Samsung kill background apps in ways that stock Android doesn't. Reliable apps on these phones use `setAlarmClock`, keep services short and declared, restart through a watchdog alarm, and send the user to the right auto-start screen. All of that is platform code. A cross-platform runtime adds startup work and memory, which makes a background process more likely to be killed on exactly these phones, which are also the most common ones where the app's users are.

## Performance and size

- **Startup matters.** The session screen has to be ready the moment an alarm fires. The release build uses R8 and a Baseline Profile (`baselineprofile/`), so startup and scrolling code is compiled ahead of time.
- **Small APK.** No bundled engine or JS runtime. Much of the audience is on low-end phones and paid mobile data.
- **Widgets.** Glance widgets are Compose-style Kotlin. Cross-platform frameworks can't draw home-screen widgets; they'd need a native module for each.

## Testing

Pure-Kotlin logic (`SessionTimeCalculator`, `AlertPolicy`, `AllowList`, `AdhkaarWindows`, the calendar) runs as plain JVM unit tests. Compose screens and widgets are rendered on the JVM with Robolectric and Roborazzi, so UI review needs no emulator.

## The trade-off: iOS

Choosing native Android means an iPhone app is a separate project. Much of this app can't exist on iOS in the same form anyway: iOS has no overlays, no foreground-app detection, and no way to open an app by itself. Blocking apps is possible only through the Screen Time (FamilyControls) API, which needs a special entitlement from Apple. So even a cross-platform codebase would have had a mostly separate iOS app for the parts that matter.

To keep the cost down, the content is plain JSON in `app/src/main/assets/` (adhkaar, collections, daily reminders, calendar), so an iOS app can ship the same files. The plan for iOS is in [LAUNCH.md](LAUNCH.md#ios).
