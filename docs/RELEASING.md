# Releasing

How a maintainer cuts a release. Store setup (Play Console, testing tracks, policy declarations) is in [LAUNCH.md](LAUNCH.md).

## Versions

- **`versionName`** follows semantic versioning, `MAJOR.MINOR.PATCH`:
  - PATCH: fixes and content corrections only.
  - MINOR: new features, new content or languages.
  - MAJOR: a change people must relearn, or a data change that can't be undone. 1.0 is the first Play Store production release.
- **`versionCode`** goes up by one for every build uploaded to Play, including internal-testing builds. Play rejects a code it has seen before.
- Both live in `app/build.gradle.kts` (`defaultConfig`). The release workflow refuses to build if the tag doesn't match `versionName`.
- Pre-releases for testing tracks use `-rc.N`, e.g. `1.0.0-rc.1`, and still get their own `versionCode`.

## Build properties

Set these in `gradle.properties` on `main` before the first public release:

| Property | Release value |
|---|---|
| `adhkaar.repoUrl` | `https://github.com/adhkaar-app/adhkaar` (the default). Links to the project in the app. |
| `adhkaar.reportUrl` | The deployed report worker, e.g. `https://adhkaar-report.<you>.workers.dev` (see [tools/report-worker](../tools/report-worker/README.md)). Used by **Contact us**. |
| `adhkaar.contactEmail` | The support address, or blank to hide the email option. Use the same address as the store listing. |
| `adhkaar.moonSightingUrl` | `https://raw.githubusercontent.com/adhkaar-app/adhkaar/main/app/src/main/assets/calendar/ng.json` |

If the repository moves, update `adhkaar.repoUrl` and `adhkaar.moonSightingUrl` together, and `GITHUB_REPO` in `tools/report-worker/wrangler.toml` (then redeploy the worker).

## Changelog

Keep [`CHANGELOG.md`](../CHANGELOG.md) in the [Keep a Changelog](https://keepachangelog.com) format. Each PR that users would notice adds a line under **Unreleased**, in plain words ("The evening session now...", not "Refactor EveningViewModel"). Content corrections go under their own **Content** heading with the source.

The Play Store "What's new" text (500 characters per language) is written from this section at release time.

## Signing

The release workflow signs with the **upload key**; Google re-signs for Play with the app signing key it holds ([LAUNCH.md](LAUNCH.md#2-app-signing)). The APK on GitHub Releases is signed with the upload key too.

Create the key once:

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Store `upload.jks` and both passwords in the maintainers' shared password manager, then add these **repository secrets** (in a `release` environment with required reviewers):

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the key password |

Never commit the keystore or put the passwords in `gradle.properties`.

## Cutting a release

1. **Check content sources.** Run `python tools/reminders/build.py --update-lock` to pick up new QuranEnc/HadeethEnc versions (their terms require it), and have a content reviewer approve any changes ([REMINDERS.md](REMINDERS.md)).
2. **Open a release PR** that bumps `versionName` and `versionCode`, moves the **Unreleased** changelog entries under the new version and date, and refreshes screenshots if the UI changed.
3. **Test the release build on a phone**: `./gradlew assembleRelease` (R8 can remove something the debug build keeps). Check a session opens on time in all three modes, the widgets draw, and the moon-sighting download works.
4. **Merge**, then tag the merge commit and push the tag:

   ```bash
   git tag -a v1.0.0 -m "Adhkaar 1.0.0"
   git push origin v1.0.0
   ```

5. The **Release** workflow (`.github/workflows/release.yml`) tests, builds, signs, and uploads:
   - a draft GitHub release with the signed APK and `SHA256SUMS`;
   - a workflow artifact with the signed AAB and the R8 `mapping.txt`.
6. **Upload the AAB** to Play Console, internal testing first, with the mapping file. Promote through closed testing and staged production rollout as in [LAUNCH.md](LAUNCH.md#9-production-and-staged-rollout).
7. **Publish the GitHub release** with the changelog section as its notes, once the Play rollout has started without problems.

## Hotfixes

Branch from the release tag (`release/1.0.x`), fix, bump PATCH and `versionCode`, and release from that branch the same way. Merge the fix back into `main`.

## Baseline Profile

Regenerate it when startup or the main screens change a lot, with a phone connected:

```bash
./gradlew :app:generateBaselineProfile
```

Commit the updated profile in `app/src/main/generated/baselineProfiles/` in its own PR.
