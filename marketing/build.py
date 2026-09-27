"""Writes the social posts (marketing/posts/*.html) and exports each to marketing/export/*.png
at 2x with headless Edge. Run from anywhere: python marketing/build.py [name ...]

Every post shares the brand mark, the colours, the type and the footer; each has its own
composition, so the set reads as one family and not one post twelve times."""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
POSTS = os.path.join(HERE, "posts")
EXPORT = os.path.join(HERE, "export")
EDGE = [p for p in (r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", r"C:\Program Files\Microsoft\Edge\Application\msedge.exe") if os.path.exists(p)][0]


def page(body, night=False, stamp="", foot="left", css=""):
    cls = "post night-post" if night else "post"
    foot_html = {
        "left": '<div class="foot"><b><i></i>Free for Android</b><span>Morning and evening adhkaar, on time.</span></div>',
        "center": '<div class="foot center"><b><i></i>Free for Android</b><span>Morning and evening adhkaar, on time.</span></div>',
        "none": "",
    }[foot]
    return f"""<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="../post.css"><style>{css}</style></head>
<body><div class="{cls}"><div class="sky {'night' if night else 'dawn'}"></div><div class="grain"></div>
<div class="top"><div class="brand"><img src="../assets/icon.webp" alt=""><span>Adhkaar</span></div><div class="stamp">{stamp}</div></div>
{body}
{foot_html}
</div></body></html>"""


def phone(img, style, cls=""):
    ext = "png" if img in ("missed", "new-widget", "complete") else "webp"
    return f'<div class="phone {cls}" style="{style}"><div class="scr"><img src="../assets/{img}.{ext}" alt=""></div></div>'


CHAT_CSS = """
.chat { background:#0b141a; display:flex; flex-direction:column; }
.chat .cs { display:flex; justify-content:space-between; padding:26px 30px 8px; background:#1f2c34; font:600 17px/1 Inter; color:#e9edef; }
.chat .ch { display:flex; gap:14px; align-items:center; padding:12px 20px 16px; background:#1f2c34; }
.chat .ch .av { width:50px; height:50px; border-radius:50%; background:radial-gradient(circle at 30% 25%, #2f6b57, #163a30); color:#f2cf8a; display:grid; place-items:center; font-size:26px; flex:none; }
.chat .ch b { display:block; font:600 20px/1.2 Inter; color:#e9edef; white-space:nowrap; }
.chat .ch em { display:block; font:400 15px/1.3 Inter; color:#8696a0; font-style:normal; white-space:nowrap; }
.chat .cb { flex:1; display:flex; flex-direction:column; gap:10px; padding:18px; }
.chat .day { align-self:center; font:500 15px/1 Inter; color:#8696a0; background:#182229; padding:8px 14px; border-radius:10px; margin-bottom:6px; }
.chat .m { max-width:84%; padding:10px 12px 8px; border-radius:12px; font:400 19px/1.36 Inter; color:#e9edef; }
.chat .m.in { align-self:flex-start; background:#202c33; border-top-left-radius:0; }
.chat .m.out { align-self:flex-end; background:#005c4b; border-top-right-radius:0; }
.chat .m u { display:block; text-decoration:none; font:600 16px/1.3 Inter; color:#53bdeb; margin-bottom:2px; }
.chat .m u.c3 { color:#7fd6a8; } .chat .m u.c4 { color:#d6a3ff; }
.chat .m s { display:block; text-decoration:none; text-align:right; font:400 13px/1 Inter; color:rgba(233,237,239,.6); margin-top:6px; }
.chat .ch > div{min-width:0;flex:1}
.chat .ch b,.chat .ch em{overflow:hidden;text-overflow:ellipsis}
"""


def chat(style, time="8:31"):
    return f"""<div class="phone" style="{style}"><div class="scr chat">
<div class="cs"><span>{time}</span><span>●●●</span></div>
<div class="ch"><span class="av">☾</span><div><b>Masjid Nur · Announcements</b><em>Aisha, Ibrahim, Musa, You and 180 others</em></div></div>
<div class="cb"><span class="day">Today</span>
<div class="m in"><u>Imam Yusuf</u>Don't forget your morning adhkaar today, a few minutes to start the day with Allah 🌅<s>6:02 AM</s></div>
<div class="m out">In sha Allah, after I check a few things<s>6:04 AM ✓✓</s></div>
<div class="m in"><u class="c3">Ibrahim</u>Did you all see this? 😂<s>6:21 AM</s></div>
<div class="m out">😂 send the rest<s>6:22 AM ✓✓</s></div>
<div class="m in"><u class="c4">Musa</u>Traffic on the bridge already, leave early<s>7:48 AM</s></div>
</div></div></div>"""


KICK = ".kick{font:600 18px/1 Inter;letter-spacing:.2em;text-transform:uppercase;color:var(--dawn)}.night-post .kick{color:var(--night)}"
# The bottom of a bleeding visual fades into the ink so the centred footer sits on a clean floor.
FLOOR = ".floor{position:absolute;left:0;right:0;bottom:0;height:380px;background:linear-gradient(transparent,rgba(5,7,15,.92) 55%,#05070F)}"

# The home-screen widgets, rebuilt in HTML from the app's own layout so they stay sharp at 2x.
# --u is one dp of the widget.
WIDGET_CSS = """
.wg{position:absolute;border-radius:calc(var(--u)*24);padding:calc(var(--u)*16) calc(var(--u)*17);overflow:hidden;
  background:linear-gradient(135deg,#243c9e 0%,#14226a 45%,#070b22 100%);border:1px solid rgba(143,165,255,.28);
  box-shadow:0 50px 90px -30px rgba(0,0,0,.95),inset 0 1px 0 rgba(255,255,255,.08);font-family:Inter}
.wg .hd{display:flex;align-items:center;gap:calc(var(--u)*8);font:700 calc(var(--u)*9.5)/1 Inter;letter-spacing:.06em;color:#8c98c9}
.wg .hd i{font-style:normal;color:#f2cf8a;font-size:calc(var(--u)*12)}
.wg .ty{font:500 calc(var(--u)*13)/1.1 Inter;color:var(--dawn)}
.wg .tm{margin-top:calc(var(--u)*3);font:500 calc(var(--u)*27)/1.05 Inter;letter-spacing:-.01em;color:#f5f7ff}
.wg .in{margin-top:calc(var(--u)*4);font:400 calc(var(--u)*10)/1.2 Inter;color:rgba(245,247,255,.62)}
.wg.sm{display:flex;flex-direction:column;justify-content:space-between}
.wg.wd{display:grid;grid-template-columns:1fr 1fr;column-gap:calc(var(--u)*16)}
.wg.wd .l .ty{margin-top:calc(var(--u)*6)}
.wg .fl{margin-left:auto;color:#f2cf8a;font-weight:700;letter-spacing:0}
.wg .r{display:flex;flex-direction:column;justify-content:space-between}
.wg .row{display:flex;align-items:center;gap:calc(var(--u)*8);font:400 calc(var(--u)*12)/1 Inter;color:#f5f7ff}
.wg .row i{width:calc(var(--u)*7);height:calc(var(--u)*7);border-radius:50%;background:var(--dawn)}
.wg .row i.n{background:#8fb1ff}.wg .row em{margin-left:auto;font-style:normal;font-weight:600;color:#5fd6a0}.wg .row em.d{color:rgba(245,247,255,.5)}
.wg .pill{padding:calc(var(--u)*8) 0;border-radius:99px;text-align:center;font:700 calc(var(--u)*11.5)/1 Inter;color:#fff;background:linear-gradient(90deg,#ff8f62,#e0577e)}
"""


def widget_small(style):
    return f"""<div class="wg sm" style="{style}"><div class="hd"><i>☾</i>ADHKAAR</div>
<div><div class="ty">Morning</div><div class="tm">6:00 AM</div><div class="in">in 12h 11m</div></div></div>"""


def widget_wide(style):
    return f"""<div class="wg wd" style="{style}">
<div class="l"><div class="hd"><i>☾</i>ADHKAAR<span class="fl">🔥 28</span></div><div class="ty">Morning</div><div class="tm">6:00 AM</div><div class="in">Tomorrow · in 12h 11m</div></div>
<div class="r"><div class="row"><i></i>Morning<em>Done</em></div><div class="row"><i class="n"></i>Evening<em class="d">—</em></div><div class="pill">Opens 6:00 AM</div></div></div>"""


POST_LIST = {
    # 1. A question, before anything else. Type does the work; the app only peeks in.
    "01-question": page(f"""
<div style="position:absolute; left:72px; right:72px; top:210px">
  <p class="kick">Be honest</p>
  <h1 class="h" style="font-size:132px; line-height:.94; margin-top:30px">When did you last read the morning adhkaar <em>on time?</em></h1>
  <p class="sub" style="max-width:470px; margin-top:40px">Not because you didn't want to. The phone just got there first.</p>
</div>
{phone("today-1", "left:618px; top:800px; width:430px; transform:rotate(-8deg)")}
""", stamp="A question", css=KICK),

    # 2. The problem, lived: the chat fills the frame, the words sit over its foot.
    "02-the-phone-wins": page(f"""
{chat("left:190px; top:190px; width:700px; transform:rotate(-4deg); opacity:.92")}
<div class="shade"></div>
<div style="position:absolute; left:72px; right:72px; top:860px">
  <h1 class="h" style="font-size:100px">We know the adhkaar.<br><em>The phone wins anyway.</em></h1>
  <p class="sub" style="max-width:720px">The reminder arrives in the group at 6:02. By 8:30 the morning is gone, and the phone was in hand the whole time.</p>
</div>
""", stamp="A familiar morning", css=CHAT_CSS + ".foot{width:auto;right:72px}.shade{position:absolute;left:0;right:0;top:620px;bottom:0;background:linear-gradient(transparent,rgba(5,7,15,.94) 30%,#05070F)}"),

    # 3. Before and after, side by side.
    "03-before-after": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:92px">Same morning.<br><em>Different ending.</em></h1>
</div>
<div class="half l"><span class="lab">Before</span><div class="win">{chat("left:50%; top:0; width:380px; margin-left:-190px", "7:52")}</div><p class="cap">A reminder in a group chat, and a promise to do it later.</p></div>
<div class="half r"><span class="lab on">With Adhkaar</span><div class="win">{phone("s2", "left:50%; top:0; width:380px; margin-left:-190px")}</div><p class="cap">The adhkaar open on your screen after Fajr, counter ready.</p></div>
""", stamp="6:00 AM", foot="center", css=CHAT_CSS + """
.half{position:absolute;top:410px;width:432px;height:720px}.half.l{left:72px}.half.r{right:72px}
.win{position:absolute;left:0;right:0;top:48px;height:570px;overflow:hidden}
.win::after{content:"";position:absolute;left:0;right:0;bottom:0;height:220px;background:linear-gradient(transparent,#05070F)}
.half.l .phone{filter:saturate(.35) brightness(.7)}
.lab{position:absolute;top:0;left:0;right:0;text-align:center;font:600 16px/1 Inter;letter-spacing:.2em;text-transform:uppercase;color:var(--text-3)}
.lab.on{color:var(--dawn)}
.cap{position:absolute;left:24px;right:24px;top:640px;text-align:center;font:400 22px/1.4 Inter;color:var(--text-3)}
.half.l .cap{text-decoration:line-through;text-decoration-color:rgba(255,184,138,.55)}
.half.r .cap{font:400 29px/1.2 'Instrument Serif';color:var(--text)}
.post::before{content:"";position:absolute;left:540px;top:410px;height:720px;width:1px;background:linear-gradient(transparent,var(--line) 15%,var(--line) 85%,transparent)}
"""),

    # 4. The three modes as a staircase: each step firmer than the last.
    "04-modes": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:96px">As gentle or as firm<br><em>as you need.</em></h1>
</div>
<div class="lab" style="left:72px; top:640px; width:260px"><span>01</span><h3>Gentle</h3><p>A chime and a notice, for when the habit is yours.</p></div>
<div class="lab" style="left:356px; top:520px; width:300px"><span>02</span><h3>Full screen</h3><p>The adhkaar open themselves, even on the lock screen.</p></div>
<div class="lab on" style="left:680px; top:400px; width:328px"><span>03</span><h3>Lockdown</h3><p>Other apps wait until you've read them. Calls always work.</p></div>
<div class="step1">{phone("today-1", "left:72px; top:780px; width:260px", "m")}<div class="notif"><img src="../assets/icon.webp" alt=""><div><b>Morning adhkaar</b><span>It's time · one soft chime</span></div></div></div>
{phone("s0", "left:356px; top:660px; width:300px", "m")}
{phone("lockdown", "left:680px; top:540px; width:328px", "m")}
<div class="floor"></div>
""", stamp="Three modes", foot="none", css=FLOOR + """
.floor{height:220px}
.lab{position:absolute}.lab span{font:600 15px/1 Inter;letter-spacing:.2em;color:var(--text-3)}
.lab h3{margin-top:10px;font:400 44px/1 'Instrument Serif'}.lab.on h3{color:var(--dawn)}.lab.on span{color:var(--dawn)}
.lab p{margin-top:8px;font:400 17px/1.4 Inter;color:var(--text-2)}
.phone.m{border-radius:32px;padding:6px}.phone.m .scr{border-radius:26px}.phone.m::before{top:15px;width:11px;height:11px;margin-left:-5.5px}
.notif{position:absolute;left:88px;width:228px;top:818px;display:flex;gap:10px;align-items:center;padding:11px 12px;border-radius:16px;background:rgba(40,42,56,.97);box-shadow:0 12px 26px rgba(0,0,0,.55)}
.notif img{width:30px;height:30px;border-radius:8px}.notif b{display:block;font:600 14px/1.2 Inter}.notif span{font:400 12.5px/1.3 Inter;color:var(--text-2)}
"""),

    # 5. The day, as a timeline.
    "05-your-day": page(f"""
<div style="position:absolute; left:72px; top:170px; width:520px">
  <h1 class="h" style="font-size:100px">Your day,<br><em>held gently.</em></h1>
  <p class="sub" style="max-width:470px">Everything follows your masjid's times. Set them once.</p>
</div>
<ol class="tl">
  <li><time>5:30</time><b>Fajr</b><span>A reminder to get ready</span></li>
  <li class="on"><time>6:00</time><b>Morning adhkaar open</b><span>Right after the congregation</span></li>
  <li><time>8:30</time><b>They close</b><span>Or the time you choose</span></li>
  <li><time>1:22</time><b>After Dhuhr</b><span>The adhkaar after salah</span></li>
  <li class="on"><time>4:45</time><b>Evening adhkaar open</b><span>After Asr is prayed</span></li>
  <li><time>6:35</time><b>Maghrib</b><span>A card you won't scroll past</span></li>
  <li><time>10:00</time><b>Before sleep</b><span>The last words of the day</span></li>
</ol>
{phone("today-1", "left:638px; top:476px; width:370px")}
""", stamp="From Fajr to sleep", css="""
.tl{position:absolute;left:78px;top:466px;width:500px;list-style:none;border-left:1px solid rgba(255,255,255,.16);padding-left:30px}
.tl li{position:relative;display:grid;grid-template-columns:96px 1fr;column-gap:14px;padding:10px 0}
.tl li::before{content:"";position:absolute;left:-36px;top:20px;width:11px;height:11px;border-radius:50%;background:#2a2d3a;border:1px solid rgba(255,255,255,.3)}
.tl li.on::before{background:var(--dawn);border-color:var(--dawn);box-shadow:0 0 14px var(--dawn)}
.tl time{grid-row:span 2;font:400 34px/1 'Instrument Serif';font-variant-numeric:tabular-nums;color:var(--text)}
.tl li.on time{color:var(--dawn)}
.tl b{font:600 21px/1.2 Inter}.tl span{font:400 17px/1.35 Inter;color:var(--text-3)}
"""),

    # 6. Salah: the card itself, with what it does on either side.
    "06-salah": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:92px">Salah is near.<br><em>Leave what you're doing.</em></h1>
</div>
{phone("popup", "left:50%; top:420px; width:396px; margin-left:-198px")}
<div class="side" style="left:72px; top:700px"><p class="kick">Before salah</p><p class="v">Time to make wudu and head out.</p></div>
<div class="side" style="right:72px; top:930px; text-align:right"><p class="kick">Over any app</p><p class="v">Even on the lock screen.</p></div>
""", night=True, stamp="Maghrib", foot="none", css=KICK + """
.side{position:absolute;width:240px}.side .v{margin-top:14px;font:400 36px/1.1 'Instrument Serif'}
.side::before{content:"";display:block;width:36px;height:1px;background:var(--night);margin-bottom:18px}
.side[style*="right"]::before{margin-left:auto}
"""),

    # 7. Before sleep: the clock and the last words of the day.
    "07-before-sleep": page(f"""
<div class="clock">10:00<small>PM</small></div>
<div style="position:absolute; left:72px; top:454px; width:500px">
  <h1 class="h" style="font-size:90px">End the day<br><em>with dhikr,</em><br>not the feed.</h1>
  <p class="sub" style="max-width:450px">At bedtime the adhkaar before sleep open by themselves. Choose Lockdown, and the late-night scroll waits until you've read them.</p>
</div>
{phone("before-sleep", "left:628px; top:454px; width:380px")}
""", night=True, stamp="Before sleep", css=".clock{position:absolute;left:62px;top:150px;font:300 250px/1 Inter;letter-spacing:-12px;color:var(--text);font-variant-numeric:tabular-nums}.clock small{font:500 40px/1 Inter;letter-spacing:.1em;color:var(--night);margin-left:22px}"),

    # 8. Missed: kindness, not guilt.
    "08-missed": page(f"""
{phone("missed", "left:50%; top:150px; width:400px; margin-left:-200px; transform:rotate(-3deg)")}
<div class="band">
  <p class="kick">It happens</p>
  <h1 class="h" style="font-size:96px; margin-top:24px">Tomorrow's Fajr<br><em>is a fresh start.</em></h1>
  <p class="sub" style="max-width:700px; margin:26px auto 0">Miss the adhkaar and the app says so kindly, with what they held and when the next ones open.</p>
</div>
""", stamp="No guilt", foot="center", css=KICK + ".band{position:absolute;left:0;right:0;top:560px;bottom:0;padding:240px 72px 0;text-align:center;background:linear-gradient(transparent,rgba(5,7,15,.96) 180px,#05070F 220px)}"),

    # 9. The streak, as one enormous number.
    "09-streak": page(f"""
<div class="big">22</div>
<div style="position:absolute; left:72px; top:640px; width:480px">
  <h1 class="h" style="font-size:92px">days in a row.<br><em>Don't let today be the gap.</em></h1>
  <p class="sub" style="max-width:430px">Every finished morning and evening adds to your streak and your month.</p>
</div>
{phone("streak", "left:608px; top:411px; width:400px")}
""", night=True, stamp="Consistency", css=".big{position:absolute;left:52px;top:120px;font:400 560px/1 'Instrument Serif';letter-spacing:-24px;background:linear-gradient(180deg,#b9ccff,#5a78ff 70%);-webkit-background-clip:text;color:transparent}"),

    # 10. Five languages, fanned out.
    "10-languages": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:96px">In the language<br><em>you pray in.</em></h1>
  <p class="langs"><span>Hausa</span><i>·</i><span>Yorùbá</span><i>·</i><span>English</span><i>·</i><span lang="ar" dir="rtl">العربية</span><i>·</i><span>Igbo</span></p>
</div>
{phone("lang-ha", "left:40px; top:610px; width:250px; transform:rotate(-13deg)", "f")}
{phone("lang-yo", "left:210px; top:540px; width:290px; transform:rotate(-6.5deg)", "f")}
{phone("lang-ig", "left:790px; top:610px; width:250px; transform:rotate(13deg)", "f")}
{phone("lang-ar", "left:580px; top:540px; width:290px; transform:rotate(6.5deg)", "f")}
{phone("today-1", "left:365px; top:500px; width:350px", "f")}
<div class="floor"></div>
""", stamp="Five languages", foot="center", css=FLOOR + """
.langs{margin-top:34px;display:flex;justify-content:center;align-items:baseline;gap:0;font:400 34px/1 'Instrument Serif';color:var(--text-2)}
.langs i{font-style:normal;margin:0 18px;color:var(--dawn)}
.langs span{unicode-bidi:isolate}.langs span[lang]{font:400 30px/1 'Amiri Quran'}
.phone.f{border-radius:30px;padding:6px}.phone.f .scr{border-radius:24px}.phone.f::before{top:15px;width:11px;height:11px;margin-left:-5.5px}
"""),

    # 11. What it asks of you, as a ledger.
    "11-trust": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:104px">It asks for<br><em>a few minutes.</em><br>Nothing else.</h1>
</div>
<dl class="ledger">
  <div><dt>Ads</dt><dd>None, ever.</dd></div>
  <div><dt>An account</dt><dd>Not needed.</dd></div>
  <div><dt>Your data</dt><dd>Stays on your phone.</dd></div>
  <div><dt>Your money</dt><dd>Free, and open source.</dd></div>
  <div><dt>Every dhikr</dt><dd>With its source.</dd></div>
</dl>
""" + phone("complete", "left:628px; top:454px; width:380px"), stamp="Why trust it", foot="left", css="""
.ledger{position:absolute;left:72px;width:520px;top:540px}
.ledger div{display:flex;align-items:baseline;gap:16px;padding:22px 0}
.ledger div::after{content:"";order:2;flex:1;border-bottom:2px dotted rgba(255,255,255,.28);transform:translateY(-8px)}
.ledger dt{order:1;font:600 15px/1 Inter;letter-spacing:.18em;text-transform:uppercase;color:var(--text-3)}
.ledger dd{order:3;font:400 38px/1 'Instrument Serif';letter-spacing:-.3px}
"""),

    # 12. Widgets: the home screen.
    "12-widgets": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">On your home screen,<br><em>too.</em></h1>
</div>
{widget_wide("left:72px; top:470px; width:936px; height:288px; --u:2.615px")}
<ul class="what">
  <li>The next adhkaar, and when it opens</li>
  <li>Your streak, kept in sight</li>
  <li>The Hijri date, and a dhikr to hold onto</li>
</ul>
{widget_small("left:700px; top:800px; width:308px; height:308px; --u:2.095px")}
""", stamp="Widgets", css=WIDGET_CSS + """
.foot{width:auto;right:72px}
.what{position:absolute;left:72px;top:812px;width:560px;list-style:none}
.what li{padding:20px 0 20px 34px;position:relative;font:400 34px/1.15 'Instrument Serif';border-bottom:1px solid var(--line)}
.what li:first-child{padding-top:0}.what li:first-child::before{top:14px}
.what li::before{content:"";position:absolute;left:0;top:34px;width:9px;height:9px;border-radius:50%;background:var(--dawn);box-shadow:0 0 12px var(--dawn)}
"""),

    # 13. Share cards: a dhikr to pass on.
    "13-share": page("""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:96px">Pass on a dhikr,<br><em>beautifully.</em></h1>
  <p class="sub" style="max-width:640px; margin:26px auto 0">Any dhikr becomes a card for family and friends, with its source. Three styles.</p>
</div>
<img class="card" src="../assets/card-dawn.png" style="left:92px; top:640px; width:360px; transform:rotate(-8deg)" alt="">
<img class="card" src="../assets/card-parchment.png" style="left:628px; top:640px; width:360px; transform:rotate(8deg)" alt="">
<img class="card" src="../assets/card-midnight.png" style="left:320px; top:560px; width:440px" alt="">
""", night=True, stamp="Share", foot="center", css=".card{position:absolute;border-radius:18px;box-shadow:0 60px 110px -30px rgba(0,0,0,.95),0 0 0 1px rgba(255,255,255,.08)}"),

    # 14. A break, even in Lockdown, with a gentle word first.
    "14-pause": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Life happens.<br><em>Pause, then come back.</em></h1>
  <p class="sub" style="max-width:620px">Even in Lockdown you can step away for ten minutes or an hour. The adhkaar ring again when the break ends.</p>
</div>
<div class="step" style="left:72px; top:640px"><b>1</b>Choose how long</div>
<div class="step" style="left:588px; top:640px"><b>2</b>A gentle word first</div>
{phone("b2", "left:72px; top:700px; width:420px")}
{phone("b3", "left:588px; top:700px; width:420px")}
<div class="floor"></div>
""", stamp="Take a break", css=FLOOR + """
.foot{width:auto;right:72px}
.step{position:absolute;display:flex;align-items:center;gap:14px;font:500 21px/1 Inter;color:var(--text-2)}
.step b{width:34px;height:34px;border-radius:50%;display:grid;place-items:center;font:600 16px/1 Inter;color:#1a0f14;background:var(--dawn)}
"""),
}

def main(only):
    os.makedirs(POSTS, exist_ok=True)
    os.makedirs(EXPORT, exist_ok=True)
    for f in os.listdir(EXPORT):
        if not only and f.endswith(".png") and f[:-4] not in POST_LIST:
            os.remove(os.path.join(EXPORT, f))
    for name, html in POST_LIST.items():
        if only and name not in only:
            continue
        src = os.path.join(POSTS, name + ".html")
        open(src, "w", encoding="utf-8").write(html)
        out = os.path.join(EXPORT, name + ".png")
        subprocess.run([EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
                        "--window-size=1080,1350", "--virtual-time-budget=4000", f"--screenshot={out}", "file:///" + src.replace("\\", "/")],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(name, os.path.exists(out))


if __name__ == "__main__":
    main(sys.argv[1:])
