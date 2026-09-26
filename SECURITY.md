# Security

## Supported versions

Only the latest release on Google Play and GitHub Releases gets security fixes.

## Reporting a problem

Please **don't open a public issue** for a security problem. Report it privately through GitHub: **Security > Report a vulnerability** on this repository ([private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).

Include what you found, how to reproduce it, and which app version and Android version you used. We aim to reply within a week and to fix confirmed problems in the next release, crediting you unless you'd rather not be named.

## What matters here

The app has no server, no account and sends no data, so the useful reports are about the phone:

- Another app abusing an exported component (the system-event receiver, the widget receivers, the widget configuration screen, or the file provider).
- Lockdown blocking calls, the dialer, SMS or emergency calls, or trapping the user with no way out. We treat these as security bugs.
- The moon-sighting download being made to load something other than the calendar file, or crashing the app with a crafted file.
- Private data (your duas, voice recordings, progress, location) leaving the phone or being readable by other apps.
- Supply-chain issues: a dependency, a GitHub Action or the release workflow.

Content errors (a wrong word or reference) aren't security issues; use the [content correction form](../../issues/new?template=content_correction.yml).
