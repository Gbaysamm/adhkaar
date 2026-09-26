# Launch plan

How Adhkaar gets from this repository to people's phones: Google Play first, F-Droid and GitHub Releases alongside, then iOS. Building and signing a release is in [RELEASING.md](RELEASING.md).

Policies change. Check each item against the current Play Console help before submitting; the dates and numbers here were right as of September 2026.

## Before anything else

- [x] **Licence chosen:** PolyForm Noncommercial 1.0.0 for the code ([LICENSE](../LICENSE)), CC BY-NC-SA 4.0 for the project's own content ([LICENSE-CONTENT.md](../LICENSE-CONTENT.md)). Third-party content keeps its own terms (Tanzil, QuranEnc, HadeethEnc: no changes allowed, credit required; fonts: OFL). Publishing on Google Play is unaffected: the project itself isn't restricted by its own licence.
- [ ] **Content review done.** Arabic proofread against *Hisn al-Muslim*, daily reminders reviewed ([REMINDERS.md](REMINDERS.md#review)), Hausa, Yoruba and Igbo interface reviewed by native speakers ([TRANSLATING.md](TRANSLATING.md)).
- [ ] **Repository public** under the `adhkaar-app` organisation, and the build properties set (see below).
- [ ] **Device testing** on at least Samsung, Xiaomi (HyperOS), Tecno or Infinix, and a Pixel, in all three modes.

### Build properties

Set in `gradle.properties` (or with `-P` on the command line / in CI):

| Property | Default | What it does |
|---|---|---|
| `adhkaar.repoUrl` | `https://github.com/adhkaar-app/adhkaar` | Links to the project in the app. |
| `adhkaar.reportUrl` | blank | Address of the deployed [report worker](../tools/report-worker/README.md). **Contact us** in the app sends messages there, and the worker files them as GitHub issues. Blank sends users to email instead. |
| `adhkaar.contactEmail` | blank | Optional support email, offered by **Contact us** when a message can't be sent. Blank hides it. Must match the email on the store listing if set. |
| `adhkaar.moonSightingUrl` | blank | Raw URL of `app/src/main/assets/calendar/ng.json` on `main`. Blank uses only the copy shipped in the app. |

## Google Play

### 1. Developer account

- Create the account at <https://play.google.com/console>. There's a one-time US$25 fee and identity verification.
- **Organisation or personal?** An organisation account needs a D-U-N-S number but skips the closed-testing requirement in step 6. A personal account created after November 2023 must run a closed test with at least **12 testers for 14 days in a row** before it can apply for production.
- Add at least two people as admins, so the project doesn't depend on one person's Google account.

### 2. App signing

- Use **Play App Signing**. Google keeps the app signing key; the project keeps an **upload key**.
- Generate the upload key once, store the keystore and passwords in a password manager held by two maintainers, and add them as GitHub secrets for the release workflow ([RELEASING.md](RELEASING.md#signing)).
- If the upload key is lost, Play support can reset it. The app signing key can't be recovered, which is why Google holds it.
- The package name `org.adhkaar.app` is permanent once published.

### 3. Build: AAB and target API

- Play takes an **Android App Bundle**: `./gradlew bundleRelease`.
- Target API: the app targets **36**. New apps and updates must target a recent API level (API 35 has been required since August 2025, and the requirement moves up each year). Keep `targetSdk` current before each August deadline.
- R8 and resource shrinking are on for release. Keep the mapping file (`app/build/outputs/mapping/release/mapping.txt`); Play uploads it with the bundle for readable crash reports.

### 4. Data safety form

The app collects no data. Answer:

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Not applicable (nothing is collected). The one download uses HTTPS.
- **Do you provide a way for users to request that their data be deleted?** Not applicable; all data is on the phone and is removed by uninstalling or clearing app data.

Notes for whoever fills it in:

- Data that stays on the phone doesn't count as "collected". This covers prayer times, progress, your duas, voice recordings and the optional location.
- The moon-sighting file is downloaded from GitHub (`raw.githubusercontent.com`). The app sends nothing with the request. GitHub sees the IP address, as any web server does; that isn't collection by the app.
- `android:allowBackup="true"` lets Android's own backup include the app's data in the user's Google backup. That's the system's backup, not the app sending data, but mention it in the privacy policy.
- A **privacy policy URL** is still required. A short page in the repository (for example `docs/PRIVACY.md` published through GitHub Pages) saying "no data collected, nothing sent, one download from GitHub" is enough.

### 5. Permissions Google reviews closely

These need a declaration in Play Console (**App content** > **Sensitive app permissions**), a clear in-app explanation before the system prompt, and often a short video. Draft text is below; keep it true to what the code does.

**Exact alarms: `SCHEDULE_EXACT_ALARM`** (declared). Don't add `USE_EXACT_ALARM`: it's reserved for apps whose core function is an alarm clock or calendar, is granted without asking, and invites rejection. `SCHEDULE_EXACT_ALARM` is granted by the user in settings, which onboarding already guides.

> Adhkaar rings at the times the user sets for their morning and evening adhkaar, like an alarm clock. The adhkaar have a fixed time (after Fajr until 7:30, and after Asr until Isha), so an inexact alarm that fires late would miss it. The app schedules at most a few alarms a day, all set by the user.

**Full-screen notifications: `USE_FULL_SCREEN_INTENT`.** On Android 14+, Play grants this by default only to alarm and calling apps; others must declare it, and the user may have to allow it.

> When the user has chosen "Full screen" or "Lockdown" for their morning or evening adhkaar, the app opens the session over the lock screen at the time they set, the way an alarm clock does. It's used only for these user-scheduled sessions, never for other notifications. In "Gentle" mode it isn't used.

**Display over other apps: `SYSTEM_ALERT_WINDOW`.**

> Used only in the optional "Lockdown" mode, which the user turns on themselves. While a Lockdown session is pending, the app covers other apps with a screen that leads back to the adhkaar, until the user finishes them or the adhkaar time ends. Phone, SMS, emergency and system screens are never covered, and the user can take a break at any time.

**Usage access: `PACKAGE_USAGE_STATS`.** Lockdown uses Usage Access to see which app is in front. It does **not** use an Accessibility Service, and should stay that way: Play restricts the Accessibility API to apps whose main purpose is helping people with disabilities, and its declaration is reviewed strictly.

> Used only in Lockdown mode, to check whether the app in front is allowed (phone, SMS, emergency) while a session is pending. The app reads only the current foreground app's package name, only during a pending session, and stores and sends none of it.

**Foreground service: `FOREGROUND_SERVICE_SPECIAL_USE`.** The service's subtype is already in the manifest. Play asks for a description and a video.

> The service rings the alert for the adhkaar session the user scheduled and keeps that session active (in Lockdown, covering other apps) until they complete it or its time window ends. It runs only while a session is pending, shows a notification the whole time, and stops as soon as the session is done. No other foreground service type fits: it isn't media playback, location, or a data sync.

**Also check:**

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Play allows this only for listed use cases, and rejections happen. The justification is that alarms and the Lockdown service are killed by battery savers on many phones. If Play rejects it, remove the permission and open the battery-optimisation settings list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) instead, which needs no permission.
- `ACCESS_COARSE_LOCATION`: optional, asked only when the user taps "Use location", used only to show calculated times for comparison. Declare it as not collected.
- `RECORD_AUDIO`: asked only when the user taps Record on their own dua. Recordings stay on the phone.

Record one short screen video showing onboarding, a Full screen session opening over the lock screen, and Lockdown covering another app with the break and emergency options visible. The same video serves all the declarations above.

### 6. Content rating

Fill in the IARC questionnaire: category **Reference, News or Educational**, no violence, no sexual content, no gambling, no user-generated content shared with others (your own duas stay on the phone), no purchases. Expected result: Everyone / PEGI 3.

Also declare: no ads, target audience 13+ (avoids the Families policy; the app is still suitable for all ages), not a news app.

### 7. Store listing (draft)

- **App name:** Adhkaar: Morning & Evening
- **Category:** Lifestyle (or Books & Reference)
- **Short description (80 characters max):** Your morning and evening adhkaar, on time, every day. Free, no ads, offline.
- **Full description:**

> Adhkaar brings your morning and evening adhkaar to you at the right time, like an alarm clock.
>
> Set your masjid's prayer times, and the app opens your adhkaar after Fajr and after Asr. Choose how firm it should be:
> • Gentle: one chime and a notification.
> • Full screen: the adhkaar open over the lock screen and ring until you're done.
> • Lockdown: other apps stay covered until you've said them, or their time ends. Calls and emergency calls always work, and you can take a break when you're busy.
>
> Also inside:
> • One dhikr at a time, with Arabic, transliteration, meaning and source, and a tap counter
> • Adhkaar after salah, before sleep, on waking, and everyday duas
> • Your own duas, in your own voice
> • A Qur'an verse or sahih hadith for each day, from Tanzil, QuranEnc and HadeethEnc
> • Hijri calendar following Nigeria's moon sighting
> • 7 home-screen widgets, streaks and insights, share cards
> • English, العربية, اردو, Hausa, Yorùbá, Igbo
>
> Free and open source. No account, no ads, no tracking. Works offline.

- **Graphics:** 512×512 icon, 1024×500 feature graphic, at least 4 phone screenshots (use the ones in `docs/screenshots/`). Localised listings for the six languages once the translations are reviewed.

### 8. Testing tracks

1. **Internal testing** (up to 100 testers, available within minutes). Maintainers and content reviewers. Use it for every release candidate.
2. **Closed testing.** For a new personal account: **at least 12 testers opted in for 14 continuous days**, then apply for production access in Play Console and answer the questions about the test. Recruit 20 or more testers so drop-outs don't reset the count, and ask for feedback on specific phone brands. Keep releasing fixes to this track during the 14 days.
3. **Production** once Play approves access.

### 9. Production and staged rollout

- Release to production with a **staged rollout**: 10% → 25% → 50% → 100%, a few days apart.
- Watch **Android vitals** (crashes, ANRs, "excessive wakeups") and reviews at each step. Halt the rollout if the crash rate rises or alarms are reported not firing.
- Expect the first review, with the sensitive permissions, to take up to a week. Answer policy emails from the shared maintainer account.

## F-Droid and GitHub Releases

- **GitHub Releases:** every tagged release attaches a signed APK ([RELEASING.md](RELEASING.md)). This is also the way to install on phones without Google Play.
- **F-Droid:** not possible with the noncommercial licence: F-Droid only accepts free software licences, which can't forbid commercial use. If the maintainers ever relicense the code (GPL-3.0 would keep forks open), the following applies: it has no Google Play Services, no trackers and no proprietary libraries. Open a request at <https://gitlab.com/fdroid/rfp> or submit metadata to `fdroiddata`. F-Droid builds from source and signs with its own key unless the build is reproducible, in which case it can ship the project's signature. Put the store text in `fastlane/metadata/android/<lang>/` so F-Droid (and later Play tooling) pick it up.
- **Check with F-Droid** about the third-party text that forbids changes (Tanzil, QuranEnc, HadeethEnc). F-Droid asks that bundled assets be free as well as the code; verbatim-only religious text has been accepted before, but confirm before submitting.
- **Android developer verification:** Google is rolling out a requirement that apps installed on certified Android phones, including outside Play, come from a verified developer (starting in some countries in 2026, wider in 2027). Register the signing key's owner so sideloaded and F-Droid builds keep installing, and track F-Droid's guidance on this.

## iOS

An iPhone version is a separate app. The Kotlin code can't be reused, but the content can.

### What ports and what doesn't

| Android feature | iOS |
|---|---|
| Content (adhkaar, collections, daily reminders, calendar, translations) | Ports as-is: the same JSON assets. |
| Reading, counter, library, my duas, insights, share cards, calendar | Ports: rebuild in SwiftUI. |
| Widgets | Ports: WidgetKit. Live Activities can show a pending session on the lock screen. |
| Gentle mode, collection and salah reminders | Ports: local notifications (at most 64 scheduled at once, so schedule a few days ahead and top up on each launch). |
| Full screen (open over the lock screen, re-ring) | **Partly.** An app can't open itself. Before iOS 26, only notifications (time-sensitive, which can break through Focus if allowed). On **iOS 26+, AlarmKit** gives a real alarm that rings through silent mode and Focus with a full-screen alert, and its button can open the session. |
| Lockdown (cover other apps) | **Only with Screen Time.** The FamilyControls, ManagedSettings and DeviceActivity APIs can shield chosen apps during a schedule (the adhkaar time) and lift the shield when the session is marked done. It needs Apple's **Family Controls entitlement**, requested separately for distribution, and the user picks the apps to shield. No overlays, no foreground-app detection. |
| Boot receivers, battery-manager workarounds, foreground services | Not needed or not possible. iOS keeps scheduled notifications and alarms across reboots. |
| Moon-sighting download | Ports: fetch on launch and with a background app refresh task. |

### Recommended approach

- **Native SwiftUI**, for the same reasons as on Android ([WHY_KOTLIN.md](WHY_KOTLIN.md)): AlarmKit, Screen Time and WidgetKit are Swift-only system frameworks.
- **Share the content, not the code.** Treat `app/src/main/assets/` as the single source. Either reference it from the iOS project in the same repository or copy it at build time. Keep the JSON schemas documented and stable, and keep pure rules (session windows, breaks, Hijri counting) small enough to port with their unit tests as the spec.
- Same content review, same translations, same credits.

### Rough milestones

1. **Foundations (4–6 weeks):** SwiftUI app reading the shared JSON; Today, session reader and counter, library; the user's prayer times; notifications for Gentle, collections and salah.
2. **Alarms (2–3 weeks):** AlarmKit on iOS 26+, time-sensitive notifications as the fallback; breaks.
3. **Screen Time "Lockdown" (3–4 weeks, plus Apple's entitlement review):** apply for the Family Controls entitlement early; shield during the adhkaar time, lift on completion.
4. **Widgets, calendar, insights, share cards, languages (3–4 weeks).**
5. **TestFlight beta, then App Store** (Apple Developer Program: US$99 a year).
