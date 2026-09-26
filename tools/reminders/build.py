"""
Builds the everyday "Reminder for today" library in app/src/main/assets/reminders/ from
openly licensed sources, and checks the hand-written special-day items in reminders.json
against the same authentic texts.

Sources (terms and reasoning in docs/REMINDERS.md):

  * Qur'an Arabic: Tanzil Quran Text (Uthmani), CC BY 3.0, verbatim only.
  * Qur'an English: "English Translation - Rowwad Translation Center" from QuranEnc.com, which
    may be republished unmodified, crediting the publisher and QuranEnc.com, with its version.
  * Hadith Arabic + English: HadeethEnc.com, which may be used unmodified, crediting the
    publisher and HadeethEnc.com. Only items that HadeethEnc grades sahih and attributes to
    Sahih al-Bukhari and/or Sahih Muslim alone are used.
  * Hadith numbers: HadeethEnc gives numbers for some items. For the rest, the Arabic is
    matched against the full Arabic text of both books (fawazahmed0/hadith-api, public domain
    under the Unlicense) to find the number. Only numbers leave that dataset, never its text.

Every download is pinned: sources.lock.json holds each file's SHA-256 (and the publisher's
version where there is one). A source that changes stops the build until someone reviews the
change and accepts it with --update-lock.

Run:     python tools/reminders/build.py                (build from the locked sources)
Update:  python tools/reminders/build.py --update-lock  (accept new source versions)
Test:    python -m unittest discover tools/reminders
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import sqlite3
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CACHE = HERE / ".cache"
LOCK = HERE / "sources.lock.json"
CURATION = HERE / "curation.json"
REVIEW = HERE / "review.tsv"
ASSETS = ROOT / "app/src/main/assets"
SPECIAL = ASSETS / "reminders.json"
OUT = ASSETS / "reminders"
STRINGS = ROOT / "app/src/main/res/values/strings_reminder_sources.xml"

USER_AGENT = "AdhkaarReminderBuild/1 (+https://github.com/; open-source app, offline assets)"

HADITH_API_COMMIT = "df57907be35291c91ad6a6691180e22ca9920784"
FILES = {
    # marks: pause marks; sajdah: the sajdah sign; rub=false: no rub' al-hizb signs mid-verse.
    "tanzil": "https://tanzil.net/pub/download/index.php"
              "?marks=true&sajdah=true&rub=false&quranType=uthmani&outType=txt-2&agree=true",
    "rowwad": "https://quranenc.com/downloads/sqlite/english_rwwad.sqlite",
    "hadeethenc": "https://hadeethenc.com/browse/download/en",
    "bukhari": f"https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@{HADITH_API_COMMIT}/editions/ara-bukhari.min.json",
    "muslim": f"https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@{HADITH_API_COMMIT}/editions/ara-muslim.min.json",
}
QURANENC_LIST = "https://quranenc.com/api/v1/translations/list/en"
ROWWAD_KEY = "english_rwwad"
HADEETHENC_API = "https://hadeethenc.com/api/v1"

# Items per asset file. The app parses only the file holding the day it shows, so this keeps
# each parse to a few dozen kilobytes on old phones.
CHUNK_SIZE = 100
# A reminder must fit the Today card and the shared image without shrinking to nothing.
MAX_VERSE_CHARS = 320
MAX_HADITH_CHARS = 400
# Short passages keep well-known sequences (a verse that finishes its sentence in the next).
MAX_PASSAGE_VERSES = 3

SURAHS = [
    "al-Fatihah", "al-Baqarah", "Al 'Imran", "an-Nisa'", "al-Ma'idah", "al-An'am", "al-A'raf",
    "al-Anfal", "at-Tawbah", "Yunus", "Hud", "Yusuf", "ar-Ra'd", "Ibrahim", "al-Hijr", "an-Nahl",
    "al-Isra'", "al-Kahf", "Maryam", "Taha", "al-Anbiya'", "al-Hajj", "al-Mu'minun", "an-Nur",
    "al-Furqan", "ash-Shu'ara'", "an-Naml", "al-Qasas", "al-'Ankabut", "ar-Rum", "Luqman",
    "as-Sajdah", "al-Ahzab", "Saba'", "Fatir", "Ya-Sin", "as-Saffat", "Sad", "az-Zumar", "Ghafir",
    "Fussilat", "ash-Shura", "az-Zukhruf", "ad-Dukhan", "al-Jathiyah", "al-Ahqaf", "Muhammad",
    "al-Fath", "al-Hujurat", "Qaf", "adh-Dhariyat", "at-Tur", "an-Najm", "al-Qamar", "ar-Rahman",
    "al-Waqi'ah", "al-Hadid", "al-Mujadilah", "al-Hashr", "al-Mumtahanah", "as-Saff", "al-Jumu'ah",
    "al-Munafiqun", "at-Taghabun", "at-Talaq", "at-Tahrim", "al-Mulk", "al-Qalam", "al-Haqqah",
    "al-Ma'arij", "Nuh", "al-Jinn", "al-Muzzammil", "al-Muddaththir", "al-Qiyamah", "al-Insan",
    "al-Mursalat", "an-Naba'", "an-Nazi'at", "'Abasa", "at-Takwir", "al-Infitar", "al-Mutaffifin",
    "al-Inshiqaq", "al-Buruj", "at-Tariq", "al-A'la", "al-Ghashiyah", "al-Fajr", "al-Balad",
    "ash-Shams", "al-Layl", "ad-Duha", "ash-Sharh", "at-Tin", "al-'Alaq", "al-Qadr", "al-Bayyinah",
    "az-Zalzalah", "al-'Adiyat", "al-Qari'ah", "at-Takathur", "al-'Asr", "al-Humazah", "al-Fil",
    "Quraysh", "al-Ma'un", "al-Kawthar", "al-Kafirun", "an-Nasr", "al-Masad", "al-Ikhlas",
    "al-Falaq", "an-Nas",
]
assert len(SURAHS) == 114

# ---------------------------------------------------------------------------------------------
# Verse heuristics. They read the English, because that is where a dependent clause shows.

# A verse opening with one of these carries on from the verse before it ("And...", "Those
# who...") or refers back to someone named there ("They...", "He said...").
CONTINUATION = re.compile(
    r"^(And|So|Then|But|Or|Nor|Neither|Yet|For|Because|Except|Unless|Until|While|Whereas|Rather|"
    r"Nay|Who|Whom|Whose|Which|That|Those|These|This|Such|Thus|Therefore|Hence|Thereupon|Also|"
    r"Likewise|They|Their|Them|It|Its|Both|Each|Some|Among|Of|Therein|Upon|When|Whenever|As|"
    r"Even|Instead|With|Having|Being|Besides|Along|After|Before|Since|Where|Wherever|Although|Though|"
    r"On (the|that) Day|The Day|A Day|On a Day|Other|Another|Other than|Lord of|\w+ed (by|with|in))\b"
)
# "they", "them", "their" (and a lowercase "he", "him", "his": the translation capitalises the
# pronouns of Allah) need someone named earlier in the verse, else they point back to the verse
# before: "The decree will befall them...".
PRONOUN = re.compile(r"\b([Tt]hey|[Tt]hem|[Tt]heir|[Tt]heirs|[Tt]hemselves|he|him|his|himself)\b")
ANTECEDENT = re.compile(
    r"\b(those|who|whoever|whom|people|mankind|humankind|humans?|man|men|women|believers|disbelievers|"
    r"righteous|wrongdoers|servants?|children|parents|anyone|everyone|one|soul|person|"
    r"messengers?|prophets?|angels)\b",
    re.IGNORECASE,
)
FIRST_PERSON = re.compile(r"\b(me|my|mine|myself)\b")
# Stories read wrongly on their own, so verses that tell one are left out: past-tense speech and
# the people of the stories.
NARRATIVE = re.compile(
    r"\b(said|replied|answered|called out|cried)\b|"
    r"\b(Pharaoh|Moses|Mūsa|Mūsā|Aaron|Hārūn|Noah|Nūh|Nūḥ|Lot|Lūt|Lūṭ|Shu‘ayb|Shu'ayb|Sālih|Ṣāliḥ|"
    r"Hūd|Joseph|Yūsuf|Jacob|Ya‘qūb|Zakariyya|Zechariah|Solomon|Sulaymān|David|Dāwūd|Iblīs|Qārūn|"
    r"Hāmān|Thamūd|‘Ād|Midian|Madyan|Sheba|Saba|Dhul-Qarnayn|Luqmān|Samiri|Sāmiri|Goliath|Saul)\b"
)
# Rulings are read with their context and a scholar; a daily reminder is not the place.
LEGAL = re.compile(
    r"divorc|menstruat|waiting period|dowr|inherit|bequest|bequeath|nurs(e|ing)|breastfe|"
    r"one-(third|sixth|eighth|fourth|half)|\bhalf\b|\bthird\b|\bsixth\b|retaliat|lash|flog|"
    r"cut off|amputat|witness(es)?\b.*\b(debt|contract|writing)|\bdebt|\bloan|\bcontract|"
    r"slave|captive|spoils|booty|\bfight|\bkill|\bslay|\bwar\b|battle|expedition|"
    r"prohibited to you|lawful (for|to) you|\bmarry|\bmarri|\bwives\b|\bwife\b|intercourse|"
    r"\bJews\b|Christians|People of the Book|Children of Israel|hypocrit|idol|usury",
    re.IGNORECASE,
)
FOOTNOTE = re.compile(r"\[\d+\]")
SENTENCE_END = re.compile(r"[.!?][”’\")\]]*$")


def starts_a_sentence(english: str) -> bool:
    """The verse can open a reminder: it begins with a capital and depends on nothing before."""
    if not english.lstrip("[")[:1].isupper() or CONTINUATION.match(english):
        return False
    # "[Remember] when...", "[He said]..." are narrative; "[O Prophet], ..." addresses the reader.
    return not (english.startswith("[") and not english.startswith("[O "))


def ends_a_sentence(english: str) -> bool:
    return bool(SENTENCE_END.search(english.strip()))


def self_contained(english: str) -> bool:
    """
    The words make sense without the verses around them: every quotation opens and closes
    inside them (else they are part of someone's speech in a story), nobody in a story is
    speaking, and pronouns have something to refer to.
    """
    if english.count("“") != english.count("”"):
        return False
    # A lowercase "my", "me" is a person speaking (Allah's are capitalised). Unless the Prophet
    # is told to "Say" it, that person is someone in a story: "O my people...".
    speaker = FIRST_PERSON.search(english)
    if speaker and "Say" not in english[:speaker.start()]:
        return False
    pronoun = PRONOUN.search(english)
    return pronoun is None or bool(ANTECEDENT.search(english[:pronoun.start()]))


def usable_text(english: str) -> bool:
    """Nothing in the verse rules it out wherever it sits in a passage."""
    # Footnote markers point at notes that aren't shown, and QuranEnc allows no changes, so
    # footnoted verses are left out rather than edited (docs/REMINDERS.md).
    if FOOTNOTE.search(english):
        return False
    return not (NARRATIVE.search(english) or LEGAL.search(english))


@dataclass
class Verse:
    surah: int
    ayah: int
    arabic: str
    english: str


def select_verses(verses: list[Verse], exclude: set[str]) -> list[list[Verse]]:
    """
    Single verses that stand alone, and passages of up to three verses whose first verse opens
    a sentence and whose last closes it. Each verse is used at most once, a single verse first.
    """
    chosen: list[list[Verse]] = []
    by_surah: dict[int, list[Verse]] = collections.defaultdict(list)
    for v in verses:
        by_surah[v.surah].append(v)
    for surah_verses in by_surah.values():
        i = 0
        while i < len(surah_verses):
            first = surah_verses[i]
            taken = 0
            # Muqatta'at ("Alif-Lām-Mīm.") and other very short verses say nothing on their own.
            if len(first.english) >= 40 and starts_a_sentence(first.english) and usable_text(first.english):
                for n in range(1, MAX_PASSAGE_VERSES + 1):
                    group = surah_verses[i:i + n]
                    if len(group) < n or not all(usable_text(v.english) for v in group):
                        break
                    if len(" ".join(v.english for v in group)) > MAX_VERSE_CHARS:
                        break
                    if ends_a_sentence(group[-1].english):
                        if self_contained(" ".join(v.english for v in group)) and verse_id(group) not in exclude:
                            chosen.append(group)
                            taken = n
                        break
            i += taken or 1
    return chosen


def verse_id(group: list[Verse]) -> str:
    first, last = group[0], group[-1]
    return f"q{first.surah}_{first.ayah}" + (f"_{last.ayah}" if last.ayah != first.ayah else "")


ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def verse_reminder(group: list[Verse]) -> dict:
    first, last = group[0], group[-1]
    ayahs = f"{first.ayah}" if len(group) == 1 else f"{first.ayah}–{last.ayah}"
    # Verse-end markers between the verses of a passage, as printed mushafs do.
    arabic = " ".join(
        v.arabic + ("" if v is last else f" ﴿{str(v.ayah).translate(ARABIC_DIGITS)}﴾") for v in group
    )
    return {
        "id": verse_id(group),
        "kind": "verse",
        "arabic": arabic,
        "meaning": " ".join(v.english for v in group),
        "reference": f"Surah {SURAHS[first.surah - 1]} {first.surah}:{ayahs} · QuranEnc.com",
    }


# ---------------------------------------------------------------------------------------------
# Hadith

# HadeethEnc's own attribution ("takhrij"). Only these: a hadith also found in the Sunan is
# fine, but then its wording may come from there.
BUKHARI_MUSLIM = {
    "متفق عليه": ("bukhari", "muslim"),
    "رواه البخاري ومسلم": ("bukhari", "muslim"),
    "رواه البخاري": ("bukhari",),
    "رواه مسلم": ("muslim",),
}
BOOK_NAMES = {"bukhari": "Sahih al-Bukhari", "muslim": "Sahih Muslim"}
REFERENCE_LINE = {"bukhari": "صحيح البخاري", "muslim": "صحيح مسلم"}
# Topics of rulings (HadeethEnc category ids and everything under them): transactions,
# inheritance, family law, food, crimes, punishments, judiciary, purification, funerals and
# jihad. A hadith filed only under these is left out; one also filed under faith or manners stays.
LEGAL_CATEGORIES = {"122", "123", "124", "126", "127", "128", "129", "130", "131", "132", "133", "135", "139"}
# Below this share of shared three-word runs, two Arabic texts are not the same hadith.
MATCH_THRESHOLD = 0.6
# Below this share, the book's text under a number HadeethEnc gives is another hadith: wording
# differs a little between narrations, but a wrong number shares next to nothing.
CONFIRM_THRESHOLD = 0.3
# How far this edition's numbering strays from the printed editions' in places (Bukhari 1169
# is its 1163, for one).
NUMBERING_DRIFT = 10

TASHKEEL = re.compile("[\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed\u0640\u200e\u200f]")


def arabic_words(text: str) -> list[str]:
    """Arabic reduced to bare letters, so editions with different vowelling compare equal."""
    text = TASHKEEL.sub("", text).replace("ﷺ", " صلى الله عليه وسلم ")
    text = re.sub("[أإآٱ]", "ا", text).replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
    return re.sub("[^\u0621-\u064a\\s]", " ", text).split()


def shingles(words: list[str], n: int = 3) -> set[str]:
    return {" ".join(words[i:i + n]) for i in range(len(words) - n + 1)}


def spoken_part(text: str) -> str:
    """The Prophet's words, between « », without the chain of narrators that differs by edition."""
    quoted = re.findall("«(.*?)»", text, re.S)
    return " ".join(quoted) if quoted else text


class Collection:
    """One of the two books in full Arabic, indexed to find where a text comes from."""

    def __init__(self, hadiths: list[tuple[int, str]]):
        self.numbers = [n for n, _ in hadiths]
        self.texts = [t for _, t in hadiths]
        self.index: dict[str, list[int]] = collections.defaultdict(list)
        for i, (_, text) in enumerate(hadiths):
            for g in shingles(arabic_words(text)):
                self.index[g].append(i)

    def find(self, text: str) -> tuple[int | None, float]:
        """The number whose text holds most of [text]; the lowest number wins a tie."""
        query = shingles(arabic_words(spoken_part(text)))
        if not query:
            return None, 0.0
        hits = collections.Counter(i for g in query for i in self.index.get(g, ()))
        if not hits:
            return None, 0.0
        best = max(hits.values())
        i = min((i for i, n in hits.items() if n == best), key=lambda i: self.numbers[i])
        return self.numbers[i], best / len(query)

    def contradicts(self, text: str, number: int, matched: int | None) -> bool:
        """
        [number] is not where [text] is: the book has other words under it and the text was
        found well away from it. Close numbers are the same hadith counted differently: in
        places this edition's numbers run a few ahead of or behind the printed ones.
        """
        return (
            bool(self.texts_numbered(number)) and self.share(text, number) < CONFIRM_THRESHOLD
            and matched is not None and abs(matched - number) > NUMBERING_DRIFT
        )

    def share(self, text: str, number: int) -> float:
        """How much of [text] the hadith with [number] holds (any of its narrations)."""
        query = shingles(arabic_words(spoken_part(text)))
        held = set().union(*(shingles(arabic_words(t)) for t in self.texts_numbered(number)))
        return len(query & held) / len(query) if query else 0.0

    def texts_numbered(self, number: int) -> list[str]:
        return [t for n, t in zip(self.numbers, self.texts) if n == number]


def load_collection(path: Path, book: str) -> Collection:
    data = json.loads(path.read_text(encoding="utf-8"))
    hadiths = []
    for h in data["hadiths"]:
        if not h["text"]:
            continue
        if book == "bukhari":
            hadiths.append((int(h["hadithnumber"]), h["text"]))
        elif "arabicnumber" in h:
            # Muslim is cited by Fu'ad 'Abd al-Baqi's numbers (the whole part of "arabicnumber";
            # the fraction numbers the narrations under it), as HadeethEnc and sunnah.com do.
            hadiths.append((int(float(h["arabicnumber"])), h["text"]))
    return Collection(hadiths)


def numbers_from_reference(reference: str, book: str) -> int | None:
    """
    The number HadeethEnc's reference gives for [book], as in "صحيح البخاري (9/ 2) (6864)":
    the book's name, maybe a volume/page in brackets, then the number in brackets.
    """
    # Not "شرح صحيح مسلم (140/7)", which is a commentary's volume and page.
    m = re.search(r"(?<!شرح )" + REFERENCE_LINE[book] + r"[^\d()]*(?:\(\s*\d+\s*/[^)]*\)\s*)?\((\d+)", reference or "")
    return int(m.group(1)) if m else None


def xlsx_rows(path: Path) -> tuple[str, list[dict[str, str]]]:
    """HadeethEnc's notice (the first cell) and the rows of its one-sheet .xlsx, keyed by the header row."""
    ns = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    with zipfile.ZipFile(path) as z:
        strings = ["".join(t.text or "" for t in si.iter(ns + "t"))
                   for si in ET.fromstring(z.read("xl/sharedStrings.xml")).iter(ns + "si")]
        sheet = ET.fromstring(z.read("xl/worksheets/sheet1.xml"))
    rows = []
    for row in sheet.iter(ns + "row"):
        cells = {}
        for c in row.iter(ns + "c"):
            col = re.match("[A-Z]+", c.get("r")).group()
            v = c.find(ns + "v")
            if c.get("t") == "s":
                cells[col] = strings[int(v.text)]
            elif c.get("t") == "inlineStr":
                cells[col] = "".join(t.text or "" for t in c.iter(ns + "t"))
            else:
                cells[col] = v.text if v is not None else ""
        rows.append(cells)
    notice = rows[0]["A"]
    header = rows[1]
    return notice, [{header[k]: v for k, v in r.items() if k in header} for r in rows[2:]]


def hadith_reminder(row: dict, numbers: dict[str, int | None]) -> dict:
    parts = [f"{BOOK_NAMES[b]} {n}" for b, n in numbers.items() if n]
    parts += [f"also in {BOOK_NAMES[b]}" for b, n in numbers.items() if not n]
    return {
        "id": f"h{row['id']}",
        "kind": "hadith",
        "arabic": row["hadith_text_ar"],
        "meaning": row["hadith_text"],
        "reference": "; ".join(parts) + " · HadeethEnc.com",
    }


# ---------------------------------------------------------------------------------------------
# Rotation

def rotation(items: list[dict]) -> list[dict]:
    """
    The order of the days. Each kind is shuffled by a hash of the id (not Python's hash(), which
    changes per run), so neighbours in a surah or a book don't land on neighbouring days. The two
    kinds are then dealt out evenly, so a verse and a hadith take turns instead of either running
    for weeks.
    """
    kinds = [
        sorted((r for r in items if r["kind"] == kind), key=lambda r: hashlib.sha256(r["id"].encode()).hexdigest())
        for kind in ("verse", "hadith")
    ]
    taken = [0, 0]
    order = []
    for _ in range(len(items)):
        # The kind whose next item is furthest behind its even spacing goes next.
        k = min((k for k in (0, 1) if taken[k] < len(kinds[k])), key=lambda k: (taken[k] + 0.5) / len(kinds[k]))
        order.append(kinds[k][taken[k]])
        taken[k] += 1
    return order


# ---------------------------------------------------------------------------------------------
# Downloads, pinned by the lock file

def fetch(url: str) -> bytes:
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except OSError:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError("unreachable")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class Sources:
    """Downloads into .cache/ once, and checks every file against sources.lock.json."""

    def __init__(self, update_lock: bool):
        self.update_lock = update_lock
        self.lock = json.loads(LOCK.read_text(encoding="utf-8")) if LOCK.exists() else {}
        CACHE.mkdir(exist_ok=True)

    def get(self, name: str, url: str, make=None) -> Path:
        """The pinned file [name]: from [url], or built by [make] (an API snapshot)."""
        path = CACHE / name
        pinned = self.lock.get(name, {}).get("sha256")
        if not path.exists() or (pinned and sha256(path.read_bytes()) != pinned) or self.update_lock:
            path.write_bytes(make() if make else fetch(url))
        digest = sha256(path.read_bytes())
        if self.update_lock or not pinned:
            self.lock[name] = {"url": url, "sha256": digest}
        elif digest != pinned:
            sys.exit(f"{name} changed at the source ({url}).\n"
                     "Review the change, then run again with --update-lock to accept it.")
        return path

    def note(self, name: str, **facts: str) -> None:
        """Records a publisher's version next to the file, or stops if it changed unreviewed."""
        entry = self.lock[name]
        for key, value in facts.items():
            if key in entry and entry[key] != value and not self.update_lock:
                sys.exit(f"{name}: {key} is now {value}, locked {entry[key]}. Re-run with --update-lock.")
            entry[key] = value

    def save(self) -> None:
        LOCK.write_text(json.dumps(self.lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def hadeethenc_snapshot(ids: list[str]) -> bytes:
    """The Arabic records (numbered references, categories) and the category tree, in one file."""
    records = {}
    for start in range(0, len(ids), 100):
        batch = ",".join(ids[start:start + 100])
        for r in json.loads(fetch(f"{HADEETHENC_API}/hadeeths/multiple/?language=ar&ids={batch}")):
            records[r["id"]] = {"reference": r.get("reference") or "", "categories": r.get("categories") or []}
    categories = json.loads(fetch(f"{HADEETHENC_API}/categories/list/?language=en"))
    tree = {c["id"]: c["parent_id"] for c in categories}
    return json.dumps({"records": records, "parents": tree}, ensure_ascii=False, sort_keys=True, indent=1).encode()


# ---------------------------------------------------------------------------------------------
# The special-day items in reminders.json are written by hand: check their Arabic is the
# source's, letter for letter.

VERSE_REF = re.compile(r"^Surah .+? (\d+):(\d+)(?:–(\d+))?")
HADITH_REF = re.compile(r"Sahih (al-Bukhari|Muslim) (\d+)")
GRADE_LABEL = re.compile(r"\((sahih|hasan)[^)]*\)")


def check_special(quran: dict[tuple[int, int], str], books: dict[str, Collection]) -> list[str]:
    """Problems with the hand-written items; empty when every Arabic text is found as cited."""
    problems = []
    for r in json.loads(SPECIAL.read_text(encoding="utf-8"))["reminders"]:
        arabic = r.get("arabic")
        if not arabic:
            continue
        if r["kind"] == "verse":
            m = VERSE_REF.match(r["reference"])
            first, last = int(m.group(2)), int(m.group(3) or m.group(2))
            source = " ".join(quran[(int(m.group(1)), a)] for a in range(first, last + 1))
            # Passages carry verse-end markers between verses, which the source file doesn't.
            if re.sub(r" ﴿[٠-٩]+﴾", "", arabic) not in source:
                problems.append(f"{r['id']}: Arabic is not in Tanzil {m.group(0)}")
        elif r["kind"] == "hadith":
            # The Arabic is the wording of the first reference; later ones may differ ("similar in").
            cited = HADITH_REF.findall(r["reference"].split(";")[0])
            if not cited:
                # Outside the two books an item must name its grading; its Arabic is checked by
                # hand (docs/REMINDERS.md lists which).
                if not GRADE_LABEL.search(r["reference"].split(";")[0]):
                    problems.append(f"{r['id']}: not from al-Bukhari or Muslim and no grading in its reference")
                continue
            book, number = cited[0]
            texts = books["bukhari" if book == "al-Bukhari" else "muslim"].texts_numbered(int(number))
            wanted = " ".join(arabic_words(arabic))
            if not any(wanted in " ".join(arabic_words(t)) for t in texts):
                problems.append(f"{r['id']}: Arabic not found in {book} {number}")
    return problems


# ---------------------------------------------------------------------------------------------

def notice_lines(tanzil_notice: str, rowwad_version: str, hadeethenc_notice: str) -> list[str]:
    """The copyright and version notices every asset file carries, as the licences ask."""
    return (
        [line.lstrip("# ").rstrip() for line in tanzil_notice.splitlines() if line.strip("#= ")]
        + [f"Qur'an English: English Translation - Rowwad Translation Center, version {rowwad_version}, "
           "from QuranEnc.com (https://quranenc.com/en/browse/english_rwwad). Republished unmodified."]
        + [line.lstrip("# ").rstrip() for line in hadeethenc_notice.splitlines() if line.strip("#-")]
        + ["Hadith: HadeethEnc.com (Encyclopedia of Translated Prophetic Hadiths). Republished unmodified.",
           "Built by tools/reminders/build.py; sources and licences in docs/REMINDERS.md."]
    )


def write_review(everyday: list[dict], how: dict[str, list[str]], links: dict[str, str]) -> None:
    """
    The checklist for the scholar review: every everyday item with where to read it in full and
    where its hadith numbers came from ("matched" numbers need the closest look).
    """
    lines = ["id\tkind\treference\tnumbers\tread in full\treviewed by"]
    for r in sorted(everyday, key=lambda r: (r["id"][0], [int(n) for n in r["id"][1:].split("_")])):
        if r["kind"] == "verse":
            surah, ayah = r["id"][1:].split("_")[:2]
            link = f"https://quranenc.com/en/browse/{ROWWAD_KEY}/{surah}#{ayah}"
        else:
            link = links[r["id"]]
        reference = r["reference"].rsplit(" · ", 1)[0]
        lines.append(f"{r['id']}\t{r['kind']}\t{reference}\t{'; '.join(how.get(r['id'], []))}\t{link}\t")
    REVIEW.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_versions(versions: dict[str, str]) -> None:
    """Keeps the in-app credits (the licences ask for the version) in step with the assets."""
    text = STRINGS.read_text(encoding="utf-8")
    for name, version in versions.items():
        text, found = re.subn(
            rf'(<string name="reminder_sources_{name}_version" translatable="false">)[^<]*(</string>)',
            rf"\g<1>{version}\g<2>", text,
        )
        assert found == 1, f"{STRINGS.name} lacks reminder_sources_{name}_version"
    STRINGS.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--update-lock", action="store_true", help="accept changed sources")
    args = parser.parse_args()
    sources = Sources(args.update_lock)
    curation = json.loads(CURATION.read_text(encoding="utf-8"))
    exclude = set(curation["exclude"])

    # Qur'an
    tanzil_text = sources.get("tanzil-uthmani.txt", FILES["tanzil"]).read_text(encoding="utf-8")
    quran = {}
    for line in tanzil_text.splitlines():
        if line and not line.startswith("#"):
            s, a, text = line.split("|", 2)
            quran[(int(s), int(a))] = text
    tanzil_notice = "\n".join(l for l in tanzil_text.splitlines() if l.startswith("#"))
    version = re.search(r"Tanzil Quran Text \(Uthmani, Version ([\d.]+)\)", tanzil_notice).group(1)
    sources.note("tanzil-uthmani.txt", version=version)

    rowwad = sources.get("english_rwwad.sqlite", FILES["rowwad"])
    # QuranEnc asks republishers to follow its latest version, so a new one stops the build
    # until it is reviewed and accepted.
    listed = next(t for t in json.loads(fetch(QURANENC_LIST))["translations"] if t["key"] == ROWWAD_KEY)
    rowwad_version = listed["version"]
    sources.note("english_rwwad.sqlite", version=rowwad_version)
    with sqlite3.connect(rowwad) as db:
        english = {(s, a): t.strip() for s, a, t in db.execute("select sura, aya, translation from translations")}
    verses = [Verse(s, a, quran[(s, a)], english[(s, a)]) for (s, a) in sorted(quran)]
    passages = select_verses(verses, exclude)

    # Hadith
    hadeethenc_notice, rows = xlsx_rows(sources.get("hadeethenc-en.xlsx", FILES["hadeethenc"]))
    he_version = re.search(r"\((v[\d.]+)\)", hadeethenc_notice).group(1)
    sources.note("hadeethenc-en.xlsx", version=he_version)
    sahih = [r for r in rows if r["takhrij_ar"].strip() in BUKHARI_MUSLIM and r["grade_ar"].strip() == "صحيح"]
    # The snapshot holds every sahih item of the two books, so curation never needs a new one.
    snapshot = json.loads(sources.get(
        "hadeethenc-api.json", f"{HADEETHENC_API}/hadeeths/multiple/?language=ar",
        make=lambda: hadeethenc_snapshot(sorted({r["id"] for r in sahih}, key=int)),
    ).read_text(encoding="utf-8"))
    candidates = [r for r in sahih if len(r["hadith_text"]) <= MAX_HADITH_CHARS and f"h{r['id']}" not in exclude]
    books = {
        "bukhari": load_collection(sources.get("ara-bukhari.json", FILES["bukhari"]), "bukhari"),
        "muslim": load_collection(sources.get("ara-muslim.json", FILES["muslim"]), "muslim"),
    }

    def legal_only(categories: list[str]) -> bool:
        def under_legal(c: str | None) -> bool:
            while c:
                if c in LEGAL_CATEGORIES:
                    return True
                c = snapshot["parents"].get(c)
            return False
        return bool(categories) and all(under_legal(c) for c in categories)

    hadith, unnumbered, legal, wrong_numbers = [], 0, 0, []
    # Where each hadith number came from, for the reviewers (review.tsv).
    how: dict[str, list[str]] = collections.defaultdict(list)
    links = {f"h{r['id']}": r["link"] for r in rows}
    for r in candidates:
        record = snapshot["records"][r["id"]]
        if legal_only(record["categories"]):
            legal += 1
            continue
        numbers = {}
        for book in BUKHARI_MUSLIM[r["takhrij_ar"].strip()]:
            found, share = books[book].find(r["hadith_text_ar"])
            matched = found if share >= MATCH_THRESHOLD else None
            number = numbers_from_reference(record["reference"], book)
            # HadeethEnc's number (from the printed editions) wins, unless the book's text under
            # it is clearly another hadith: a few of its references were copied from a neighbour.
            if number is not None and books[book].contradicts(r["hadith_text_ar"], number, matched):
                wrong_numbers.append(f"h{r['id']}: {BOOK_NAMES[book]} {number}, text is at {matched}")
                number = None
            numbers[book] = number if number is not None else matched
            if numbers[book] is not None:
                how[f"h{r['id']}"].append(f"{BOOK_NAMES[book]}: {'HadeethEnc' if number is not None else 'matched'}")
        if not any(numbers.values()):
            unnumbered += 1
            continue
        hadith.append(hadith_reminder(r, numbers))

    problems = check_special(quran, books)
    everyday = rotation([verse_reminder(p) for p in passages] + hadith)
    special_ids = {r["id"] for r in json.loads(SPECIAL.read_text(encoding="utf-8"))["reminders"]}
    assert not special_ids & {r["id"] for r in everyday}, "an everyday id repeats a special-day id"

    # Write
    notice = notice_lines(tanzil_notice, rowwad_version, hadeethenc_notice)
    OUT.mkdir(exist_ok=True)
    for old in OUT.glob("everyday-*.json"):
        old.unlink()
    for n, start in enumerate(range(0, len(everyday), CHUNK_SIZE)):
        chunk = {"version": 1, "notice": notice, "reminders": everyday[start:start + CHUNK_SIZE]}
        (OUT / f"everyday-{n:03d}.json").write_text(
            json.dumps(chunk, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    library = {
        "version": 1,
        "count": len(everyday),
        "chunkSize": CHUNK_SIZE,
        "sources": {
            "tanzil": version, "rowwad": rowwad_version, "hadeethenc": he_version,
            "hadithNumbers": f"fawazahmed0/hadith-api@{HADITH_API_COMMIT}",
        },
        "notice": notice,
    }
    (OUT / "library.json").write_text(json.dumps(library, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    sources.save()
    write_review(everyday, how, links)
    write_versions({"tanzil": version, "rowwad": rowwad_version, "hadeethenc": he_version.lstrip("v")})

    size = sum(p.stat().st_size for p in OUT.glob("*.json"))
    print(f"{len(passages)} Qur'an items ({sum(len(p) > 1 for p in passages)} passages), "
          f"{len(hadith)} hadith ({len(candidates)} candidates, {legal} rulings-only, {unnumbered} without a number): "
          f"{len(everyday)} days, {size / 1024:.0f} KiB in {OUT.relative_to(ROOT)}")
    for w in wrong_numbers:
        print("HadeethEnc number replaced:", w)
    for p in problems:
        print("special-day item:", p)
    if problems:
        sys.exit(1)


if __name__ == "__main__":
    main()
