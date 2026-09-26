"""Builds the simulator's data from the screens SiteCrawlTest captured (site/sim/shots).

Each screen's buttons are linked to the screen they open, by their label. Long pages captured a
screen at a time (name-1, name-2, ...) are linked for scrolling. Output: site/sim/app/*.webp and
site/sim/crawl.js. Run from the repo root: python site/sim/build.py
"""
import glob
import json
import os
import re

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "shots")
OUT = os.path.join(HERE, "app")

TABS = {"Today": "today-1", "Adhkaar": "adhkaar-1", "Insights": "insights-1", "Settings": "settings-1"}
BACK, HOME, DONE = "@back", "@home", "@complete"

# (screen name pattern, label prefix, where it goes). The first match wins.
RULES = [
    # Today
    (r"^today", "Continue", "s0"),
    (r"^today", "22", "streak"),
    (r"^today", "REMINDER FOR TODAY", "day-reminder"),
    (r"^today", "Share", "share-card"),
    (r"^today", "FOR THIS MOMENT", "waking"),
    (r"^today", "After salah", "after-salah"),
    (r"^today", "Before sleep", "before-sleep"),
    (r"^today", "On waking", "on-waking"),
    (r"^today", "Everyday duas", "everyday-1"),
    (r"^today", "Next ", "salah-reminders-1"),
    (r"^today", "Finish phone setup", None),
    # Adhkaar tab
    (r"^adhkaar", "Morning", "shelf-morning-1"),
    (r"^adhkaar", "Evening", "shelf-evening-1"),
    (r"^adhkaar", "After salah", "after-salah"),
    (r"^adhkaar", "Before sleep", "before-sleep"),
    (r"^adhkaar", "On waking", "on-waking"),
    (r"^adhkaar", "Everyday duas", "everyday-1"),
    (r"^adhkaar", "Favourites", "favourites"),
    (r"^adhkaar", "My duas", "my-duas"),
    (r"^shelf-morning", "Begin", "s0"),
    (r"^shelf-morning-1$", "1 Ayat al-Kursi", "shelf-morning-open"),
    (r"^shelf-morning-1$", "Add to favourites", "shelf-morning-fav"),
    (r"^shelf-morning-(open|fav)$", "", "shelf-morning-1"),
    (r"^my-duas", "Write a dua", "dua-editor-1"),
    # Settings
    (r"^settings", "Guide", "guide-1"),
    (r"^settings", "Test kit", "test-kit-1"),
    (r"^settings", "Contact us", "contact-1"),
    (r"^settings", "Gentle", "settings-1"),
    (r"^settings", "Full screen", "settings-full"),
    (r"^settings", "Lockdown", "settings-lockdown"),
    (r"^settings", "Salah reminders", "salah-reminders-1"),
    (r"^salah-reminders", "6:35 PM", "salah-edit"),
    (r"^salah-edit$", "", "salah-reminders-1"),
    # Session (Full screen)
    (r"^s0$", "0 of 1", "s1"),
    (r"^s1$", "0 of 3", "s2"),
    (r"^s2$", "1 of 3", "s3"),
    (r"^s3$", "2 of 3", "s4"),
    (r"^s4$", "0 of 3", DONE),
    (r"^s0$", "Add to favourites", "s-fav"),
    (r"^s-fav$", "Remove from favourites", "s0"),
    (r"^s-fav$", "0 of 1", "s1"),
    (r"^s\d$", "Reading options", "s-read"),
    (r"^s\d$", "Share", "s-share"),
    (r"^s\d$", "Take a break", "b1"),
    (r"^s", "Close", HOME),
    (r"^b1$", "20", "b2"),
    (r"^b[12]$", "Keep reading", "s0"),
    (r"^b2$", "Pause for 20", "b3"),
    (r"^b3$", "Stay and finish", "s0"),
    (r"^(s-read|s-share)$", "", "s0"),
]
# Screens shown over another (sheets, dialogs, collections): anything unlinked goes back.
OVERLAYS = r"^(streak|day-reminder|share-card|waking|after-salah|before-sleep|on-waking|favourites|everyday|my-duas|dua-editor|guide|test-kit|contact|salah-reminders|shelf-)"


def clean(s):
    return re.sub("[⁦-⁩]", "", s or "")


def target(name, label):
    L = clean(label)
    session = re.match(r"^(s\d|s-|b\d)", name)
    if L in TABS and not session:
        return TABS[L]
    for pat, prefix, to in RULES:
        if re.search(pat, name) and L.startswith(prefix):
            return to
    if L in ("Back", "Close", "Cancel", "Done", "OK") or L.startswith("Close"):
        return BACK
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    names = sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(SHOTS, "*.png")))
    states = {}
    for n in names:
        img = Image.open(os.path.join(SHOTS, n + ".png")).convert("RGB")
        img.save(os.path.join(OUT, n + ".webp"), "WEBP", quality=80, method=6)
        hot = json.load(open(os.path.join(SHOTS, n + ".json"), encoding="utf-8"))
        for h in hot:
            h["to"] = target(n, h["l"])
            if h["to"] is None and re.search(OVERLAYS, n) and clean(h["l"]) in ("", "Close", "Back"):
                h["to"] = BACK
        m = re.match(r"^(.*)-(\d+)$", n)
        scroll = up = None
        if m:
            base, i = m.group(1), int(m.group(2))
            if f"{base}-{i + 1}" in names:
                scroll = f"{base}-{i + 1}"
            if i > 1:
                up = f"{base}-{i - 1}"
        states[n] = {"id": n, "hot": hot, "scroll": scroll, "up": up}
    missing = sorted({h["to"] for s in states.values() for h in s["hot"] if h["to"] and not h["to"].startswith("@") and h["to"] not in states})
    with open(os.path.join(HERE, "crawl.js"), "w", encoding="utf-8") as f:
        f.write("window.SIM_CRAWL=" + json.dumps({"app": states}, ensure_ascii=False) + ";\n")
    size = sum(os.path.getsize(p) for p in glob.glob(os.path.join(OUT, "*.webp")))
    linked = sum(1 for s in states.values() for h in s["hot"] if h["to"])
    print(f"{len(states)} screens, {linked} links, {size / 1e6:.1f} MB; links to screens not captured: {missing}")


if __name__ == "__main__":
    main()
