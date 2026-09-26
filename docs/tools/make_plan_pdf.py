from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import mm
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
                                KeepTogether, ListFlowable, ListItem, PageBreak)
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon

import os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "Adhkaar-Reminder-Android-Plan.pdf")

GREEN = colors.HexColor("#0F5132")
GREEN_LIGHT = colors.HexColor("#E8F3EC")
GOLD = colors.HexColor("#B8860B")
INK = colors.HexColor("#1F2328")
MUTED = colors.HexColor("#57606A")
RULE = colors.HexColor("#D0D7DE")
RED_LIGHT = colors.HexColor("#FBEAEA")
RED = colors.HexColor("#9A2A2A")

base = dict(fontName="Helvetica", textColor=INK)
H_TITLE = ParagraphStyle("t", fontName="Helvetica-Bold", fontSize=26, leading=31, textColor=GREEN)
H_SUB = ParagraphStyle("s", fontName="Helvetica", fontSize=12.5, leading=17, textColor=MUTED)
H1 = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=15, leading=19, textColor=GREEN,
                    spaceBefore=14, spaceAfter=6, keepWithNext=1)
H2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=11.5, leading=15, textColor=INK,
                    spaceBefore=8, spaceAfter=3)
BODY = ParagraphStyle("b", fontSize=10, leading=14.5, spaceAfter=6, **base)
SMALL = ParagraphStyle("sm", fontSize=8.8, leading=12, **base)
SMALL_B = ParagraphStyle("smb", parent=SMALL, fontName="Helvetica-Bold")
CELL_H = ParagraphStyle("ch", fontName="Helvetica-Bold", fontSize=9, leading=12, textColor=colors.white)
CODE = ParagraphStyle("code", fontName="Courier", fontSize=8.8, leading=12, textColor=INK)
CALLOUT = ParagraphStyle("co", fontSize=9.8, leading=14, **base)


def p(text, style=BODY):
    return Paragraph(text, style)


def bullets(items, style=BODY):
    return ListFlowable([ListItem(p(i, style), leftIndent=12) for i in items],
                        bulletType="bullet", start="•", bulletFontSize=9, leftIndent=12, bulletColor=GREEN,
                        spaceBefore=0, spaceAfter=4)


def numbered(items):
    return ListFlowable([ListItem(p(i), leftIndent=16) for i in items],
                        bulletType="1", bulletFontName="Helvetica-Bold", bulletColor=GREEN,
                        leftIndent=16, spaceAfter=4)


def table(header, rows, widths, zebra=True):
    data = [[p(h, CELL_H) for h in header]]
    for r in rows:
        data.append([p(c, SMALL_B if i == 0 else SMALL) for i, c in enumerate(r)])
    t = Table(data, colWidths=widths, repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), GREEN),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
    ]
    if zebra:
        for i in range(1, len(data)):
            if i % 2 == 0:
                style.append(("BACKGROUND", (0, i), (-1, i), colors.HexColor("#F6F8FA")))
    t.setStyle(TableStyle(style))
    return t


def callout(text, bg=GREEN_LIGHT, bar=GREEN):
    t = Table([[p(text, CALLOUT)]], colWidths=[170 * mm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("LINEBEFORE", (0, 0), (0, -1), 3, bar),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ]))
    return t


def arrow(d, x1, y1, x2, y2, label=None, color=MUTED):
    d.add(Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=1.1))
    import math
    a = math.atan2(y2 - y1, x2 - x1)
    s = 5
    d.add(Polygon([x2, y2,
                   x2 - s * math.cos(a - 0.45), y2 - s * math.sin(a - 0.45),
                   x2 - s * math.cos(a + 0.45), y2 - s * math.sin(a + 0.45)],
                  fillColor=color, strokeColor=color))
    if label:
        if y1 == y2:
            d.add(String((x1 + x2) / 2, y1 + 4, label, fontName="Helvetica-Oblique",
                         fontSize=7, fillColor=MUTED, textAnchor="middle"))
        else:
            d.add(String(x1 + 4, (y1 + y2) / 2 - 2, label, fontName="Helvetica-Oblique",
                         fontSize=7, fillColor=MUTED))


def box(d, x, y, w, h, title, sub=None, fill=GREEN_LIGHT, stroke=GREEN):
    d.add(Rect(x, y, w, h, rx=5, ry=5, fillColor=fill, strokeColor=stroke, strokeWidth=0.9))
    ty = y + h / 2 + (3 if sub else -3)
    d.add(String(x + w / 2, ty, title, fontName="Helvetica-Bold", fontSize=8.5, fillColor=INK,
                 textAnchor="middle"))
    if sub:
        d.add(String(x + w / 2, y + h / 2 - 9, sub, fontName="Helvetica", fontSize=7,
                     fillColor=MUTED, textAnchor="middle"))


def architecture():
    W, H = 170 * mm, 250
    d = Drawing(W, H)
    bw, bh = 120, 34
    # column x positions
    cx1, cx2, cx3 = 8, 180, 352
    top = H - bh - 6
    box(d, cx1, top, bw, bh, "BootReceiver", "boot, time change, update")
    box(d, cx2, top, bw, bh, "AlarmScheduler", "setAlarmClock (exact)")
    box(d, cx3, top, bw, bh, "SessionAlarmReceiver", "fires at adhkaar time")
    arrow(d, cx1 + bw, top + bh / 2, cx2, top + bh / 2, "reschedule")
    arrow(d, cx2 + bw, top + bh / 2, cx3, top + bh / 2, "alarm")

    row2 = top - 62
    box(d, cx1, row2, bw, bh, "Gentle", "high-priority notification", fill=colors.white)
    box(d, cx2, row2, bw, bh, "Full screen", "full-screen intent", fill=colors.white)
    box(d, cx3, row2, bw, bh, "Lockdown", "EnforcementService (FGS)",
        fill=colors.HexColor("#FFF4DB"), stroke=GOLD)
    mid = cx3 + bw / 2
    arrow(d, mid, top, cx1 + bw / 2, row2 + bh)
    arrow(d, mid, top, cx2 + bw / 2, row2 + bh)
    arrow(d, mid, top, mid, row2 + bh)
    d.add(String(mid + 4, top - 12, "by strictness level", fontName="Helvetica-Oblique",
                 fontSize=7, fillColor=MUTED))

    row3 = row2 - 62
    box(d, cx2, row3, bw, bh, "SessionActivity", "over lock screen, tap counters")
    box(d, cx3, row3, bw, bh, "Block overlay", "covers any disallowed app",
        fill=colors.HexColor("#FFF4DB"), stroke=GOLD)
    arrow(d, cx2 + bw / 2, row2, cx2 + bw / 2, row3 + bh, "opens")
    arrow(d, mid, row2, mid, row3 + bh, "fg app not allowed")
    arrow(d, cx3, row3 + bh / 2, cx2 + bw, row3 + bh / 2, "relaunch")

    row4 = row3 - 56
    box(d, cx2, row4, bw, bh, "SessionState.complete()", "stop service, schedule next")
    arrow(d, cx2 + bw / 2, row3, cx2 + bw / 2, row4 + bh, "all counters done")
    box(d, cx3, row4, bw, bh, "Watchdog alarm", "every 5 min, restarts FGS",
        fill=colors.white, stroke=GOLD)
    arrow(d, mid, row4 + bh, mid, row3, color=GOLD)
    box(d, cx1, row3, bw, bh, "ForegroundAppDetector", "UsageStats, ~0.6 s poll",
        fill=colors.white, stroke=GOLD)
    box(d, cx1, row4, bw, bh, "AllowList", "dialer, SMS, in-call, us",
        fill=colors.white, stroke=GOLD)
    d.add(String(cx1 + bw / 2, row4 - 11, "both used by EnforcementService", fontName="Helvetica-Oblique",
                 fontSize=7, fillColor=GOLD, textAnchor="middle"))
    return d


def on_page(canvas, doc):
    canvas.saveState()
    w, h = A4
    canvas.setFillColor(GREEN)
    canvas.rect(0, h - 6, w, 6, stroke=0, fill=1)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(20 * mm, 12 * mm, "Adhkaar Reminder - Android implementation plan - draft 0.1")
    canvas.drawRightString(w - 20 * mm, 12 * mm, f"Page {doc.page}")
    canvas.restoreState()


story = []
story += [
    Spacer(1, 18 * mm),
    p("Adhkaar Reminder", H_TITLE),
    Spacer(1, 3),
    p("Android implementation plan", ParagraphStyle("t2", parent=H_TITLE, fontSize=17, leading=22,
                                                   textColor=INK)),
    Spacer(1, 8),
    p("An open-source app that opens the morning and evening adhkaar on your phone at the right "
      "time and, if you choose, keeps other apps closed until you've completed them.", H_SUB),
    Spacer(1, 6),
    p("Draft 0.1 - September 2026 - Kotlin + Jetpack Compose - minSdk 26 / targetSdk 36", SMALL),
    Spacer(1, 10),
    callout("<b>Why this document exists.</b> The project is going open source. This plan is here so "
            "developers can see what we are building and how, what has been decided, "
            "and where help is most needed before the first release. Everything below is open "
            "to discussion."),
]

story += [p("1. The idea", H1),
          p("Most Muslims know the morning and evening adhkaar matter. The hard part is remembering them "
            "every day. Reminder notifications are easy to swipe away. This app treats the adhkaar "
            "like an alarm: at the chosen time it opens on its own. In the strictest mode, the rest of the "
            "phone stays blocked until every dhikr has been recited and counted, while calls "
            "and emergency access keep working."),
          p("2. Where we are now", H1),
          bullets([
              "Plan written, stack chosen, adhkaar content drafted (20 adhkaar with Hisn al-Muslim references).",
              "Android app built: all five core milestones written, 35 unit and screenshot tests passing. "
              "<b>Nothing has been tested on a real device yet</b>, which is the most useful help right now.",
              "iOS comes later. Apple's Screen Time API (FamilyControls, ManagedSettings, DeviceActivity) can "
              "block other apps. It needs an entitlement from Apple, so that request will be filed early.",
          ])]

story += [p("3. Stack and principles", H1),
          table(["Decision", "Choice", "Reason"], [
              ["Language / UI", "Kotlin + Jetpack Compose (native)",
               "The core features (exact alarms, full-screen intents, overlays, UsageStats) are native "
               "APIs. A cross-platform layer would only add risk."],
              ["Content", "Plain JSON asset",
               "Can be reused unchanged by the iOS app. Easy to review and translate."],
              ["Backend", "None",
               "Works offline, needs no account, collects no data."],
              ["Prayer times", "adhan (Batoul Apps)",
               "Calculated on the phone, with a choice of calculation methods and madhab (for Asr)."],
              ["Min / target SDK", "26 / 36",
               "Covers almost every active Android phone. Targets the SDK level Google Play requires."],
          ], [32 * mm, 45 * mm, 93 * mm])]

story += [p("4. Strictness levels", H1),
          p("The user chooses how strict the app is. The default and recommended level is <b>Lockdown</b>. "
            "Gentle and Full screen are there for anyone who wants less."),
          table(["Level", "What happens at adhkaar time"], [
              ["Gentle", "A high-priority notification."],
              ["Full screen", "Alarm-style: the app opens over the lock screen and turns the screen on. "
                              "The user can leave. A reminder notification stays until they finish."],
              ["Lockdown", "Full screen, plus every other app is covered by a block screen until the "
                           "adhkaar are completed."],
          ], [32 * mm, 138 * mm])]

story += [p("5. Safety rules (cannot be turned off)", H1),
          callout("A lock that stops someone reaching emergency help is dangerous, and Google "
                  "Play would reject it. These rules are part of the design, not optional extras.",
                  bg=RED_LIGHT, bar=RED),
          Spacer(1, 6),
          bullets([
              "<b>Calls always work.</b> Enforcement pauses during any call (cellular, WhatsApp and similar), "
              "detected through the audio mode. No phone permission is needed.",
              "<b>Never blocked:</b> the default dialer and SMS apps, the in-call screen, the emergency dialer "
              "and system permission dialogs. The block screen has an <b>Emergency call</b> button.",
              "<b>Hold-to-skip</b> is always available: a 5-second hold, with a weekly number of skips (default 2). "
              "After those run out, skipping needs a 30-second hold. The goal is friction, "
              "never a trap.",
              "<b>Lockdown ends automatically</b> after a maximum duration (default 3 hours). After that the "
              "app goes back to showing a reminder.",
          ])]

story += [KeepTogether([p("6. Architecture", H1),
          p("Everything runs on the phone, driven by alarms. Gold boxes run only in Lockdown mode."),
          architecture()]), Spacer(1, 6),
          table(["Package", "Responsibility"], [
              ["data/", "Loads the adhkaar JSON. Stores settings (SharedPreferences, so receivers can read them "
                        "synchronously) and SessionState (the pending session, counter progress, skip allowance)."],
              ["schedule/", "SessionTimeCalculator (pure Kotlin, unit tested), PrayerTimes wrapper, "
                            "AlarmScheduler, and the alarm, boot and time-change receivers."],
              ["enforce/", "EnforcementService, ForegroundAppDetector, AllowList (pure, unit tested) and "
                           "BlockOverlay (plain Views, no Compose inside the service)."],
              ["session/", "SessionLauncher: decides what happens at adhkaar time for the chosen strictness level."],
              ["ui/", "Compose screens: Home (with health check), Session, Settings, Permissions."],
              ["oem/", "Deep links to auto-start and battery settings for each phone brand."],
          ], [28 * mm, 142 * mm])]

story += [p("7. Why each mechanism", H1),
          table(["Mechanism", "Why", "Caveat"], [
              ["setAlarmClock", "The most reliable exact alarm. Doze and most OEM battery savers leave it alone.",
               "Needs the exact-alarm permission (user-granted on Android 14+)."],
              ["Full-screen intent", "The same path alarm clocks use to open over the lock screen.",
               "Android 14+: this is a special permission the user may need to grant, and Play "
               "declares it for alarm apps."],
              ["Display over other apps", "The block overlay stops touches reaching the app underneath. "
                                          "Holding this permission also lets the app open its screen "
                                          "from the background.",
               "From Android 15 that also requires one of our overlays to be on screen, which the block overlay is."],
              ["Usage Access", "Tells the service which app is in front.",
               "Chosen instead of an Accessibility Service, which Google Play restricts heavily."],
              ["Foreground service (specialUse)", "Keeps enforcement running while a Lockdown session is pending.",
               "Needs a Play Console declaration. Stops as soon as the adhkaar is done."],
              ["Watchdog alarm", "Restarts the service every 5 minutes if a phone brand kills it.",
               "Only scheduled while a Lockdown session is pending."],
          ], [36 * mm, 72 * mm, 62 * mm])]

story += [KeepTogether([p("8. Onboarding and health check", H1),
          p("Onboarding is a checklist. Each item shows whether it's granted and has a button that opens the "
            "right settings screen. On Home, a health check shows a red banner when the chosen level is missing "
            "a permission it needs, and says exactly what will not work."),
          numbered([
              "Notifications",
              "Exact alarms",
              "Full-screen notifications (Android 14+)",
              "Display over other apps (Lockdown only)",
              "Usage access (Lockdown only)",
              "Ignore battery optimisation",
              "Auto-start for each phone brand: Xiaomi, Oppo, Vivo, Huawei, Honor, Samsung, Tecno/Infinix, OnePlus, Asus",
              "Location, used once for prayer times (optional; fixed times work without it)",
          ])])]

story += [p("9. Session timing and experience", H1),
          bullets([
              "<b>Morning:</b> after Fajr + offset (default 15 min), or a fixed time.",
              "<b>Evening:</b> after Asr + offset (default 15 min), or a fixed time.",
              "If the user has already done the adhkaar that day, the alarm for that session is skipped.",
              "One card per dhikr: Arabic, transliteration, translation, source, and a large tap counter. "
              "The session only counts as complete when every counter reaches its target.",
              "Progress is saved on every tap, so if the app is killed the user continues where they left off.",
              "The screen stays on during a session. In Lockdown, Back is blocked and Home is covered by the overlay.",
          ])]

story += [p("10. Milestones", H1),
          table(["#", "Milestone", "Status"], [
              ["1", "Project scaffold, adhkaar content, Session screen with counters", "Built"],
              ["2", "Settings, time calculation, alarms, boot handling, full-screen launch", "Built"],
              ["3", "Lockdown: service, overlay, foreground detection, watchdog", "Built, untested on device"],
              ["4", "Onboarding checklist, OEM guidance, health banner", "Built"],
              ["5", "Unit tests + JVM screenshot tests (35 passing)", "Done"],
              ["6", "Real-device testing, audio, Play listing and policy declarations", "Help wanted"],
          ], [10 * mm, 112 * mm, 48 * mm])]

story += [p("11. Known risks", H1),
          table(["Risk", "Impact", "Mitigation"], [
              ["OEM battery killers (Xiaomi, Oppo, Vivo, Tecno, Huawei)",
               "Alarms or the service silently stop",
               "setAlarmClock, a watchdog alarm, per-brand onboarding, and testing on real devices"],
              ["Google Play policy", "Rejection over specialUse, full-screen intent or usage access",
               "Clear in-app disclosure, a narrow purpose, and the service only runs during a session"],
              ["The user revokes permissions", "Lockdown degrades to Full screen",
               "A health banner explains what stopped working. This is acceptable by design."],
              ["Content errors", "Wrong Arabic text or references",
               "Review by a qualified person before release (see below)"],
          ], [45 * mm, 50 * mm, 75 * mm])]

story += [p("12. Where you can help", H1),
          table(["Area", "What's needed"], [
              ["Device testing", "Run test builds on Samsung, Xiaomi/Redmi/POCO, Oppo/Realme, Vivo, Tecno/Infinix, "
                                 "Huawei and Pixel. Report whether alarms fire and whether Lockdown survives."],
              ["Content review", "A student of knowledge or scholar to proofread the Arabic, "
                                 "transliterations and references against Hisn al-Muslim."],
              ["Translations", "Urdu, Hausa, Yoruba, Indonesian, Malay, Turkish, French, Bengali, Arabic UI, "
                               "and more."],
              ["Audio", "Recitation audio for each dhikr, with clear licensing."],
              ["iOS", "A Swift/SwiftUI port using FamilyControls, DeviceActivity and AlarmKit, plus help with the "
                      "Family Controls entitlement request."],
              ["Accessibility", "TalkBack support, large text, RTL layout checks."],
          ], [32 * mm, 138 * mm])]

story += [p("13. Open-source setup (proposed)", H1),
          bullets([
              "<b>License: still to be decided.</b> GPL-3.0 means any fork or copy must stay open source. "
              "Apache-2.0 is more permissive and lets others build closed apps from the code. "
              "The adhkaar JSON carries its sources and can be licensed separately (e.g. CC BY 4.0).",
              "Add CONTRIBUTING.md, a device test matrix, and issue templates for bug reports "
              "for specific phone brands.",
              "No analytics, no ads, no network permission. Privacy is a feature of the app.",
          ])]

story += [Spacer(1, 8),
          callout("<b>Content note:</b> all Arabic text must be proofread against Hisn al-Muslim by a "
                  "qualified person before any public release.")]

import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=20 * mm, rightMargin=20 * mm,
                        topMargin=18 * mm, bottomMargin=20 * mm,
                        title="Adhkaar Reminder - Android Implementation Plan",
                        author="Adhkaar Reminder contributors",
                        subject="Implementation plan for the open-source Adhkaar Reminder Android app")
doc.build(story, onFirstPage=on_page, onLaterPages=on_page)
print("wrote", OUT)
