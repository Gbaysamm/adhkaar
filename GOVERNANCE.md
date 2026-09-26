# Governance

Adhkaar carries the words of the Qur'an and the Sunnah, so the project treats two kinds of change differently: **code**, reviewed by maintainers, and **religious content**, which also needs a qualified content reviewer and a source.

## Roles

**Contributors.** Anyone who opens an issue, reviews, tests or sends a pull request.

**Maintainers** (`@adhkaar-app/maintainers`). Review and merge code, cut releases, run the Play Console and the GitHub organisation, and enforce the Code of Conduct. At least two maintainers hold admin rights everywhere (GitHub, Play Console, signing keys), so the project never depends on one person.

**Content reviewers** (`@adhkaar-app/content-reviewers`). A small committee of people with the knowledge to check religious text: Arabic and tashkeel, hadith references and gradings, translations of meaning. Aim for three to five, with at least one strong in hadith and one in Arabic. Reviewers are named in the team; they don't need to write code.

**Language reviewers.** Native speakers who review interface translations for one language. They're added per language and review under the maintainers' rules (interface text isn't religious content, but adhkaar meanings in `assets/i18n/` are).

### Joining and leaving

- New maintainers are invited by the existing maintainers after sustained, good-quality contributions, with no maintainer objecting.
- New content reviewers are proposed by any maintainer or reviewer, with a short note on their background, and approved by the existing content reviewers with no objection.
- Anyone can step down at any time; please say so in an issue so access can be handed over. Maintainers inactive for six months are moved to emeritus and their access removed; they can come back by asking.

## What counts as content

Content is any religious text or its source data:

- `app/src/main/assets/**`: adhkaar, collections, daily reminders, special-day reminders, meanings in `i18n/`, audio, and the Hijri calendar
- `tools/reminders/**`: the reminder build, its selection rules, `curation.json` and the source lock
- The credits and source strings (`strings_reminder_sources.xml`)

The moon-sighting bot's automatic updates to `calendar/ng.json` are the exception: they follow [docs/MOON_SIGHTING.md](docs/MOON_SIGHTING.md), and a content reviewer checks only the disputed cases the bot raises as issues.

## How changes are approved

### Code changes

- **One maintainer approval** and passing CI.
- Changes to scheduling, Lockdown enforcement, permissions or the safety rules should get a second maintainer's look, and a real-device test noted in the PR.
- Adding a permission, a network request or a dependency needs a maintainer's agreement in an issue first.

### Content changes

- **A content reviewer's approval is required**, in addition to the usual checks. CODEOWNERS requests it automatically.
- **Every change must cite a source**: the book and number, the page of a printed edition, or the publisher's URL and version. "I remember it this way" isn't a source.
- Text from Tanzil, QuranEnc and HadeethEnc is shown exactly as published. It isn't corrected here; errors are reported to the publisher, and the change arrives with their next version ([docs/REMINDERS.md](docs/REMINDERS.md)).
- Machine translation is never accepted for religious text.
- A content PR changes content only. Code in the same PR goes through the code rule as well.
- If reviewers disagree, the change waits. Where scholars differ on a wording or grading, the project follows the cited source and may note the difference; it doesn't pick sides by vote.

## How decisions are made

- **Most decisions** happen in issues and pull requests by lazy consensus: a proposal stands if no maintainer objects within a week (72 hours for fixes).
- **Larger decisions** (licence, adding or removing a feature, a new platform, changes to the safety rules, the project's scope) are opened as an issue labelled `decision`, left open for at least two weeks, and decided by the maintainers. If consensus can't be reached, a simple majority of maintainers decides.
- **Religious questions** are decided by the content reviewers, not by maintainers or by vote of contributors.
- **Non-negotiable:** no ads, no tracking, no selling data, calls and emergency access always work, and there's always a way out of Lockdown. Changing any of these would be a change to what the project is, not a decision within it.

## Branch protection for `main`

Recommended settings (GitHub > Settings > Branches, or a ruleset):

- Require a pull request before merging.
- **Require 1 approving review.**
- **Require review from Code Owners** (this is what enforces the content-reviewer rule).
- Dismiss stale approvals when new commits are pushed.
- **Require status checks to pass:** `Build, test and lint` from `ci.yml`. Don't require `Screenshot check`; it's informative.
- Require branches to be up to date before merging.
- Require conversation resolution before merging.
- **Block force pushes** and **block deletion** of `main`.
- Allow squash merging only; enable auto-delete of head branches.
- Apply the rules to administrators too.
- Allow the moon-sighting workflow's bot to push `calendar/ng.json` to `main` (add `github-actions` to the bypass list for that ruleset, or have the workflow open a PR instead).

Release tags (`v*`) should be protected so only maintainers can create them ([docs/RELEASING.md](docs/RELEASING.md)).

## Changing this document

Changes to this file are a larger decision, as above.
