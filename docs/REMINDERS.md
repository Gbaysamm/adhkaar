# Daily reminders: sources, licences and review

The Today screen and the calendar show one "Reminder for today" per date: a Qur'an verse or a
sahih hadith, or on special days (Friday, the white days, Ramadan, the ten days of Dhul Hijjah,
'Arafah, the Eids, Tasu'a and 'Ashura) an item about that day.

| | Where | Items | Written by |
|---|---|---|---|
| Special days | `app/src/main/assets/reminders.json` | 31 | Hand, in the app's own English |
| Everyday rotation | `app/src/main/assets/reminders/` | 1,231 (668 Qur'an, 563 hadith) | `tools/reminders/build.py` |

The everyday rotation runs through all 1,231 items before any repeats: about 3 years and 4
months. Verses and hadith take turns. The files come to 745 KiB (about 200 KiB compressed in
the APK), in 13 files of 100 items; the app parses only the one holding the day it shows.

**Status: not yet reviewed by a scholar.** See [Review](#review) before any release.

## Sources used

Each one was chosen because its publisher clearly allows an app like this to redistribute it.
Checked on 26 September 2026.

### Qur'an Arabic: Tanzil Quran Text (Uthmani, version 1.1)

- Terms: <https://tanzil.net/docs/text_license>, repeated in the downloaded file. Creative
  Commons Attribution 3.0, with these conditions: "Permission is granted to copy and distribute
  verbatim copies of this text, but CHANGING IT IS NOT ALLOWED"; the text "can be used in any
  website or application, provided that its source (Tanzil Project) is clearly indicated, and a
  link is made to tanzil.net"; and the copyright notice "shall be reproduced appropriately in
  all files derived from or containing substantial portion of this text".
- How the app complies: verses are copied character for character (the build checks it, and
  also checks the special-day verses). Between the verses of a passage it puts the verse-end
  sign with the verse number (﴿٤١﴾), as printed mushafs do; the verses themselves are untouched.
  Every asset file carries Tanzil's full notice, and the in-app credits name Tanzil and link to
  tanzil.net.
- Note: the special-day verses were typed without the tatweel (ـ) Tanzil has before a dagger
  alif, and in a different order of combining marks. They now use Tanzil's exact characters;
  they look the same.

### Qur'an English: English Translation - Rowwad Translation Center (version 1.0.19), QuranEnc.com

- A modern, readable translation by the Rowwad Translation Center with the Rabwah Da'wah
  Association, the Islamic Content Service Association in Languages and IslamHouse.com,
  published on QuranEnc.com (Encyclopedia of the Noble Qur'an).
- Terms: <https://quranenc.com/en/home/api/>. Translations may be downloaded and republished on
  seven conditions:
  1. "No modification, addition, or deletion of the content."
  2. "Clearly referring to the publisher and the source (QuranEnc.com)."
  3. "Mentioning the version number when re-publishing the translation."
  4. "Keeping the transcript information inside the document."
  5. "Notifying the source (QuranEnc.com) of any note on the translation."
  6. "Updating the translation according to the latest version issued from the source (QuranEnc.com)."
  7. "Inappropriate advertisements must not be included when displaying translations."
- How the app complies: (1) each verse's translation is shown whole and unchanged; verses with
  a footnote marker such as `[66]` are left out, because showing the marker without the note,
  or removing it, would both change the text. (2)(3) every verse's reference ends
  "· QuranEnc.com", and the credits name the translation, the centre, QuranEnc.com and the
  version. (4) every asset file carries the title and version. (5) send corrections to
  QuranEnc.com rather than changing the text. (6) the build stops when QuranEnc publishes a new
  version; accept it with `--update-lock` before each release. (7) the app has no ads.

### Hadith, Arabic and English: HadeethEnc.com (version 1.25.0)

- The Encyclopedia of Translated Prophetic Hadiths: selected hadith with their Arabic text, a
  modern English translation, a grading and the books that narrate them.
- Terms: <https://hadeethenc.com/api-docs/> (the API documentation): "Contents of the project can
  be used, with the following terms and conditions: 1. No modification, addition, or deletion of
  the content. 2. Clearly referring to the publisher and the source (HadeethEnc.com)." The
  download also asks that its version notice not be removed.
- How the app complies: the Arabic and English of each hadith are shown whole and unchanged
  (the app does not show HadeethEnc's explanation, which is a separate text). Every hadith's
  reference ends "· HadeethEnc.com", the credits name HadeethEnc.com and its version, and every
  asset file carries HadeethEnc's notice.
- Only hadith that HadeethEnc grades sahih and attributes to al-Bukhari and Muslim together
  ("agreed upon"), or to one of them alone, are used. A hadith also narrated in the Sunan is
  left out even when sahih, because its wording may come from there.

### Hadith numbers: fawazahmed0/hadith-api (commit `df57907`)

- The full Arabic text of Sahih al-Bukhari and Sahih Muslim, released under the Unlicense
  (public domain). The classical Arabic texts are themselves long out of copyright.
- Used only inside the build, to find the number of hadith that HadeethEnc gives without one,
  and to check the Arabic of the special-day hadith. None of its text ships in the app.

## Sources considered and not used

| Source | Why not |
|---|---|
| Pickthall, *The Meaning of the Glorious Koran* (1930) | Public domain almost everywhere (Pickthall died in 1936, so life + 70 years ended in 2006; in the US, works published in 1930 entered the public domain on 1 January 2026). Two problems: the English is archaic ("thee", "thou"), and the digital copies found (Tanzil, many apps) don't say which printing they follow or who corrected them, and Tanzil offers its translations for non-commercial use only. Usable later if a verified 1930 text is found. |
| Yusuf Ali (1934–1937) | Public domain where copyright lasts life + 70 years (he died in 1953), but in the US works of that age from abroad stay protected until 95 years after publication (to about 2030–2033). Most digital copies are the revised editions (e.g. Amana, 1989), which are under copyright. |
| Tanzil's translations page | "For non-commercial purposes only. If used otherwise, you need to obtain necessary permission from the translator or the publisher." The app is free, but its code and content are meant to be open for anyone to reuse, so a non-commercial condition doesn't fit, and Tanzil does not grant the translators' rights. |
| Saheeh International, Hilali–Khan, Abdel Haleem, The Clear Quran and other modern translations | Copyrighted by their publishers, with no licence that covers bundling in an open-source app. (QuranEnc hosts Saheeh International and Hilali–Khan, but the rights stay with their publishers; Rowwad is QuranEnc's own.) |
| English hadith in fawazahmed0/hadith-api, sunnah.com data, hadith-json and similar scrapes | The English is Muhsin Khan's translation of al-Bukhari and Abdul Hamid Siddiqui's of Muslim, both still under copyright. The Unlicense on a repository can't license text its author doesn't own. sunnah.com gives no general reuse licence. |

## What the app must show

1. **On every reminder** (already in the data): the reference ends with the site to credit,
   "· QuranEnc.com" for verses and "· HadeethEnc.com" for hadith. It appears wherever the
   reference does: the Today card, the reminder sheet, the shared image and the shared text.
   Keep it there. The Today card cuts a long reference to one line; the full one is in the
   sheet the card opens.
2. **A credits section**, from `app/src/main/res/values/strings_reminder_sources.xml` (translated
   in `values-ar`, `-ur`, `-ha`, `-yo`, `-ig`). Show these together, for example at the bottom
   of Settings under "About", or as a "Sources" link in the reminder sheet:

   ```kotlin
   stringResource(R.string.reminder_sources_title)
   stringResource(R.string.reminder_sources_quran_arabic, stringResource(R.string.reminder_sources_tanzil_version))
   stringResource(R.string.reminder_sources_quran_meaning, stringResource(R.string.reminder_sources_rowwad_version))
   stringResource(R.string.reminder_sources_hadith, stringResource(R.string.reminder_sources_hadeethenc_version))
   stringResource(R.string.reminder_sources_unchanged)
   ```

   Make "tanzil.net" a link to <https://tanzil.net>, and ideally QuranEnc.com and HadeethEnc.com
   links too. The build keeps the three version strings up to date.
3. Never change the text of an everyday reminder (no trimming, re-punctuating, replacing words,
   and no machine translation into other languages). Both QuranEnc and HadeethEnc publish their
   own translations into Urdu, Hausa, Yoruba and other languages under the same terms, which is
   the way to offer the meanings in those languages later.

## How items are chosen

`tools/reminders/README.md` has the full rules. In short:

- **Verses** must start a sentence and not depend on the verse before (no "And", "So", "Those
  who", "They" and the like at the start, no pronoun without someone to refer to), must not be
  part of a story (someone speaking, the people of the stories) or a ruling (divorce,
  inheritance, fighting, debts and the like), must have no footnote marker, and must be 40–320
  characters of English. Short passages of two or three verses are kept when the first verse
  starts the sentence and the last ends it (54 of them), such as al-Ahzab 33:70–71.
- **Hadith** must be graded sahih by HadeethEnc, from al-Bukhari and/or Muslim only, up to 400
  characters of English, and not filed only under rulings (trade, family law, food, crimes,
  punishments, judiciary, purification, funerals, jihad).
- `tools/reminders/curation.json` lists items a reviewer took out, with the reason. 18 verses
  were taken out after a first read (for example ones about a particular battle, or starting
  "In each...").

The rules are cautious, so they also leave out many good verses. The biggest single cause is
the footnote rule (2,215 of the 6,236 verses have a footnote marker in this translation). If
QuranEnc confirms that the marker may be left out when the note isn't shown, allowing it adds
about 420 verses (around another year and two months of days).

## Hadith numbers

Of the 563 hadith, 278 have every number from HadeethEnc itself and 285 have at least one number
found by matching the Arabic against the full text of the book. The matching compares runs of
three words with the vowel marks removed, and needs 60% of the hadith's words in one place.
Spot checks found the matched numbers right, but in places the dataset's numbering runs a few
ahead of or behind the printed editions (its Bukhari 1163 is the printed 1169, for example).
74 hadith cite a second book as "also in Sahih al-Bukhari" (or Muslim) without a number,
because HadeethEnc names the book and no confident match was found. 55 hadith were left out
for having no number at all.

HadeethEnc's own numbers are checked the same way. One was wrong (its hadith 5394, on the
Friday bath, cites al-Bukhari 1294 and Muslim 103, which are a different hadith); the build uses
the matched numbers (al-Bukhari 894, Muslim 844) and prints the correction. It is worth
reporting to HadeethEnc.

Muslim is numbered as in Fu'ad 'Abd al-Baqi's edition, as HadeethEnc and sunnah.com do.

## Special-day items

These are the app's own English wording of the sources they cite, not a published translation.
Every Arabic text was checked against the downloaded sources:

- All 7 verses match Tanzil letter for letter (after using Tanzil's exact characters, above).
  Four are parts of verses, marked "(part)".
- 17 of the 19 hadith match the full Arabic of the al-Bukhari or Muslim number cited first. The
  other two, from at-Tirmidhi and Ibn Majah, were checked by hand against the same dataset's
  editions of those books.
- **Changed:**
  - `adha_days_of_tashriq` (Muslim 1141): the Arabic joined two narrations ("...and
    remembering Allah" is an addition in the second). It now quotes the first narration exactly,
    and the meaning mentions the addition.
  - `dhul_hijjah_best_days`: the wording is at-Tirmidhi's (757), not al-Bukhari's (969, which
    is worded differently and sits under al-Bukhari's chapter on the days of Tashriq).
    The reference now puts at-Tirmidhi first with its grading: "Jami' at-Tirmidhi 757 (sahih);
    similar in Sahih al-Bukhari 969". Graded sahih by Ahmad Shakir and al-Albani; at-Tirmidhi
    said hasan sahih gharib.
  - `qadr_dua_for_pardon`: the Arabic matches Ibn Majah 3850 exactly, while at-Tirmidhi 3513 in
    the downloaded edition adds "karim". Now "Sunan Ibn Majah 3850 (sahih); Jami' at-Tirmidhi
    3513". Graded sahih by al-Albani (hasan sahih by at-Tirmidhi; some later scholars graded it
    weak).
  - `white_days_fact`: now "Jami' at-Tirmidhi 761 (hasan)", its grading by at-Tirmidhi.
  - `dhul_hijjah_takbir`: cited Musnad Ahmad 5446, whose grading is disputed. Replaced by the
    report al-Bukhari records in the chapter before hadith 969: Ibn 'Umar and Abu Hurairah went
    out to the market in the ten days saying takbir, and people said it after them. It is a
    chapter report (mu'allaq), which has no number of its own.
  - `jumuah_sunnah`: added al-Bukhari 881 (coming early) and the grading of Abu Dawud 1047.
- The 50 everyday items that used to be in `reminders.json` were removed: the everyday rotation
  now comes only from the licensed sources, so every everyday item is published text rather
  than the app's paraphrase.
- Outside al-Bukhari and Muslim, a special-day item must carry its grading in brackets after the
  first reference; the build checks that it does.

## Review

Before release, a qualified scholar (and ideally a second reader) should go through:

1. **The special-day items** in `reminders.json`: the English wording, the four partial verses,
   and the gradings above.
2. **The everyday selection**, using `tools/reminders/review.tsv`: one line per item with a link
   to read it in full at QuranEnc or HadeethEnc, and where each hadith number came from. Take
   out anything that misleads on its own (verses whose meaning depends on their context that
   the rules missed, rulings, hadith better read with their explanation) by adding its id and
   reason to `tools/reminders/curation.json` and rebuilding. Numbers marked `matched` need a
   check against a printed edition or sunnah.com.
3. **The credits text** in the five translated string files (drafts, like the rest of the Hausa,
   Yoruba and Igbo interface).

| Part | Reviewed by | Date |
|---|---|---|
| Special-day items | | |
| Everyday verses (668) | | |
| Everyday hadith (563) and their numbers | | |
| Credits strings | | |

Also worth confirming with the publishers, though their published terms already allow this use:
that QuranEnc is content with verses shown one or a few at a time and with footnoted verses left
out (info@quranenc.com), and that HadeethEnc is content with hadith shown without their
explanation (the contact form on hadeethenc.com).
