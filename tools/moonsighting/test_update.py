"""Tests for the moon-sighting job, replayed against real newspaper feeds saved in fixtures/."""
import unittest
from datetime import date
from pathlib import Path

import update
from update import Report, Story

FIXTURES = Path(__file__).parent / "fixtures"


def saved_stories() -> list[Story]:
    stories = []
    for f in sorted(FIXTURES.glob("*.xml")):
        stories += update.parse_feed(f.stem, f.read_text(encoding="utf-8"))
    return stories


def story(title: str, text: str = "", published: date = date(2026, 8, 13), site: str = "paper") -> Story:
    return Story(site, f"https://{site}.ng/{abs(hash(title))}", title, text, published)


class ReadingStories(unittest.TestCase):
    def test_explicit_date_in_text_wins(self):
        s = story("Sultan urges Muslims to look out for moon", "look out for the new moon of Rabi'ul Awwal 1448 AH on Thursday, August 13, 2026.")
        (r,) = update.reports_from(s)
        self.assertEqual(("lookout", date(2026, 7, 16)), (r.kind, r.date))  # the 29th is Aug 13, so the month began July 16

    def test_weekday_in_headline(self):
        s = story("Sultan declares Friday first day of Rabi'ul Awwal", published=date(2026, 8, 13))
        self.assertEqual(date(2026, 8, 14), update.reports_from(s)[0].date)

    def test_weekday_only_in_body_is_ignored(self):
        # "on Tuesday" here is when the statement was issued, not the first day.
        s = story("Ramadan: Moon sighted, Sultan announces beginning of fasting", "a statement issued on Tuesday", date(2026, 2, 17))
        self.assertEqual([], update.reports_from(s))

    def test_denied_day_is_not_the_day(self):
        s = story("Not Thursday, Sultan announces Eid-el-Fitr Friday", published=date(2026, 3, 18))
        self.assertEqual(date(2026, 3, 20), update.reports_from(s)[0].date)

    def test_day_month_order(self):
        s = story("Sultan announces 1st day of Zulhijjah", "has declared Monday 18, May, 2026 to be", date(2026, 5, 18))
        self.assertEqual(date(2026, 5, 18), update.reports_from(s)[0].date)

    def test_other_countries_and_dissent_are_ignored(self):
        self.assertEqual([], update.reports_from(story("Saudi Arabia declares Friday Eid as Nigerians await Sultan's call")))
        self.assertEqual([], update.reports_from(story("Sokoto cleric, followers defy Sultan, lead Eid prayers on Thursday")))

    def test_not_sighted_tells_the_previous_month_had_30_days(self):
        s = story("Sultan declares Thursday as first day of Safar", "The announcement followed the inability to sight the moon", date(2026, 7, 15))
        kinds = {r.kind: r.date for r in update.reports_from(s)}
        self.assertEqual(date(2026, 7, 16), kinds["declaration"])
        self.assertEqual(date(2026, 6, 16), kinds["previous"])

    def test_tavily_results(self):
        stories = update.parse_tavily({"results": [
            {"title": "Sultan declares Friday first day of Rabi'ul Awwal 1448", "url": "https://www.vanguardngr.com/2026/08/x/",
             "content": "has declared Friday, August 14, 2026", "published_date": "Thu, 13 Aug 2026 21:10:00 GMT"},
            {"title": "No date", "url": "https://punchng.com/y/", "content": ""},
        ]})
        self.assertEqual(1, len(stories))
        self.assertEqual(("vanguardngr", date(2026, 8, 13)), (stories[0].site, stories[0].published))
        self.assertEqual(date(2026, 8, 14), update.reports_from(stories[0])[0].date)


class CommitteePosts(unittest.TestCase):
    X = "https://x.com/moonsightingng/status/2098916457165840678"

    def test_reads_the_daily_post(self):
        (r,) = update.committee_reports("Today’s date is Sunday 1st Rabi’ul Thani 1448H/13th September 2026", self.X)
        self.assertEqual((date(2026, 9, 13), "1448-04", "committee"), (r.date, r.hijri, r.kind))

    def test_any_day_of_the_month_gives_its_first_day(self):
        text = "Today's date is Wednesday 30th Muharram 1448H/15th July 2026. Today’s date is Friday 13th Rabi’ul Thani 1448H/25th September 2026"
        found = {r.hijri: r.date for r in update.committee_reports(text, "https://x.com/moonsightingng")}
        self.assertEqual({"1448-01": date(2026, 6, 16), "1448-04": date(2026, 9, 13)}, found)

    def test_only_the_official_accounts(self):
        self.assertEqual([], update.committee_reports("Today's date is Sunday 1st Rabi'ul Thani 1448H/13th September 2026", "https://x.com/someone/status/1"))

    def test_one_post_settles_a_month(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        posts = update.committee_reports("Today's date is Monday 1st Jumada al-Ula 1448H/12th October 2026", self.X)
        out, notes = update.update(data, [], date(2026, 10, 12), rule_allowed=True, extra=posts)
        self.assertEqual(("1448-05", "2026-10-12", "reports"), tuple(out["months"][-1][k] for k in ("hijri", "start", "decided")))
        self.assertEqual([], notes)

    def test_a_different_month_name_raises_an_alert(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        posts = update.committee_reports("Today's date is Sunday 1st Jumada al-Ula 1448H/13th September 2026", self.X)
        _, notes = update.update(data, [], date(2026, 9, 20), rule_allowed=True, extra=posts)
        self.assertTrue(any("1448-05" in n for n in notes))

    def test_author_pages_quoting_old_news_are_ignored(self):
        s = story("Animashaun Salman, Author at Punch Newspapers", "Sultan declares Saturday, March 1, first day of Ramadan", site="punchng")
        self.assertEqual([], update.reports_from(s))


class DecidingMonths(unittest.TestCase):
    def test_replays_nine_months_as_nigeria_observed_them(self):
        """From Rajab 1447 (declared for 22 December 2025), using only the saved newspaper feeds."""
        data = {"months": [{"hijri": "1447-07", "start": "2025-12-22", "decided": "reports"}]}
        out, notes = update.update(data, saved_stories(), date(2026, 9, 26), rule_allowed=True)
        starts = {m["hijri"]: m["start"] for m in out["months"]}
        self.assertEqual({
            "1447-07": "2025-12-22",
            "1447-08": "2026-01-20",  # Sha'aban: the moon was seen
            "1447-09": "2026-02-18",  # Ramadan began Wednesday
            "1447-10": "2026-03-20",  # Eid al-Fitr on Friday: Shawwal moon not seen on the 29th
            "1447-11": "2026-04-19",  # Dhul Qa'dah, "declared today"
            "1447-12": "2026-05-18",  # Dhul Hijjah: Monday, May 18
            "1448-01": "2026-06-16",  # Muharram
            "1448-02": "2026-07-16",  # Safar: Thursday, after the moon wasn't seen
            "1448-03": "2026-08-14",  # Rabi' al-Awwal: Friday, August 14
            "1448-04": "2026-09-13",  # Rabi' al-Thani: no sighting reported, 30-day rule
        }, starts)
        self.assertEqual([], notes)
        self.assertEqual("30-day rule", out["months"][-1]["decided"])

    def test_waits_before_the_month_can_have_started(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        out, _ = update.update(data, [], date(2026, 10, 10), rule_allowed=True)  # the 28th
        self.assertEqual(1, len(out["months"]))

    def test_no_rule_when_the_feeds_were_unreachable(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        out, _ = update.update(data, [], date(2026, 10, 14), rule_allowed=False)
        self.assertEqual(1, len(out["months"]))

    def test_rule_then_corrected_by_later_reports(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        guessed, _ = update.update(data, [], date(2026, 10, 13), rule_allowed=True)
        self.assertEqual("2026-10-13", guessed["months"][-1]["start"])  # 30-day rule
        reports = [
            story("Sultan declares Monday first day of Jumada al-Ula", published=date(2026, 10, 11), site="vanguardngr"),
            story("Jumada al-Ula: Sultan declares Monday first day", published=date(2026, 10, 11), site="punchng"),
        ]
        fixed, notes = update.update(guessed, reports, date(2026, 10, 13), rule_allowed=True)
        self.assertEqual("2026-10-12", fixed["months"][-1]["start"])
        self.assertTrue(any("moved" in n for n in notes))

    def test_conflicting_declarations_raise_an_alert(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        reports = [
            story("Sultan declares Monday first day of Jumada", published=date(2026, 10, 11), site="a"),
            story("Sultan declares Monday first day of Jumada", published=date(2026, 10, 11), site="b"),
            story("Sultan declares Tuesday first day of Jumada", published=date(2026, 10, 12), site="c"),
        ]
        _, notes = update.update(data, reports, date(2026, 10, 14), rule_allowed=True)
        self.assertTrue(any("1448-05" in n for n in notes))

    def test_evidence_is_kept_after_it_leaves_the_feeds(self):
        data = {"months": [{"hijri": "1448-04", "start": "2026-09-13"}]}
        reports = [story("Sultan declares Monday first day of Jumada", published=date(2026, 10, 11), site=s) for s in ("a", "b")]
        first, _ = update.update(data, reports, date(2026, 10, 13), rule_allowed=True)
        later, _ = update.update(first, [], date(2026, 10, 20), rule_allowed=True)
        self.assertEqual("2026-10-12", later["months"][-1]["start"])

    def test_tavily_only_around_the_29th(self):
        months = [{"hijri": "1448-04", "start": "2026-09-13"}]  # the 29th is 11 October
        self.assertFalse(update.tavily_due(months, date(2026, 10, 1)))
        self.assertTrue(update.tavily_due(months, date(2026, 10, 11)))
        self.assertFalse(update.tavily_due(months, date(2026, 10, 20)))


if __name__ == "__main__":
    unittest.main()
