"""Builds the Qur'an assets for the app (app/src/main/assets/quran/) from the King Fahd Glorious
Qur'an Printing Complex's own files:

  * the 604 page fonts of the Madinah mushaf, 1421 AH print (QCF2001..QCF2604), used here only to
    measure: every word is drawn at its printed width, so each page's lines can be recovered exactly
    (every line of a page fills the same width). They are NOT shipped in the app.
  * the Uthmanic Hafs font (v22) and its text, shipped unchanged: one 300 KB font for the whole
    mushaf, drawn line by line with the printed line breaks.

Output: mushaf.txt (the lines of every page), quran.txt (the text, one ayah per line) and the font.
Run: python tools/quran/build.py   (downloads into tools/quran/.cache once)
"""
import json
import math
import os
import shutil
import sys
import urllib.request
from pathlib import Path

from fontTools.ttLib import TTFont

HERE = Path(__file__).parent
CACHE = HERE / ".cache"
OUT = HERE.parent.parent / "app/src/main/assets/quran"
FONT_OUT = HERE.parent.parent / "app/src/main/res/font/uthmanic_hafs.ttf"
REPO = "https://raw.githubusercontent.com/nuqayah/qpc-fonts/master"
AYAHS = [7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77,
         227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55,
         78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25,
         22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6]
REFS = [(s + 1, a + 1) for s, n in enumerate(AYAHS) for a in range(n)]
# Pause marks the Complex sometimes draws as a glyph of their own after the word.
MARKS = {chr(c) for c in range(0x06D6, 0x06DD)} | {"۩"}
# Pages 1 and 2 are set in a smaller frame whose lines narrow towards the top and bottom, so equal
# widths don't find their breaks; their lines are written out here from the printed mushaf.
OPENING_LINES = {1: 7, 2: 6}
OPENING = {
    1: ["H1", "L 0:0-3e", "L 1:0-3e", "L 2:0-1e 3:0-2e", "L 4:0-3e 5:0-0", "L 5:1-2e 6:0-2", "L 6:3-7", "L 6:8-8e"],
    2: ["H2", "B", "L 7:0-0e 8:0-5", "L 8:6-6e 9:0-3", "L 9:4-7e 10:0-0", "L 10:1-7", "L 10:8-11e 11:0-1", "L 11:2-7e"],
}


def fetch(url: str, path: Path):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(url, path)
    return path


def page_font(p: int) -> TTFont:
    return TTFont(fetch(f"{REPO}/mushaf-v2/QCF2{p:03d}.ttf", CACHE / f"v2/QCF2{p:03d}.ttf"))


def load():
    hafs = json.loads(fetch(f"{REPO}/text-mushafs/UthmanicHafs_V22/UthmanicHafs%20v22.json", CACHE / "hafs_v22.json").read_text(encoding="utf-8"))
    codes = [l.split(",", 1) for l in fetch(f"{REPO}/mushaf-v2.txt", CACHE / "mushaf-v2.txt").read_text(encoding="utf-8").splitlines()]
    assert len(hafs) == len(codes) == 6236
    return [h[0] for h in hafs], codes


def glyph_stream():
    """Every page's glyphs in reading order: the Complex numbers a page's words consecutively."""
    stream = []
    for p in range(1, 605):
        f = page_font(p)
        cmap, hmtx = f.getBestCmap(), f["hmtx"]
        # A page's own words run from U+FC41 without a gap; the codes around them are shared.
        code = 0xFC41
        while code in cmap:
            stream.append((p, code, hmtx[cmap[code]][0]))
            code += 1
    return stream


def assign(stream, codes):
    """How many glyphs each ayah has, checked against the stream; gaps in the code list are filled from it."""
    counts = []
    pos = 0

    def ayah_codes(i):
        t = codes[i][1]
        return None if t == "None" else [ord(c) for c in t if c != " "]

    def matches(at, cs):
        return at + len(cs) <= len(stream) and all(stream[at + k][1] == c for k, c in enumerate(cs))

    skip = set()
    for i in range(6236):
        if i in skip:
            continue
        cs = ayah_codes(i)
        if cs is not None and matches(pos, cs) and (i == 6235 or ayah_codes(i + 1) is None or matches(pos + len(cs), ayah_codes(i + 1))):
            counts.append(len(cs))
            pos += len(cs)
            continue
        # Unknown or wrong: this ayah runs up to where the next known ayah begins.
        j = i + 1
        while j < 6236 and ayah_codes(j) is None:
            j += 1
        nxt = ayah_codes(j) if j < 6236 else None
        if nxt is None:
            n = len(stream) - pos
        else:
            n = next(k for k in range(1, 400) if matches(pos + k, nxt))
        if j > i + 1:
            # Several unknown in a row (e.g. حمٓ then عٓسٓقٓ): each has its words and its number;
            # the last takes what is left, and it must come out the same.
            words = [len([w for w in HAFS[k].split(" ") if w]) + 1 for k in range(i, j)]
            if sum(words) != n:
                raise SystemExit(f"can't split {REFS[i]}..{REFS[j - 1]}: {n} glyphs for {words}")
            for k, c in zip(range(i, j), words):
                counts.append(c)
                print(f"  filled {REFS[k]} from the page fonts: {c} glyphs")
            pos += n
            skip.update(range(i + 1, j))
            continue
        counts.append(n)
        pos += n
        print(f"  filled {REFS[i]} from the page fonts: {n} glyphs")
    assert pos == len(stream), (pos, len(stream))
    return counts


def break_lines(widths, n, forced):
    """Splits a page's glyphs into n lines of as equal width as possible; a line never crosses a forced break."""
    m = len(widths)
    pre = [0]
    for w in widths:
        pre.append(pre[-1] + w)
    target = pre[-1] / n
    inf = float("inf")
    best = [[inf] * (m + 1) for _ in range(n + 1)]
    back = [[0] * (m + 1) for _ in range(n + 1)]
    best[0][0] = 0
    for k in range(1, n + 1):
        for j in range(1, m + 1):
            lo = max((b for b in forced if b < j), default=0)
            for i in range(lo, j):
                if best[k - 1][i] < inf and (i == lo or i not in forced or True):
                    if any(i < b < j for b in forced):
                        continue
                    c = best[k - 1][i] + (pre[j] - pre[i] - target) ** 2
                    if c < best[k][j]:
                        best[k][j] = c
                        back[k][j] = i
    if best[n][m] == inf:
        return None, None
    out, j = [], m
    for k in range(n, 0, -1):
        i = back[k][j]
        out.append((i, j))
        j = i
    out.reverse()
    worst = max(abs((pre[j] - pre[i]) / target - 1) for i, j in out)
    return out, worst


def align_words(words, glyph_widths, word_widths):
    """Which glyphs make each word (the last glyph, the ayah's number, is left out). A word takes one
    glyph, plus one for each pause mark the Complex drew apart; widths decide which."""
    n, m = len(glyph_widths), len(words)
    extra = [sum(1 for c in w if c in MARKS) for w in words]
    scale = sum(glyph_widths) / max(1, sum(word_widths))
    inf = float("inf")
    best = [[inf] * (n + 1) for _ in range(m + 1)]
    back = [[0] * (n + 1) for _ in range(m + 1)]
    best[0][0] = 0
    for i in range(m):
        for j in range(n + 1):
            if best[i][j] == inf:
                continue
            # A few ayahs carry a glyph the text has no mark for (a sajdah line), so a word may take
            # up to two glyphs more than its marks, at a cost, and widths decide.
            for take in range(1, extra[i] + 4):
                if j + take > n:
                    break
                gw = sum(glyph_widths[j:j + take])
                c = best[i][j] + abs(math.log(max(gw, 1) / max(word_widths[i] * scale, 1))) + 0.5 * max(0, take - 1 - extra[i])
                if c < best[i + 1][j + take]:
                    best[i + 1][j + take] = c
                    back[i + 1][j + take] = take
    if best[m][n] == inf:
        return None
    takes, j = [], n
    for i in range(m, 0, -1):
        t = back[i][j]
        takes.append(t)
        j -= t
    takes.reverse()
    owner = []
    for i, t in enumerate(takes):
        owner += [i] * t
    return owner


def main():
    CACHE.mkdir(exist_ok=True)
    global HAFS
    hafs, codes = load()
    HAFS = hafs
    print("reading the 604 page fonts…")
    stream = glyph_stream()
    counts = assign(stream, codes)
    hafs_font = TTFont(fetch(f"{REPO}/text-mushafs/UthmanicHafs_V22/uthmanic_hafs_v22.ttf", CACHE / "uthmanic_hafs_v22.ttf"))
    hcmap, hmtx = hafs_font.getBestCmap(), hafs_font["hmtx"]

    def word_width(w):
        return sum(hmtx[hcmap[ord(c)]][0] for c in w if ord(c) in hcmap)

    # Every glyph: (page, width, ayah index, word index in the ayah or -1 for the ayah's number).
    glyphs = []
    stream_codes = {}
    pos = 0
    for i, n in enumerate(counts):
        words = [w for w in hafs[i].split(" ") if w]
        ws = [g[2] for g in stream[pos:pos + n]]
        owner = align_words(words, ws[:-1], [word_width(w) for w in words])
        if owner is None:
            raise SystemExit(f"can't match {REFS[i]}: {n} glyphs, {len(words)} words")
        for k in range(n):
            g = (stream[pos + k][0], stream[pos + k][2], i, owner[k] if k < n - 1 else -1)
            glyphs.append(g)
            stream_codes[id(g)] = stream[pos + k][1]
        pos += n

    lines_out = []
    debug = {}
    report = []
    for p in range(1, 605):
        page = [g for g in glyphs if g[0] == p]
        starts = sorted({g[2] for g in page if REFS[g[2]][1] == 1 and g[3] in (0, -1)})
        starts = [i for i in starts if page[0][2] != i or True]
        first_glyph = {}
        for idx, g in enumerate(page):
            first_glyph.setdefault(g[2], idx)
        surah_starts = [i for i in starts if first_glyph[i] == min(k for k, g in enumerate(page) if g[2] == i)]
        heads = [REFS[i][0] for i in surah_starts]
        header_lines = sum(1 if s in (1, 9) else 2 for s in heads)
        n = OPENING_LINES.get(p, 15 - header_lines)
        forced = {first_glyph[i] for i in surah_starts if first_glyph[i] > 0}
        breaks, worst = break_lines([g[1] for g in page], n, forced)
        if breaks is None:
            raise SystemExit(f"page {p}: can't make {n} lines")
        if p not in OPENING_LINES:
            report.append((worst, p))
        debug[p] = [[stream_codes[id(g)] for g in page[a:b]] for a, b in breaks]
        lines_out.append(f"P{p}")
        if p in OPENING:
            # The opening pages' breaks, from their written-out lines.
            heads_out = [l for l in OPENING[p] if not l.startswith("L ")]
            breaks, idx = [], 0
            for spec in (l for l in OPENING[p] if l.startswith("L ")):
                members = set()
                for tok in spec[2:].split(" "):
                    ay, rest = tok.split(":")
                    end = rest.endswith("e")
                    rest = rest.rstrip("e")
                    if rest:
                        w0, w1 = map(int, rest.split("-"))
                        members |= {(int(ay), w) for w in range(w0, w1 + 1)}
                    if end:
                        members.add((int(ay), -1))
                start = idx
                while idx < len(page) and (page[idx][2], page[idx][3]) in members:
                    idx += 1
                breaks.append((start, idx))
            assert idx == len(page), f"page {p}: the written lines don't cover the page"
            lines_out += heads_out
        for a, b in breaks:
            first = page[a]
            if p not in OPENING and first[2] in surah_starts and first[3] in (0, -1) and REFS[first[2]][1] == 1:
                s = REFS[first[2]][0]
                lines_out.append(f"H{s}")
                if s not in (1, 9):
                    lines_out.append("B")
            # A line: the page font's glyphs in reading order, then which ayah each run of them is
            # ("5908x10,5909x4": ten glyphs of ayah 5908, then four of 5909), for taps.
            codes = "".join(chr(stream_codes[id(g)]) for g in page[a:b])
            runs = []
            for g in page[a:b]:
                if runs and runs[-1][0] == g[2]:
                    runs[-1][1] += 1
                else:
                    runs.append([g[2], 1])
            lines_out.append("L " + codes + "|" + ",".join(f"{ay}x{n}" for ay, n in runs))
    report.sort(reverse=True)
    print("pages over 3%:", len([1 for w, p in report if w > 0.03]), "over 6%:", [p for w, p in report if w > 0.06])
    print("widest line deviation, worst pages:", [(p, round(w, 3)) for w, p in report[:12]])

    (CACHE / "debug_lines.json").write_text(json.dumps(debug))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "mushaf.txt").write_text("\n".join(lines_out) + "\n", encoding="utf-8")
    (OUT / "quran.txt").write_text("\n".join(hafs) + "\n", encoding="utf-8")
    shutil.copyfile(CACHE / "uthmanic_hafs_v22.ttf", FONT_OUT)
    # Surah headings and the basmala as printed: the Complex's heading font, shipped unchanged.
    shutil.copyfile(fetch(f"{REPO}/mushaf-v2/QCF2BSML.ttf", CACHE / "v2/QCF2BSML.ttf"), FONT_OUT.with_name("qcf_bsml.ttf"))
    write_meanings()
    print("wrote", OUT, "and", FONT_OUT)


def write_meanings():
    """The English meaning of every ayah, with its footnotes, from QuranEnc's Rowwad Translation
    Center edition (the one the reminders use; tools/reminders fetches and checks it). Republished
    unmodified, as QuranEnc requires, and credited in the app."""
    import sqlite3
    db = HERE.parent / "reminders/.cache/english_rwwad.sqlite"
    if not db.exists():
        raise SystemExit("run tools/reminders/build.py first: it downloads the QuranEnc translation")
    lock = json.loads((HERE.parent / "reminders/sources.lock.json").read_text(encoding="utf-8"))
    version = lock["english_rwwad.sqlite"]["version"]
    rows = sqlite3.connect(db).execute("select sura, aya, translation, footnotes from translations order by sura, aya").fetchall()
    assert len(rows) == 6236
    out = [f"# English Translation - Rowwad Translation Center, QuranEnc.com, version {version}. Republished unmodified."]
    # One ayah per line: the meaning, a tab, then its footnotes with their line breaks written as \n.
    for sura, aya, text, notes in rows:
        clean = lambda t: (t or "").replace("\r", "").replace("\t", " ").replace("\n", "\\n").strip()
        out.append(f"{clean(text)}\t{clean(notes)}")
    (OUT / "en.txt").write_text("\n".join(out) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
