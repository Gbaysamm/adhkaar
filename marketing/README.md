# Social posts

Twenty-five 1080 × 1350 (4:5) posts for sharing Adhkaar. The exported PNGs are 2160 × 2700, in `export/`.

| Post | Story |
|---|---|
| 01-question | When did you last read the morning adhkaar on time? |
| 02-the-phone-wins | The problem: the group-chat reminder, and the phone wins |
| 03-before-after | The same morning, without and with Adhkaar |
| 04-modes | Gentle, Full screen, Lockdown |
| 05-your-day | The day as a timeline, from Fajr to sleep |
| 06-salah | The card before salah |
| 07-before-sleep | End the day with dhikr, not the feed |
| 08-missed | Missed adhkaar, said kindly |
| 09-streak | The streak |
| 10-languages | Hausa, Yorùbá, English, Arabic, Igbo |
| 11-trust | What it asks of you |
| 12-widgets | Home-screen widgets |
| 13-share | Share cards |
| 14-pause | A break, even in Lockdown |
| 15-update | For testers: version 0.1.7, with a QR code to the download |
| 16-moon-sighting | The Hijri date as announced by the moon-sighting committee |
| 17-calendar | Hijri and Gregorian in one month, filled in by what you read |
| 18-daily-reminder | A verse or hadith each day, and its share card |
| 19-special-days | Arafah, Ashura, the white days and Friday, the evening before |
| 20-insights | Streaks, sessions and the week |
| 21-my-duas | Your own duas beside the adhkaar |
| 22-collections | On waking, after salah, before sleep, everyday duas |
| 23-favourites | The adhkaar you return to |
| 24-your-times | Your masjid's salah times, not calculated ones |
| 25-reading | Arabic size, transliteration, translation, auto-advance, haptics |

## Editing

- `post.css` holds the shared grid:
  - 72px margins
  - the brand top-left and the stamp top-right
  - the footer
  - the phone frame
- `build.py` holds each post's composition.
- Screens come from `assets/`, which holds real captures from the app.

To rebuild after a change, run:

```
python marketing/build.py            # every post
python marketing/build.py 06-salah   # one post
```

The build renders each post with headless Microsoft Edge.
