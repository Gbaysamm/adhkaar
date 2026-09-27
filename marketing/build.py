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
    # 15. For testers: the fix, and where to get it.
    "15-update": page(f"""
<div style="position:absolute; left:72px; top:190px; width:520px">
  <p class="kick">Update · For everyone testing</p>
  <h1 class="h" style="font-size:96px; margin-top:26px">After salah,<br><em>right on time.</em></h1>
  <p class="sub" style="max-width:500px">Version 0.1.7 fixes a delay that held back the after-salah and before-sleep reminders. Install it over the one you have. Nothing is lost: your progress, streak and settings stay.</p>
</div>
<div class="qr"><img src="../assets/qr-0.1.7.png" alt=""><div><b>Scan to download</b><span>Or tap the link in the message. From now on, the app tells you when an update is out.</span></div></div>
{phone("after-salah", "left:628px; top:454px; width:380px")}
""", stamp="Version 0.1.7", css=KICK + """
.qr{position:absolute;left:72px;top:870px;width:520px;display:flex;gap:28px;align-items:center}
.qr img{width:176px;height:176px;padding:14px;border-radius:22px;background:#fff;box-shadow:0 30px 60px -24px rgba(0,0,0,.8)}
.qr b{display:block;font:400 36px/1.1 'Instrument Serif'}.qr span{display:block;margin-top:10px;font:400 18px/1.45 Inter;color:var(--text-2)}
"""),
    # 16. The Hijri date as the committee announces it.
    "16-moon-sighting": page("""
<div class="moon"></div>
<div style="position:absolute; left:72px; right:72px; top:470px; text-align:center">
  <h1 class="h" style="font-size:100px">The Hijri date,<br><em>as announced.</em></h1>
  <p class="sub" style="max-width:780px; margin:28px auto 0">Not a calculation. Dates follow the Sultan of Sokoto's moon-sighting committee, and when a new month is announced, the app updates on its own.</p>
</div>
<img class="card" src="../assets/settings-hijri.png" alt="">
""", night=True, stamp="Hijri calendar", foot="center", css="""
.moon{position:absolute;left:50%;top:150px;width:230px;height:230px;margin-left:-80px;border-radius:50%;box-shadow:-46px 22px 0 0 #F2CF8A;filter:drop-shadow(0 0 46px rgba(242,207,138,.55))}
.card{position:absolute;left:50%;width:620px;margin-left:-310px;top:846px;border-radius:30px;box-shadow:0 50px 100px -30px rgba(0,0,0,.95),0 0 0 1px rgba(255,255,255,.1)}
"""),

    # 17. Both calendars in one month, filled in by what was read.
    "17-calendar": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Two calendars,<br><em>one month.</em></h1>
</div>
<div style="position:absolute; left:72px; top:480px; width:380px">
  <p class="sub" style="max-width:380px; margin-top:0">Every day carries its Hijri and Gregorian date, filled in when you read.</p>
  <ul class="key"><li><i class="m"></i>Morning</li><li><i class="e"></i>Evening</li><li><i class="b"></i>Both</li></ul>
</div>
<img class="cal" src="../assets/calendar-card.png" alt="">
""", stamp="Calendar", css="""
.cal{position:absolute;left:488px;top:480px;width:520px;border-radius:26px;box-shadow:0 60px 110px -30px rgba(0,0,0,.95),0 0 0 1px rgba(255,255,255,.1)}
.key{list-style:none;margin-top:48px;display:grid;gap:20px}
.key li{display:flex;align-items:center;gap:16px;font:400 36px/1 'Instrument Serif'}
.key i{width:26px;height:26px;border-radius:8px}.key .m{background:#E0763A}.key .e{background:#5F86F0}.key .b{background:linear-gradient(135deg,#E0763A 50%,#5F86F0 50%)}
"""),

    # 18. The reminder of the day, and the card it makes.
    "18-daily-reminder": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:100px">A reminder<br><em>for every day.</em></h1>
  <p class="sub" style="max-width:700px; margin:26px auto 0">A verse or a hadith each day, always with its source. Save it, or share it as an image.</p>
</div>
<img class="share" src="../assets/day-card.png" alt="">
{phone("day-reminder", "left:600px; top:560px; width:360px; transform:rotate(4deg)")}
<div class="floor"></div>
""", night=True, stamp="Every day", foot="center", css=FLOOR + """
.share{position:absolute;left:110px;top:600px;width:480px;border-radius:26px;transform:rotate(-4deg);box-shadow:0 60px 110px -30px rgba(0,0,0,.95),0 0 0 1px rgba(255,255,255,.1)}
"""),

    # 19. The special days: the lock screen at 8 PM the evening before.
    "19-special-days": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:96px">The days that matter,<br><em>the evening before.</em></h1>
</div>
<ul class="days">
  <li><b>9 Dhul Hijjah</b><span>The Day of Arafah</span></li>
  <li><b>10 Muharram</b><span>ʿAshura, with the 9th</span></li>
  <li><b>13 · 14 · 15</b><span>The white days, every month</span></li>
  <li><b>Friday</b><span>Surah al-Kahf and salawat</span></li>
</ul>
<div class="lock">
  <div class="wall"></div>
  <div class="clock"><small>Wednesday, 8 Dhul Hijjah</small>8:00</div>
  <div class="nt main"><div class="hd"><img src="../assets/icon.webp" alt=""><span>Adhkaar · now</span></div><b>Tomorrow: The Day of Arafah</b><p>Fasting it expiates the sins of the past year and the coming year. Make the intention tonight.</p></div>
  <div class="nt old"><div class="hd"><img src="../assets/icon.webp" alt=""><span>Adhkaar · Friday</span></div><b>Jumuʿah Mubarak</b><p>Read Surah al-Kahf today, and send abundant salawat on the Prophet ﷺ.</p></div>
</div>
<div class="floor"></div>
""", night=True, stamp="Special days", css=FLOOR + """
.floor{height:240px}
.days{position:absolute;left:72px;top:470px;width:400px;list-style:none;border-left:1px solid rgba(242,207,138,.35);padding-left:32px}
.days li{position:relative;padding:0 0 38px}
.days li::before{content:"";position:absolute;left:-39px;top:12px;width:13px;height:13px;border-radius:50%;background:var(--gold);box-shadow:0 0 16px rgba(242,207,138,.7)}
.days b{display:block;font:400 44px/1 'Instrument Serif';color:var(--gold)}
.days span{display:block;margin-top:10px;font:400 20px/1.3 Inter;color:var(--text-2)}
.lock{position:absolute;left:540px;top:430px;width:468px;height:1010px;border-radius:52px;padding:9px;background:linear-gradient(150deg,#3a3d48,#15171e 22%,#0b0c11 60%,#26282f);box-shadow:0 60px 120px -30px rgba(0,0,0,.9),0 0 0 1px rgba(255,255,255,.08)}
.lock .wall{position:absolute;inset:9px;border-radius:44px;background:radial-gradient(70% 40% at 70% 12%,rgba(143,177,255,.45),transparent 70%),radial-gradient(80% 50% at 20% 100%,rgba(106,76,255,.35),transparent 70%),linear-gradient(#141a3d,#070a1c)}
.lock .wall::after{content:"";position:absolute;right:64px;top:44px;width:46px;height:46px;border-radius:50%;box-shadow:-10px 5px 0 0 #F2CF8A;filter:drop-shadow(0 0 14px rgba(242,207,138,.6))}
.lock .clock{position:absolute;left:0;right:0;top:120px;text-align:center;font:200 124px/1 Inter;letter-spacing:-4px}
.lock .clock small{display:block;margin-bottom:10px;font:500 20px/1 Inter;letter-spacing:0;color:var(--text-2)}
.nt{position:absolute;left:28px;right:28px;padding:18px 20px;border-radius:24px;background:rgba(30,34,58,.82);backdrop-filter:blur(20px);border:1px solid rgba(255,255,255,.1)}
.nt .hd{display:flex;align-items:center;gap:10px;font:500 15px/1 Inter;color:var(--text-3)}
.nt .hd img{width:26px;height:26px;border-radius:8px}
.nt b{display:block;margin-top:12px;font:600 21px/1.25 Inter}
.nt p{margin-top:6px;font:400 17px/1.4 Inter;color:var(--text-2)}
.nt.main{top:340px}.nt.old{top:540px;opacity:.6;transform:scale(.96)}
"""),

    # 20. Insights: the numbers and the week.
    "20-insights": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Watch the habit<br><em>take root.</em></h1>
</div>
<div class="tiles">
  <div><b>28</b><span>Day streak</span></div>
  <div><b>28</b><span>Best streak</span></div>
  <div><b>46</b><span>Sessions this month</span></div>
  <div><b>20</b><span>Full days this month</span></div>
</div>
<div class="bars">
  <p>Days with adhkaar · last 8 weeks</p>
  <div class="cols">
    <div style="--v:6"><i>6/8</i><em></em><span>S</span></div><div style="--v:6"><em></em><span>M</span></div><div style="--v:6"><em></em><span>T</span></div>
    <div style="--v:6"><em></em><span>W</span></div><div style="--v:5"><em></em><span>T</span></div><div style="--v:6"><em></em><span>F</span></div><div style="--v:5"><em></em><span>S</span></div>
  </div>
</div>
""", stamp="Insights", css="""
.tiles{position:absolute;left:72px;right:72px;top:450px;display:grid;grid-template-columns:repeat(4,1fr);gap:18px}
.tiles div{padding:28px 24px;border-radius:28px;background:linear-gradient(160deg,rgba(255,255,255,.1),rgba(255,255,255,.03));border:1px solid rgba(255,255,255,.12)}
.tiles b{display:block;font:400 76px/1 'Instrument Serif'}.tiles div:first-child b{color:var(--dawn)}
.tiles span{display:block;margin-top:12px;font:400 17px/1.3 Inter;color:var(--text-2)}
.bars{position:absolute;left:72px;right:72px;top:710px;height:430px;padding:34px 40px;border-radius:32px;background:linear-gradient(160deg,rgba(255,255,255,.08),rgba(255,255,255,.02));border:1px solid rgba(255,255,255,.12)}
.bars p{font:600 21px/1 Inter}
.cols{position:absolute;left:40px;right:40px;bottom:30px;top:100px;display:grid;grid-template-columns:repeat(7,1fr);gap:28px;align-items:end}
.cols div{position:relative;display:grid;justify-items:center;gap:14px}
.cols em{width:56px;height:calc(var(--v) * 30px);border-radius:10px 10px 4px 4px;background:rgba(95,134,240,.7)}
.cols div:first-child em{background:#6B8FFF;box-shadow:0 0 30px rgba(107,143,255,.5)}
.cols span{font:500 18px/1 Inter;color:var(--text-3)}.cols i{position:absolute;top:-34px;font:600 17px/1 Inter;font-style:normal}
.foot{width:auto;right:72px}
"""),

    # 21. Your own duas, written down and kept with the adhkaar.
    "21-my-duas": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Your duas,<br><em>kept close.</em></h1>
  <p class="sub" style="max-width:560px">Write the duas that matter to you, and read them with your morning or evening adhkaar.</p>
</div>
<div class="dua back"><p class="t">Before my exam</p><p class="en">O Allah, make it easy for me, and let me remember what I have learnt.</p><div class="tags"><span>Morning</span></div></div>
<div class="dua front">
  <p class="t">For my parents</p>
  <div class="ar">رَّبِّ ٱرْحَمْهُمَا كَمَا رَبَّيَانِى صَغِيرًا</div>
  <p class="en">My Lord, have mercy on them, as they raised me when I was small.</p>
  <div class="tags"><span class="n">×3</span><span>Morning</span><span class="ev">Evening</span><em>al-Isra 17:24</em></div>
</div>
<div class="pen"><i></i>Write a dua</div>
""", stamp="My duas", css="""
.dua{position:absolute;border-radius:36px;padding:40px 44px;background:linear-gradient(160deg,rgba(255,255,255,.13),rgba(255,255,255,.04));border:1px solid rgba(255,255,255,.16);backdrop-filter:blur(20px);box-shadow:0 60px 110px -30px rgba(0,0,0,.95)}
.dua .t{font:600 22px/1 Inter;color:var(--text-2)}
.dua .en{margin-top:18px;font:italic 400 36px/1.25 'Instrument Serif'}
.dua.front{left:72px;right:72px;top:620px;transform:rotate(-2deg)}
.dua.front .ar{margin-top:26px;font:400 64px/1.6 'Amiri Quran';color:var(--gold);direction:rtl;text-align:center}
.dua.front .en{text-align:center;font-size:32px}
.dua.back{left:180px;right:40px;top:520px;padding-top:30px;transform:rotate(3deg);opacity:.55}
.dua.back .en{font-size:26px}
.tags{margin-top:26px;display:flex;align-items:center;gap:12px}
.tags span{padding:9px 16px;border-radius:99px;border:1px solid rgba(255,255,255,.18);font:500 17px/1 Inter;color:var(--text-2)}
.tags span:not(.n)::before{content:"";display:inline-block;width:8px;height:8px;border-radius:50%;background:var(--dawn);margin-right:9px;vertical-align:1px}
.tags span.ev::before{background:var(--night)}
.tags em{margin-left:auto;font:600 15px/1 Inter;font-style:normal;letter-spacing:.16em;text-transform:uppercase;color:var(--dawn)}
.dua.front .tags{justify-content:center}
.pen{position:absolute;right:72px;top:1100px;display:flex;align-items:center;gap:14px;padding:22px 34px;border-radius:99px;background:linear-gradient(90deg,#4a6cff,#6b8fff);font:600 22px/1 Inter;box-shadow:0 20px 50px -14px rgba(75,108,255,.8)}
.pen i{width:30px;height:30px;border-radius:50%;background:rgba(255,255,255,.25);position:relative}
.pen i::before,.pen i::after{content:"";position:absolute;left:50%;top:50%;width:14px;height:2px;margin:-1px 0 0 -7px;background:#fff}.pen i::after{transform:rotate(90deg)}
"""),
    # 22. Collections: a dhikr for each moment, and how it ends.
    "22-collections": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px; text-align:center">
  <h1 class="h" style="font-size:100px">For every moment<br><em>of the day.</em></h1>
  <p class="sub" style="max-width:720px; margin:26px auto 0">On waking, after each salah, before sleep, and the everyday duas. Each with its source, each a few minutes.</p>
</div>
{phone("library", "left:110px; top:580px; width:400px; transform:rotate(-5deg)")}
{phone("sleep-complete", "left:560px; top:540px; width:410px; transform:rotate(4deg)")}
<div class="floor"></div>
""", night=True, stamp="Collections", foot="center", css=FLOOR),

    # 23. Favourites: the ones you return to.
    "23-favourites": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Keep the ones<br><em>you return to.</em></h1>
  <p class="sub" style="max-width:600px">Tap the heart on any dhikr, and it waits for you in Favourites, with its count and its source.</p>
</div>
<div class="heart"></div>
<ol class="fav">
  <li><i>1</i><div><b>Ayat al-Kursi</b><span>al-Baqarah 2:255</span></div><u class="on"></u></li>
  <li class="lift"><i>2</i><div><b>Sayyid al-Istighfar</b><span>al-Bukhari 6306</span></div><u class="on"></u></li>
  <li><i>3</i><div><b>Allah is sufficient for me</b><span>Abu Dawud 5081</span></div><em>×7</em><u class="on"></u></li>
</ol>
""", stamp="Favourites", css="""
.heart{position:absolute;right:92px;top:190px;width:150px;height:150px;transform:rotate(-10deg);filter:drop-shadow(0 0 40px rgba(255,92,138,.6))}
.heart::before{content:"";position:absolute;inset:0;background:#FF6B94;-webkit-mask:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M12 21s-7.5-4.6-9.6-9.2C.9 8.4 3 4.5 6.9 4.5c2.2 0 3.7 1.2 5.1 3 1.4-1.8 2.9-3 5.1-3 3.9 0 6 3.9 4.5 7.3C19.5 16.4 12 21 12 21z'/%3E%3C/svg%3E") center/contain no-repeat}
.fav{position:absolute;left:72px;right:72px;top:580px;list-style:none;display:grid;gap:26px}
.fav li{display:flex;align-items:center;gap:26px;padding:38px 36px;border-radius:30px;background:linear-gradient(160deg,rgba(255,255,255,.1),rgba(255,255,255,.03));border:1px solid rgba(255,255,255,.13)}
.fav li.lift{transform:translateX(26px) scale(1.02);background:linear-gradient(160deg,rgba(255,255,255,.16),rgba(255,255,255,.05));box-shadow:0 40px 80px -30px rgba(0,0,0,.9)}
.fav i{width:52px;height:52px;flex:none;border-radius:50%;border:1px solid rgba(255,255,255,.25);display:grid;place-items:center;font:500 20px/1 Inter;font-style:normal;color:var(--text-2)}
.fav div{flex:1}.fav b{display:block;font:400 42px/1.05 'Instrument Serif'}.fav span{display:block;margin-top:8px;font:400 18px/1 Inter;color:var(--text-3)}
.fav em{font:500 18px/1 Inter;font-style:normal;padding:9px 14px;border-radius:99px;border:1px solid rgba(255,255,255,.2);color:var(--text-2)}
.fav u{width:34px;height:34px;flex:none;background:#FF6B94;-webkit-mask:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M12 21s-7.5-4.6-9.6-9.2C.9 8.4 3 4.5 6.9 4.5c2.2 0 3.7 1.2 5.1 3 1.4-1.8 2.9-3 5.1-3 3.9 0 6 3.9 4.5 7.3C19.5 16.4 12 21 12 21z'/%3E%3C/svg%3E") center/contain no-repeat}
.foot{width:auto;right:72px}
"""),

    # 24. Salah times: the masjid's, set once.
    "24-your-times": page("""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Your masjid's times,<br><em>not a guess.</em></h1>
  <p class="sub" style="max-width:640px">Calculated times are only a starting point. Set the times your masjid prays once, and every reminder follows them.</p>
</div>
<img class="strip" src="../assets/prayers-strip.png" alt="">
<div class="edit">
  <div class="row"><span class="k">Maghrib</span><span class="calc">Calculated start 6:40 PM</span></div>
  <div class="arrow"></div>
  <div class="chip">6:35 PM<small>Your masjid</small></div>
</div>
""", night=True, stamp="Salah times", css="""
.strip{position:absolute;left:72px;right:72px;width:936px;top:560px;border-radius:30px;box-shadow:0 50px 100px -30px rgba(0,0,0,.95),0 0 0 1px rgba(255,255,255,.08)}
.edit{position:absolute;left:72px;right:72px;top:980px;display:flex;align-items:center;gap:30px}
.edit .k{display:block;font:400 44px/1 'Instrument Serif'}.edit .calc{display:block;margin-top:10px;font:400 20px/1 Inter;color:var(--text-3);text-decoration:line-through;text-decoration-color:rgba(143,177,255,.6)}
.edit .arrow{flex:1;height:1px;background:linear-gradient(90deg,rgba(143,177,255,.1),var(--night));position:relative}
.edit .arrow::after{content:"";position:absolute;right:-2px;top:-6px;border:6px solid transparent;border-left:10px solid var(--night)}
.chip{padding:20px 30px;border-radius:24px;background:linear-gradient(135deg,#3b5bdb,#6b8fff);font:600 36px/1 Inter;box-shadow:0 20px 50px -14px rgba(75,108,255,.8)}
.chip small{display:block;margin-top:8px;font:500 16px/1 Inter;color:rgba(255,255,255,.8)}
.foot{width:auto;right:72px}
"""),

    # 25. Reading: the page set the way you read.
    "25-reading": page(f"""
<div style="position:absolute; left:72px; right:72px; top:170px">
  <h1 class="h" style="font-size:100px">Read it<br><em>your way.</em></h1>
</div>
{phone("reading-menu", "left:72px; top:470px; width:400px")}
<div class="floor"></div>
<ul class="opts">
  <li><b>Arabic size</b><span class="sz"><i>−</i>100%<i>+</i></span></li>
  <li><b>Transliteration</b><span class="tg on"></span></li>
  <li><b>Translation</b><span class="tg on"></span></li>
  <li><b>Auto-advance</b><span class="tg on"></span></li>
  <li><b>Recitation</b><span class="tg"></span></li>
  <li><b>Haptics</b><span class="tg on"></span></li>
</ul>
<p class="said">Large Arabic for older eyes, the Latin letters for those still learning, a light tap with every count.</p>
""", stamp="Reading", css=FLOOR + """
.floor{height:240px}
.opts{position:absolute;left:540px;right:72px;top:470px;list-style:none;border-top:1px solid var(--line)}
.opts li{display:flex;align-items:center;justify-content:space-between;padding:24px 0;border-bottom:1px solid var(--line)}
.opts b{font:400 38px/1 'Instrument Serif'}
.tg{width:66px;height:38px;border-radius:99px;background:rgba(255,255,255,.14);position:relative}
.tg::after{content:"";position:absolute;left:5px;top:5px;width:28px;height:28px;border-radius:50%;background:rgba(255,255,255,.7)}
.tg.on{background:#4a6cff}.tg.on::after{left:33px;background:#fff}
.sz{display:flex;align-items:center;gap:16px;font:600 20px/1 Inter}
.sz i{width:38px;height:38px;border-radius:50%;border:1px solid rgba(255,255,255,.25);display:grid;place-items:center;font-style:normal;font-weight:400}
.said{position:absolute;left:540px;right:72px;top:1000px;font:italic 400 30px/1.3 'Instrument Serif';color:var(--text-2)}
.foot{left:540px;width:auto;right:72px}
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
