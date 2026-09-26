# Hijri dates in Nigeria

The app shows Hijri dates as declared by the **Sultan of Sokoto** (the Nigerian Supreme Council
for Islamic Affairs and the National Moonsighting Committee). There is no official API for these
declarations, so the project keeps them in one file, updated automatically:

`app/src/main/assets/calendar/ng.json`

The app ships with this file and downloads the latest version at most twice a day. Only the first
day of each month is stored; the app counts the rest.

## How the file stays up to date

A scheduled GitHub Action ([`.github/workflows/moon-sighting.yml`](../.github/workflows/moon-sighting.yml))
runs every three hours and calls [`tools/moonsighting/update.py`](../tools/moonsighting/update.py).

1. **It reads the committee's own posts.** The National Moonsighting Committee posts the date
   every day on X ([@moonsightingng](https://x.com/moonsightingng)) and Facebook: *"Today's date is
   Sunday 1st Rabi'ul Thani 1448H/13th September 2026"*. The job reads these through the
   [Tavily](https://tavily.com) search API once a day, and on every run around the 29th. Only the
   committee's official accounts count, and one post settles a month.
1. **It reads Nigerian newspapers** as a second source. Public feeds from Blueprint, The Eagle Online, Leadership,
   NewsclickNG, Annahda, Vanguard, Daily Trust, Premium Times, Legit, Punch, Channels and Tribune.
   Around each 29th it also searches the mainstream papers, including The Nation, Guardian and
   TheCable, through the [Tavily](https://tavily.com) search API.
2. **It turns newspaper stories into evidence.** Two kinds of story matter:
   - *"Look out for the moon on Thursday, August 13"*: that day is the 29th, so it confirms when
     the current month began.
   - *"Sultan declares Friday, August 14, first day of Rabi'ul Awwal"*: the new month's first day.

   Stories about other countries, or about people going against the Sultan's date, are ignored.
3. **It decides.** Every month is 29 or 30 days, so each month has only two possible first days.
   The job picks the ones the evidence supports; a committee post, or a declaration reported by
   two newspapers, settles a month. With no reports, the month completes 30 days, which is the rule when the moon isn't
   seen. Later reports correct an earlier guess.
4. **It never guesses between two reported answers.** If both possible days are reported, the
   committee names a different month than the file, or a published month moves, it opens a GitHub issue labelled `moon-sighting` for a person to check.

All the evidence is kept in the file under `reports`, with links, so anyone can see why a date
was chosen.

The reader is tested against real newspaper feeds saved in `tools/moonsighting/fixtures`: from
Rajab 1447 it reproduces every month as Nigeria observed it through Rabi' al-Thani 1448.

## Setup (once)

1. Push the repository to GitHub. Scheduled jobs are free for public repositories.
2. Optional but recommended: get a free key at [tavily.com](https://tavily.com) (1,000 searches a
   month; the job uses about 250) and add it under *Settings → Secrets and variables → Actions*
   as `TAVILY_API_KEY`.
3. Set `adhkaar.moonSightingUrl` in `gradle.properties` to the raw URL of the file, for example
   `https://raw.githubusercontent.com/<owner>/<repo>/main/app/src/main/assets/calendar/ng.json`.

GitHub pauses scheduled jobs in a repository with no activity for 60 days. The job commits a new
month roughly every 30 days, which keeps it running.

## Correcting a date by hand

Edit the month's `start` in `ng.json` and add a report for it under `reports` with
`"kind": "declaration"` and a link; the job treats it like any other report and won't undo it.
