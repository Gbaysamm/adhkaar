"""
Keeps app/src/main/assets/calendar/ng.json in step with Nigeria's moon sighting, automatically.

The Sultan of Sokoto (NSCIA) declares the start of every Hijri month, and Nigerian newspapers
report it, usually in two kinds of story:

  * a call to look for the moon: "look out for the moon on Thursday, August 13"
    (that day is the 29th, so it confirms when the current month began), and
  * a declaration: "Sultan declares Friday, August 14, first day of Rabi'ul Awwal".

Best of all, the National Moonsighting Committee posts the date every day on X and Facebook:
"Today's date is Sunday 1st Rabi'ul Thani 1448H/13th September 2026". The Tavily search API reads
those posts, and one of them settles a month on its own.

This script reads the committee's posts through Tavily (once a day, and every run around the
29th), the newspapers' public feeds, and around each 29th the mainstream papers through Tavily
too (Vanguard, Punch, The Nation, Daily Trust, Guardian and others, including the ones that
block plain feed readers). It turns each story into
a piece of evidence for a month's first day, and keeps every piece it has seen in the file.
It then picks the run of month starts that the evidence supports best, where every month is 29
or 30 days. With no evidence the month completes 30 days, which is the rule when the moon isn't
seen. A declaration reported by two newspapers settles a month on its own.

It never guesses between two well-reported answers: if both possible dates for a month have
declarations behind them, it writes alert.md, and the scheduled job opens a GitHub issue.

Run:  python tools/moonsighting/update.py            (reads the feeds, updates the file)
Test: python -m unittest discover tools/moonsighting
"""
from __future__ import annotations

import html
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CALENDAR = ROOT / "app/src/main/assets/calendar/ng.json"
ALERT = ROOT / "alert.md"

NIGERIA = timezone(timedelta(hours=1))  # West Africa Time, no daylight saving

# Newspaper search feeds (the WordPress/Blogger kind): these return older stories too.
SEARCH_FEEDS = {
    "blueprint": "https://blueprint.ng/?s={q}&feed=rss2",
    "eagleonline": "https://theeagleonline.com.ng/?s={q}&feed=rss2",
    "newsclick": "https://newsclickng.com/?s={q}&feed=rss2",
    "leadership": "https://leadership.ng/search/{q}/feed/rss2/",
    "annahda": "https://www.annahda.com.ng/feeds/posts/default?q={q}&alt=rss",
}
SEARCHES = ["sultan+moon", "sultan+declares"]
# Latest-news feeds: only recent stories, but checked every few hours that is enough.
LATEST_FEEDS = {
    "vanguard": "https://www.vanguardngr.com/feed/",
    "dailytrust": "https://dailytrust.com/feed/",
    "premiumtimes": "https://www.premiumtimesng.com/feed",
    "legit": "https://www.legit.ng/rss/all.rss",
    "punch": "https://punchng.com/feed/",
    "channels": "https://www.channelstv.com/feed/",
    "tribune": "https://tribuneonlineng.com/feed/",
}
# Mainstream papers searched through Tavily (https://tavily.com, free tier: 1,000 searches a
# month). The key is the TAVILY_API_KEY secret; without it, the feeds above are used alone.
TAVILY_DOMAINS = [
    "vanguardngr.com", "punchng.com", "thenationonlineng.net", "dailytrust.com", "premiumtimesng.com",
    "guardian.ng", "channelstv.com", "leadership.ng", "thecable.ng", "tribuneonlineng.com",
    "blueprint.ng", "legit.ng", "sunnewsonline.com", "independent.ng",
]
TAVILY_QUERIES = ["Sultan of Sokoto declares first day Islamic month", "Sultan of Sokoto moon sighting new moon"]
# Search only from two days before the 29th to three days after it: about 100 searches a month.
TAVILY_WINDOW = (-2, 3)
# Below this many feeds answering, the network is too unreliable to apply the 30-day rule.
MIN_FEEDS_FOR_RULE = 3

# The committee's own post outweighs anything the papers say about it.
WEIGHT = {"committee": 4, "declaration": 2, "lookout": 1, "previous": 1}

# The committee's official accounts; posts quoted elsewhere don't count.
COMMITTEE_ACCOUNTS = re.compile(r"(x|twitter)\.com/moonsightingng|facebook\.com/nsciamoonsightingcommittee", re.I)
COMMITTEE_QUERY = "National Moonsighting Committee Nigeria \"Today's date is\""

# ---------------------------------------------------------------------------------------------
# Reading stories


@dataclass(frozen=True)
class Story:
    site: str
    url: str
    title: str
    text: str
    published: date


@dataclass(frozen=True)
class Report:
    """One newspaper's evidence that a Hijri month began on [date]."""

    date: date
    site: str
    kind: str  # committee | declaration | lookout | previous
    url: str
    title: str
    hijri: str = ""  # "1448-04", when the report names the month (the committee's posts do)

    @property
    def weight(self) -> int:
        return WEIGHT[self.kind]

    def to_json(self) -> dict:
        out = {"date": self.date.isoformat(), "site": self.site, "kind": self.kind, "url": self.url, "title": self.title}
        if self.hijri:
            out["hijri"] = self.hijri
        return out

    @staticmethod
    def from_json(d: dict) -> "Report":
        return Report(date.fromisoformat(d["date"]), d["site"], d["kind"], d.get("url", ""), d.get("title", ""), d.get("hijri", ""))


def parse_feed(site: str, xml_text: str) -> list[Story]:
    stories = []
    for item in ET.fromstring(xml_text).iter("item"):
        title = clean(item.findtext("title") or "")
        text = clean(re.sub(r"<[^>]+>", " ", item.findtext("description") or ""))
        try:
            published = parsedate_to_datetime(item.findtext("pubDate") or "").astimezone(NIGERIA).date()
        except (TypeError, ValueError):
            continue
        stories.append(Story(site, (item.findtext("link") or "").strip(), title, text, published))
    return stories


def clean(s: str) -> str:
    s = html.unescape(s)
    s = re.sub(r"[‘’ʼʻ`´]", "'", s)
    s = re.sub(r"[“”]", '"', s)
    return re.sub(r"\s+", " ", s).strip()


WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"]
MONTH_RE = "|".join(MONTHS)

# Stories about somewhere else, or about people going against the Sultan's date.
EXCLUDE = re.compile(r"saudi|arabia|\buae\b|emirates|pakistan|morocco|egypt|defy|defie|defian|ignores|umrah", re.I)
AUTHORITY = re.compile(r"sultan", re.I)
TOPIC = re.compile(r"moon|crescent|sight|ramadan|eid|sallah|first day|1st day|hijjah|muharram|safar|rabi|jumad|rajab|sha.?aban|shawwal|ki.?ida|qa.?dah", re.I)
LOOKOUT = re.compile(r"look out|look for|lookout|search for|begin the search|observe the sky|to sight|calls? for|urges?|directs?|orders?|advise|instruct|participate in the sighting", re.I)
DECLARATION = re.compile(r"declar|first day|1st day|day 1|begins|kick.?off|commence|to be celebrated|observe eid|beginning of fasting|end of ramadan|announc\w* .{0,30}\b(eid|sallah|day)\b|\beid\b.{0,25}\b(on|holds|is)\b", re.I)
NOT_SIGHTED = re.compile(r"not (been )?sighted|no .{0,30}crescent sighted|inabilit|did not receive|no report|complet\w* 30|30 days", re.I)
SIGHTED = re.compile(r"moon sighted|confirmed sighting|sighting of the (new )?(moon|crescent)|following the sighting|crescent (was )?sighted", re.I)


def story_date(story: Story, part: str) -> date | None:
    """
    The date a story is about. Explicit calendar dates are trusted anywhere; a bare weekday only
    in the headline, since in body text it is often just the day the statement was issued.
    """
    for segment, weekdays_ok in ((story.title, True), (story.text, False)):
        found = explicit_date(segment, story.published)
        if found:
            return found
        if re.search(r"\btoday\b", segment, re.I) and weekdays_ok:
            return story.published
        if weekdays_ok:
            # "Not Thursday, Sultan announces Eid day": a denied day is not the day.
            s = re.sub(r"\bnot (on )?(" + "|".join(WEEKDAYS) + r")\b", "", segment, flags=re.I)
            m = re.search(r"\b(" + "|".join(WEEKDAYS) + r")\b", s, re.I)
            if m:
                return next_weekday(story.published - timedelta(days=1), WEEKDAYS.index(m.group(1).lower()))
    return None


def explicit_date(s: str, published: date) -> date | None:
    patterns = [
        rf"\b({MONTH_RE})\.? (\d{{1,2}})(?:st|nd|rd|th)?\b(?:,? (\d{{4}}))?",          # August 14, 2026
        rf"\b(\d{{1,2}})(?:st|nd|rd|th)?,? (?:of )?({MONTH_RE})\b(?:,? (\d{{4}}))?",  # 14 August / 18, May, 2026
    ]
    best = None
    for i, pattern in enumerate(patterns):
        for m in re.finditer(pattern, s, re.I):
            month_name, day = (m.group(1), m.group(2)) if i == 0 else (m.group(2), m.group(1))
            year = int(m.group(3)) if m.group(3) else published.year
            try:
                d = date(year, MONTHS.index(month_name.lower()) + 1, int(day))
            except ValueError:
                continue
            if not m.group(3):  # A story in late December can be about early January.
                if (d - published).days > 180:
                    d = d.replace(year=year - 1)
                elif (published - d).days > 180:
                    d = d.replace(year=year + 1)
            if best is None or m.start() < best[0]:
                best = (m.start(), d)
    return best[1] if best else None


def next_weekday(start: date, weekday: int) -> date:
    return start + timedelta(days=(weekday - start.weekday()) % 7)


# Hijri month names as Nigerian writers spell them, with everything but letters removed.
HIJRI_MONTHS = {
    1: ["muharram"], 2: ["safar"],
    3: ["rabiulawwal", "rabialawwal", "rabiulawal", "rabiiawwal", "rabiuawwal"],
    4: ["rabiulthani", "rabialthani", "rabiulakhir", "rabialakhir", "rabiussani", "rabiuthani", "rabiiithani"],
    5: ["jumadalula", "jumadaalula", "jumadalawwal", "jumadaalawwal", "jumadaluula", "jumadaulula", "jumadalawal"],
    6: ["jumadalthani", "jumadaalthani", "jumadalakhir", "jumadaalakhirah", "jumadaassaniya", "jumadalakhirah", "jumadathani", "jumadassaniya"],
    7: ["rajab"], 8: ["shaaban", "shaban", "shaaaban"], 9: ["ramadan", "ramadhan"], 10: ["shawwal", "shawal"],
    11: ["dhulqadah", "dhulqidah", "dhulqaadah", "zulqida", "zulkiida", "zulqadah", "dhulqaida"],
    12: ["dhulhijjah", "dhulhijja", "zulhijjah", "zulhijja", "dhulhijah"],
}
HIJRI_NUMBER = {name: n for n, names in HIJRI_MONTHS.items() for name in names}

COMMITTEE_POST = re.compile(
    r"today.{0,3}s date is\s+(?:[a-z]+day,?\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+([a-z'\u2019\u02bc\- ]+?)\s+(\d{4})\s*a?\.?h\.?\s*/\s*"
    rf"(\d{{1,2}})(?:st|nd|rd|th)?\s+(?:of\s+)?({MONTH_RE}),?\s+(\d{{4}})",
    re.I,
)


def committee_reports(text: str, url: str) -> list[Report]:
    """The committee's daily posts: each gives the day of the Hijri month, so the month's first day."""
    if not COMMITTEE_ACCOUNTS.search(url):
        return []
    site = "moonsightingng" if "moonsightingng" in url.lower() else "nmsc-facebook"
    out = {}
    for m in COMMITTEE_POST.finditer(text):
        hday, hname, hyear, gday, gmonth, gyear = m.groups()
        month = HIJRI_NUMBER.get(re.sub(r"[^a-z]", "", hname.lower()))
        try:
            gregorian = date(int(gyear), MONTHS.index(gmonth.lower()) + 1, int(gday))
        except ValueError:
            continue
        if month is None or not 1 <= int(hday) <= 30:
            continue
        start = gregorian - timedelta(days=int(hday) - 1)
        out[start] = Report(start, site, "committee", url, clean(m.group(0)), f"{int(hyear)}-{month:02d}")
    return list(out.values())


def reports_from(story: Story) -> list[Report]:
    both = f"{story.title} {story.text}"
    # A newspaper story counts only when its headline is about the moon or the calendar, which
    # keeps out author pages and round-ups that merely quote an old declaration.
    if EXCLUDE.search(both) or not AUTHORITY.search(both) or not TOPIC.search(story.title):
        return []
    if LOOKOUT.search(story.title):
        kind = "lookout"
    elif DECLARATION.search(story.title):
        kind = "declaration"
    elif LOOKOUT.search(story.text):
        kind = "lookout"
    elif DECLARATION.search(story.text):
        kind = "declaration"
    else:
        return []
    when = story_date(story, kind)
    if when is None:
        return []
    if kind == "lookout":
        # The search is on the 29th, so the current month began 28 days earlier.
        return [Report(when - timedelta(days=28), story.site, kind, story.url, story.title)]
    reports = [Report(when, story.site, kind, story.url, story.title)]
    # A declaration also tells how long the month before was.
    if NOT_SIGHTED.search(both):
        reports.append(Report(when - timedelta(days=30), story.site, "previous", story.url, story.title))
    elif SIGHTED.search(both):
        reports.append(Report(when - timedelta(days=29), story.site, "previous", story.url, story.title))
    return reports


# ---------------------------------------------------------------------------------------------
# Deciding the months


def next_hijri(label: str) -> str:
    year, month = map(int, label.split("-"))
    return f"{year + 1}-01" if month == 12 else f"{year}-{month + 1:02d}"


def evidence(reports: list[Report]) -> dict[date, int]:
    """Weight per date, counting each newspaper once per kind of evidence."""
    seen, score = set(), {}
    for r in reports:
        key = (r.date, r.site, r.kind)
        if key in seen:
            continue
        seen.add(key)
        score[r.date] = score.get(r.date, 0) + r.weight
    return score


def decide(anchor: dict, reports: list[Report], today: date, rule_allowed: bool = True) -> list[dict]:
    """
    Month starts from [anchor] up to today: the chain (29 or 30 days per month) with the most
    evidence. Ties go to 30-day months, the rule when the moon is not seen.
    """
    score = evidence(reports)
    # Each path: (evidence, thirty-day months, [starts]).
    paths = [(0, 0, [date.fromisoformat(anchor["start"])])]
    while True:
        extended = []
        for total, thirties, starts in paths:
            for length in (29, 30):
                start = starts[-1] + timedelta(days=length)
                extended.append((total + score.get(start, 0), thirties + (length == 30), starts + [start]))
        # A new month is included once it has begun, or once it is reported.
        newest = [p for p in extended if p[2][-1] <= today or score.get(p[2][-1], 0) > 0]
        if not newest:
            break
        # Keep the best path ending on each date (the chains never need more).
        by_end: dict[date, tuple] = {}
        for p in newest:
            if p[2][-1] not in by_end or p[:2] > by_end[p[2][-1]][:2]:
                by_end[p[2][-1]] = p
        # An unreported month that has only just begun waits for the reports, unless the rule applies.
        best = max(by_end.values(), key=lambda p: p[:2])
        if score.get(best[2][-1], 0) == 0 and not rule_allowed:
            break
        paths = list(by_end.values())
    total, thirties, starts = max(paths, key=lambda p: p[:2])
    months, label = [], anchor["hijri"]
    for i, start in enumerate(starts):
        supporting = sorted({r.url for r in reports if r.date == start and r.url})
        months.append({
            "hijri": label,
            "start": start.isoformat(),
            "decided": ("reports" if score.get(start, 0) > 0 else "30-day rule") if i else anchor.get("decided", "reports"),
            "source": " ".join(supporting) if i else anchor.get("source", ""),
        })
        label = next_hijri(label)
    return months


def conflicts(months: list[dict], reports: list[Report]) -> list[str]:
    """
    Months where the other possible first day also has a declaration behind it, or where the
    committee's own post names a different month than the file.
    """
    score = evidence([r for r in reports if r.kind in ("declaration", "committee")])
    out = []
    label_at = {m["start"]: m["hijri"] for m in months}
    for r in reports:
        if r.kind == "committee" and r.hijri and label_at.get(r.date.isoformat(), r.hijri) != r.hijri:
            out.append(f"**{label_at[r.date.isoformat()]}**: the committee calls the month beginning {r.date} {r.hijri}: [{r.title}]({r.url})")
    for prev, month in zip(months, months[1:]):
        chosen = date.fromisoformat(month["start"])
        other = date.fromisoformat(prev["start"]) + timedelta(days=59) - (chosen - date.fromisoformat(prev["start"]))
        if score.get(other, 0) >= WEIGHT["declaration"]:
            others = [f"- {r.site}: [{r.title}]({r.url})" for r in reports if r.date == other and r.kind in ("declaration", "committee")]
            out.append(f"**{month['hijri']}**: chose {chosen}, but these report {other}:\n" + "\n".join(others))
    return out


# ---------------------------------------------------------------------------------------------


def fetch(url: str) -> str | None:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; AdhkaarMoonSighting/1.0; +https://github.com)"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8", errors="replace")
    except Exception as e:  # One newspaper being down never stops the others.
        print(f"  skipped {url}: {e}", file=sys.stderr)
        return None


def site_of(url: str) -> str:
    host = re.sub(r"^https?://(www\.)?", "", url).split("/")[0]
    return host.split(".")[0]


def parse_tavily(response: dict) -> list[Story]:
    stories = []
    for r in response.get("results", []):
        try:
            published = parsedate_to_datetime(r["published_date"]).astimezone(NIGERIA).date()
        except (KeyError, TypeError, ValueError):
            try:
                published = date.fromisoformat(str(r.get("published_date", ""))[:10])
            except ValueError:
                continue  # Without a date, a weekday in the headline can't be placed.
        url = r.get("url", "")
        stories.append(Story(site_of(url), url, clean(r.get("title", "")), clean(r.get("content", "")), published))
    return stories


def read_committee(api_key: str) -> list[Report]:
    """The committee's recent daily posts on X and Facebook, through Tavily (2 credits)."""
    body = {
        "query": COMMITTEE_QUERY, "topic": "general", "search_depth": "advanced", "max_results": 10,
        "include_domains": ["x.com", "twitter.com", "facebook.com"], "include_raw_content": "text",
    }
    response = tavily(api_key, body)
    reports = []
    for r in (response or {}).get("results", []):
        reports += committee_reports(f"{r.get('content') or ''} {r.get('raw_content') or ''}", r.get("url", ""))
    return reports


def committee_due(months: list[dict], now: datetime) -> bool:
    """Once a day (the morning run), and every run around the 29th."""
    return tavily_due(months, now.date()) or 6 <= now.hour < 9


def tavily(api_key: str, body: dict) -> dict | None:
    request = urllib.request.Request(
        "https://api.tavily.com/search", data=json.dumps(body).encode(), method="POST",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return json.loads(response.read())
    except Exception as e:
        print(f"  Tavily search failed: {e}", file=sys.stderr)
        return None


def tavily_due(months: list[dict], today: date) -> bool:
    """Only search around the 29th of the current month, when an announcement is expected."""
    twenty_ninth = date.fromisoformat(months[-1]["start"]) + timedelta(days=28)
    return TAVILY_WINDOW[0] <= (today - twenty_ninth).days <= TAVILY_WINDOW[1]


def read_tavily(api_key: str) -> list[Story]:
    stories = []
    for query in TAVILY_QUERIES:
        response = tavily(api_key, {
            "query": query, "topic": "news", "time_range": "week", "search_depth": "basic",
            "max_results": 20, "include_domains": TAVILY_DOMAINS,
        })
        if response:
            stories += parse_tavily(response)
    return stories


def read_feeds() -> tuple[list[Story], int]:
    stories, answered = [], 0
    for site, template in SEARCH_FEEDS.items():
        for q in SEARCHES:
            text = fetch(template.format(q=q))
            if text:
                try:
                    stories += parse_feed(site, text)
                    answered += 1
                except ET.ParseError:
                    pass
    for site, url in LATEST_FEEDS.items():
        text = fetch(url)
        if text:
            try:
                stories += parse_feed(site, text)
                answered += 1
            except ET.ParseError:
                pass
    return stories, answered


def update(data: dict, stories: list[Story], today: date, rule_allowed: bool, extra: list[Report] = ()) -> tuple[dict, list[str]]:
    known = [Report.from_json(r) for r in data.get("reports", [])]
    new = [r for s in stories for r in reports_from(s)] + list(extra)
    keys = {(r.date, r.site, r.kind, r.url) for r in known}
    reports = known + [r for r in new if (r.date, r.site, r.kind, r.url) not in keys and not keys.add((r.date, r.site, r.kind, r.url))]
    months = decide(data["months"][0], reports, today, rule_allowed)
    notes = conflicts(months, reports)
    # Tell a person whenever a month that was already published moves.
    old = {m["hijri"]: m["start"] for m in data["months"]}
    for m in months:
        if m["hijri"] in old and old[m["hijri"]] != m["start"]:
            notes.append(f"**{m['hijri']}** moved from {old[m['hijri']]} to {m['start']} after new reports ({m['source'] or m['decided']}).")
    out = dict(data)
    out["months"] = months
    # Evidence from before the first listed month can't change anything; don't carry it.
    oldest = date.fromisoformat(months[0]["start"]) - timedelta(days=31)
    out["reports"] = sorted((r.to_json() for r in reports if r.date >= oldest), key=lambda r: (r["date"], r["site"], r["kind"]))
    return out, notes


def load_local_env() -> None:
    """For running on a computer: reads TAVILY_API_KEY from a git-ignored .env at the project root."""
    env = ROOT / ".env"
    if not env.exists():
        return
    for line in env.read_text(encoding="utf-8").splitlines():
        name, _, value = line.partition("=")
        if name.strip() and not name.strip().startswith("#") and value:
            os.environ.setdefault(name.strip(), value.strip().strip('"'))


def main() -> int:
    # --search-now: search Tavily even outside the days around the 29th.
    # --dry-run: show what would change without writing anything.
    search_now = "--search-now" in sys.argv
    dry_run = "--dry-run" in sys.argv
    load_local_env()
    sys.stdout.reconfigure(encoding="utf-8")
    data = json.loads(CALENDAR.read_text(encoding="utf-8"))
    stories, answered = read_feeds()
    now = datetime.now(NIGERIA)
    today = now.date()
    committee = []
    print(f"{answered} feeds answered, {len(stories)} stories")
    key = os.environ.get("TAVILY_API_KEY", "").strip()
    if key and (search_now or committee_due(data["months"], now)):
        committee = read_committee(key)
        print(f"Committee posts: {len(committee)}")
        for r in sorted(committee, key=lambda r: r.date):
            print(f"  {r.hijri} began {r.date}   ({r.title})")
    if key and (search_now or tavily_due(data["months"], today)):
        found = read_tavily(key)
        stories += found
        print(f"Tavily: {len(found)} stories")
        for story in found:
            for r in reports_from(story):
                print(f"  {r.kind:<11} {r.date}  {r.site:<14} {r.title[:70]}")
    elif search_now:
        print("No TAVILY_API_KEY found (add it to .env).")
    updated, notes = update(data, stories, today, rule_allowed=answered >= MIN_FEEDS_FOR_RULE, extra=committee)
    if dry_run:
        latest = updated["months"][-1]
        print(f"dry run: latest month {latest['hijri']} began {latest['start']} ({latest['decided']}); nothing written")
        for note in notes:
            print("alert:", note)
        return 0
    if updated != data:
        CALENDAR.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        latest = updated["months"][-1]
        print(f"updated: latest month {latest['hijri']} began {latest['start']} ({latest['decided']})")
    else:
        print("no change")
    if notes:
        ALERT.write_text("The moon-sighting job needs a person to look at this:\n\n" + "\n\n".join(notes) + "\n", encoding="utf-8")
        print("alert written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
