# Daily reminder library

`build.py` builds the everyday "Reminder for today" rotation in
`app/src/main/assets/reminders/` from openly licensed sources, and checks the hand-written
special-day items in `app/src/main/assets/reminders.json` against the same authentic texts.
Why these sources, their terms and what the app must show are in
[docs/REMINDERS.md](../../docs/REMINDERS.md).

Python 3.10+ standard library only.

```bash
python tools/reminders/build.py                # build from the locked sources
python tools/reminders/build.py --update-lock  # accept new versions of the sources
python -m unittest discover tools/reminders    # tests for the selection rules (offline)
```

## What it does

1. Downloads each source into `.cache/` (not committed) and checks its SHA-256 against
   `sources.lock.json`. If a source changed, the build stops: look at what changed, then accept
   it with `--update-lock` and commit the new lock file with the rebuilt assets.
   QuranEnc asks republishers to keep up with its latest version, so check for updates
   (`--update-lock`) before every release.
2. Picks Qur'an verses and short passages that read well on their own (rules below), with the
   Tanzil Arabic and the Rowwad English exactly as published.
3. Picks HadeethEnc hadith graded sahih and attributed to Sahih al-Bukhari and/or Sahih Muslim
   alone, up to 400 characters of English, leaving out those filed only under rulings (trade,
   family law, punishments and so on). It finds each one's number (see below).
4. Deals verses and hadith out in turns, in a fixed shuffled order, and writes them 100 to a file
   (`everyday-000.json`, ...) with `library.json` holding the count. The app parses only the file
   for the day it shows. Every file carries the sources' copyright and version notices.
5. Checks every Arabic text in `reminders.json`: verses letter for letter against Tanzil, hadith
   against the full Arabic of the book and number cited first. Items from other books must carry
   their grading in the reference, e.g. `Jami' at-Tirmidhi 761 (hasan)`.
6. Writes `review.tsv`, the checklist for the scholar review, and the source versions into
   `app/src/main/res/values/strings_reminder_sources.xml`.

## Verse rules

A verse (or the first verse of a passage of up to three) must:

- start a sentence: a capital letter, not a continuation such as "And", "So", "Then", "Who",
  "Those who", "That", "When", "The Day", "Lord of";
- make sense alone: quotation marks that open and close inside it, no lowercase "my"/"me"
  (a person in a story speaking) unless it follows "Say", and a person named before any
  "they", "them", "he", "his" (the translation capitalises the pronouns of Allah);
- not tell a story (past-tense "said", the names of the people of the stories) or give a ruling
  (divorce, inheritance, fighting, debts and similar);
- have no footnote marker such as `[66]` (see docs/REMINDERS.md);
- be 40 to 320 characters of English, and the passage must end a sentence.

The rules are deliberately cautious. A reviewer removes anything they miss by adding its id to
`curation.json` with the reason, and rebuilding.

## Hadith numbers

HadeethEnc gives the al-Bukhari and Muslim numbers for about a third of its hadith. For the rest,
the build matches the Arabic against the full text of both books
([fawazahmed0/hadith-api](https://github.com/fawazahmed0/hadith-api), public domain, pinned to a
commit) and takes the number of the best match. Muslim is numbered as Fu'ad 'Abd al-Baqi's
edition (the one sunnah.com and HadeethEnc cite). In places the dataset's numbers run a few
ahead of or behind the printed editions', so matched numbers are marked `matched` in
`review.tsv` and need checking. A HadeethEnc number whose text is clearly another hadith is replaced, and the
build prints it.
