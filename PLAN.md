# Adhkaar Reminder — Android Implementation Plan

## Goal
At the morning and evening adhkaar times, the app opens by itself and (in the
strictest mode) keeps the user from using other apps until they complete the
adhkaar. It must keep working on any Android phone, including the brands
(Xiaomi, Oppo, Vivo, Tecno/Infinix, Huawei, Samsung) that kill background apps.

## Stack
- **Kotlin 2.1 + Jetpack Compose**, native Android, Glance for widgets. All of the core features
  (alarms, launching over the lock screen, overlays, detecting the foreground
  app) are native APIs, so a cross-platform layer would only add risk. The full
  reasoning is in [docs/WHY_KOTLIN.md](docs/WHY_KOTLIN.md).
  The adhkaar content lives in plain JSON assets so an iOS app can reuse them.
- minSdk 26 (Android 8.0), targetSdk 36.
- Everything runs on the phone. No backend, no account, works offline. The one download is the public moon-sighting list.
- Prayer times are the user's own (their masjid's). The app starts with common local
  defaults (Fajr 5:30, Sunrise 6:30, Dhuhr 1:00, Asr 3:55, Maghrib 6:35, Isha 7:50).
  Nothing is scheduled from a calculation; the `adhan` library is used only to show
  calculated times next to the user's for comparison, if they give a location.
- Release builds use R8 and a Baseline Profile (`baselineprofile/`).

## Strictness levels (the user picks one)
| Level | What happens at adhkaar time |
|---|---|
| **Gentle** | One chime and a notification. It never rings again. |
| **Full screen** | Alarm-style: the app opens over the lock screen and turns the screen on, and rings again until the adhkaar are done. The user can still leave. |
| **Lockdown** | Full screen, plus other apps are covered until the adhkaar are finished or their time ends (7:30 in the morning, Isha in the evening). |

Modes apply to the morning and evening adhkaar only. Other collections (after salah,
before sleep, on waking and so on) and salah reminders are notifications only.

## Safety rules (these can't be turned off)
- Phone calls always work. Enforcement pauses during any call (cellular, WhatsApp and similar), detected through the audio mode.
- The default phone and SMS apps and the emergency dialer are never blocked. The lock screen offers an **Emergency call** button.
- **Take a break** instead of skipping: 10, 20, 30 minutes or an hour, offered only when it ends before the adhkaar window closes. Nothing rings or blocks during the break; then the session rings again.
- Lockdown **ends automatically** when the adhkaar time ends (7:30 morning, Isha evening), and never lasts longer than a user-set maximum (default 3 hours).

## Architecture

```
AlarmScheduler ──setAlarmClock──▶ SessionAlarmReceiver
      ▲                                  │ mark session pending
      │                                  ├─ Gentle     → notification
 BootReceiver                            ├─ Full screen → full-screen intent → SessionActivity
 (boot, time/timezone change,            └─ Lockdown   → + EnforcementService (foreground)
  app update, exact-alarm grant)                             │ polls foreground app (UsageStats)
                                                             │ not allowed → full-screen overlay
                                                             │                + relaunch SessionActivity
                                                             └ watchdog alarm restarts it if killed
SessionActivity ── completed ──▶ SessionState.complete() → stop service, schedule next
```

### Modules / packages
- `data/` — content loaders, `Settings`, `SessionState` (the pending session and any break), calendar, insights
- `schedule/` — `SessionTimeCalculator` (pure Kotlin, unit tested), `AlarmScheduler`, receivers
- `enforce/` — `EnforcementService`, `ForegroundAppDetector`, `AllowList` (pure, unit tested), `BlockOverlay`
- `session/` — notifications, alert sound and `AlertPolicy` (ringing, re-ringing, breaks; pure, unit tested)
- `ui/` — Home, Session, Library, Insights, Settings, onboarding (Compose)
- `widget/` — 7 Glance widgets, each in 3 styles
- `oem/` — auto-start and battery settings shortcuts for each phone brand

### Why each mechanism was chosen
- **`setAlarmClock`** is the most reliable exact alarm. Doze and most OEM battery savers leave it alone.
- **Full-screen intent**: the same path alarm clocks use to open over the lock screen. On Android 14+ this is a permission the user may have to grant, which onboarding checks.
- **Display over other apps**: the overlay blocks touches on the app underneath, and holding this permission lets the app open its screen from the background. From Android 15 that also requires one of the app's own overlays to be on screen, which the block overlay is.
- **Usage Access** tells the service which app is in front. It's used instead of an Accessibility Service, which Google Play restricts heavily.
- **Foreground service (`specialUse`)** runs only while a Lockdown session is pending, and stops as soon as the adhkaar is done.
- **Watchdog alarm** every 5 minutes during a Lockdown session restarts the service if the OEM kills it.

## Onboarding / permission checklist
1. Notifications
2. Exact alarms
3. Full-screen notifications (Android 14+)
4. Display over other apps (Lockdown only)
5. Usage access (Lockdown only)
6. Ignore battery optimisation
7. Brand-specific auto-start (Xiaomi, Oppo, Vivo, Huawei, Honor, Samsung, Tecno/Infinix, OnePlus, Asus), with deep links where they exist
8. Your masjid's prayer times (the defaults work until you change them)

The Home screen shows a health check. If any permission the chosen level needs is missing, a red banner explains what will not work.

## Session timing
Congregation is taken as the prayer time + 20 minutes (`schedule/Congregation.kt`).

- **Morning**: opens 10 min after Fajr congregation (6:00 with the defaults), ends at 7:30.
- **Evening**: opens 30 min after Asr congregation (about 4:45), best until Maghrib, closes at Isha.
- **After salah**: a notification after each prayer's congregation; the moment lasts 15 minutes.

## Session UX
- One card per dhikr: Arabic, transliteration, translation, reference, and a large tap counter.
- The session counts as complete only when every counter reaches its target.
- A progress bar, and the screen stays on while the session is open.
- Back and Home are blocked in Lockdown (Home through the overlay).

## Milestones
Status as of 2026-09-26: milestones 1–5 are done and the app runs on real phones, but it
has not had wide device testing yet. Launch steps are in [docs/LAUNCH.md](docs/LAUNCH.md).

1. ✅ Project scaffold, adhkaar content, Session screen with counters
2. ✅ Settings, time calculation, and the alarm, boot and full-screen launch
3. ✅ Lockdown enforcement: service, overlay, detector, watchdog (code complete, untested on a device)
4. ✅ Onboarding checklist, OEM guidance, health banner
5. ✅ Unit tests (time calculation, allow list, skip allowance, prayer times, content) + JVM screenshot tests
6. Next: wider device testing (Samsung, Xiaomi/HyperOS, Tecno, Pixel), audio recitation, and the Play Store launch in [docs/LAUNCH.md](docs/LAUNCH.md)

## Feature roadmap (agreed 2026-09-25)

**Phase 1 (done, untested on device):**
- Reading controls: Arabic size, auto-advance, haptics
- Prayer times on Today
- Pre-reminder (heads-up), and breaks of 10/20/30/60 minutes within the adhkaar time (these replaced skips and snooze)
- Hijri calendar with a ±2-day adjustment for local moon sighting
- Reminders for Friday (al-Kahf, salawat), the white days, Ramadan and its last ten nights, the ten days of Dhul Hijjah, Arafah, Eid, Tasuʿa and Ashura
- Favourites

**Phase 2 (done, untested on device):**
- Collections: after salah, before sleep, on waking, and everyday duas. Each has its own sources, and the session collections have their own completion words.
- Reminders: after each prayer's congregation for the after-salah adhkaar, plus a bedtime reminder
- My duas: write your own, and add them to the morning and/or evening session
- Insights tab: streaks, a month calendar, usual completion times, weekday consistency, and collection counts

**Phase 3 (done, untested on device):**
- Home-screen widgets: 7 widgets (today, next session, day sky, dhikr, Hijri date, streak, collection), each in 3 styles
- Share a dhikr as an image card, from sessions and lists
- Recitation player for bundled recordings in `assets/audio/<id>`. The owner is recording these; the checklist is in [docs/AUDIO.md](docs/AUDIO.md). Evening sessions play only evening-worded recordings.
- Record your own duas in your own voice. Recordings are kept privately on the phone.

The app ID is now `org.adhkaar.app` (permanent once published).

**Phase 4 (interface done, needs native review):**
- Interface languages: English, Arabic, Urdu, Hausa, Yoruba, Igbo. Choose one in Settings → Language (per-app language on Android 13+, and the same on older phones).
- Right-to-left layout for Arabic and Urdu, with the Arabic typeface for headings and the countdown.
- Hausa, Yoruba and Igbo are drafts and must be reviewed by native speakers before release. Arabic and Urdu need a proofread too. See [docs/TRANSLATING.md](docs/TRANSLATING.md).
- Adhkaar meanings and virtues per language go in `assets/i18n/<lang>.json` (template: `docs/i18n-template.json`). None exist yet. They must come from trusted human translations; the app falls back to English until then.

**Hijri dates follow Nigeria's moon sighting (done):** a scheduled GitHub Action reads Nigerian newspapers (and the Tavily search API around each 29th), picks up the Sultan's declarations, and updates `app/src/main/assets/calendar/ng.json` automatically; it opens an issue only when reports disagree. The app downloads that file. See [docs/MOON_SIGHTING.md](docs/MOON_SIGHTING.md).

## Content note
All Arabic text must be proofread against Hisn al-Muslim by a qualified
person before release.
The daily reminders need the scholar review described in [docs/REMINDERS.md](docs/REMINDERS.md).
Every change to religious text follows the content review in [GOVERNANCE.md](GOVERNANCE.md).
