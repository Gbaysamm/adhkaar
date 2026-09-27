"""Writes the social posts (marketing/posts/*.html) and exports each to marketing/export/*.png
at 2x with headless Edge. Run from anywhere: python marketing/build.py"""
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
POSTS = os.path.join(HERE, "posts")
EXPORT = os.path.join(HERE, "export")
EDGE = [p for p in (r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", r"C:\Program Files\Microsoft\Edge\Application\msedge.exe") if os.path.exists(p)][0]


def page(body, night=False, stamp=""):
    cls = "post night-post" if night else "post"
    sky = "night" if night else "dawn"
    return f"""<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="../post.css"></head>
<body><div class="{cls}"><div class="sky {sky}"></div><div class="grain"></div>
<div class="top"><div class="brand"><img src="../assets/icon.webp" alt=""><span>Adhkaar</span></div><div class="stamp">{stamp}</div></div>
{body}
<div class="foot"><span>Morning and evening adhkaar, on time.</span><b><i></i>Free for Android</b></div>
</div></body></html>"""


def phone(img, style):
    return f'<div class="phone" style="{style}"><div class="scr"><img src="../assets/{img}.webp" alt=""></div></div>'


CHAT = """<div class="phone" style="left:538px; top:520px; width:470px">
  <div class="scr chat">
    <div class="cs"><span>8:31</span><span>●●●</span></div>
    <div class="ch"><span class="av">☾</span><div><b>Masjid Nur · Announcements</b><em>Aisha, Ibrahim, Musa, You and 180 others</em></div></div>
    <div class="cb">
      <span class="day">Today</span>
      <div class="m in"><u>Imam Yusuf</u>Don't forget your morning adhkaar today, a few minutes to start the day with Allah 🌅<s>6:02 AM</s></div>
      <div class="m out">In sha Allah, after I check a few things<s>6:04 AM ✓✓</s></div>
      <div class="m in"><u class="c3">Ibrahim</u>Did you all see this? 😂<s>6:21 AM</s></div>
      <div class="m out">😂 send the rest<s>6:22 AM ✓✓</s></div>
      <div class="m in"><u class="c4">Musa</u>Traffic on the bridge already, leave early<s>7:48 AM</s></div>
    </div>
  </div>
</div>
<style>
.chat { background:#0b141a; display:flex; flex-direction:column; font-family: Inter, sans-serif; }
.chat .cs { display:flex; justify-content:space-between; padding:26px 30px 8px; background:#1f2c34; font:600 17px/1 Inter; color:#e9edef; }
.chat .ch { display:flex; gap:14px; align-items:center; padding:12px 20px 16px; background:#1f2c34; }
.chat .ch .av { width:50px; height:50px; border-radius:50%; background:radial-gradient(circle at 30% 25%, #2f6b57, #163a30); color:#f2cf8a; display:grid; place-items:center; font-size:26px; flex:none; }
.chat .ch b { display:block; font:600 20px/1.2 Inter; color:#e9edef; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.chat .ch em { display:block; font:400 15px/1.3 Inter; color:#8696a0; font-style:normal; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:320px; }
.chat .cb { flex:1; display:flex; flex-direction:column; gap:10px; padding:18px 18px; }
.chat .day { align-self:center; font:500 15px/1 Inter; color:#8696a0; background:#182229; padding:8px 14px; border-radius:10px; margin-bottom:6px; }
.chat .m { max-width:84%; padding:10px 12px 8px; border-radius:12px; font:400 19px/1.36 Inter; color:#e9edef; }
.chat .m.in { align-self:flex-start; background:#202c33; border-top-left-radius:0; }
.chat .m.out { align-self:flex-end; background:#005c4b; border-top-right-radius:0; }
.chat .m u { display:block; text-decoration:none; font:600 16px/1.3 Inter; color:#53bdeb; margin-bottom:2px; }
.chat .m u.c3 { color:#7fd6a8; } .chat .m u.c4 { color:#d6a3ff; }
.chat .m s { display:block; text-decoration:none; text-align:right; font:400 13px/1 Inter; color:rgba(233,237,239,.6); margin-top:6px; }
</style>"""

POST_LIST = {
    # 1. The problem, told as a morning in a group chat.
    "01-the-phone-wins": page(f"""
<div class="head"><h1 class="h">We know the adhkaar.<br><em>The phone wins anyway.</em></h1>
<p class="sub">The reminder arrives in the group. We mean to. Then the morning is gone.</p></div>
{CHAT}
<div class="note" style="left:72px; top:860px; width:400px"><div class="k">8:30 AM</div><div class="v">Missed again.</div><p>The phone was in hand the whole time.</p></div>
""", stamp="A familiar morning"),

    # 2. After Fajr, the adhkaar are already open.
    "02-after-fajr": page(f"""
<div class="head"><h1 class="h">The adhkaar open<br><em>after you pray.</em></h1>
<p class="sub">Ten minutes after the Fajr congregation, they're on your screen, counter ready. Tap, and each one moves on by itself.</p></div>
{phone("s2", "left:538px; top:560px")}
<div class="note" style="left:72px; top:860px; width:400px"><div class="k">6:00 AM</div><div class="v">Surah al-Ikhlas, ×3</div><p>No searching, no scrolling. Just the words.</p></div>
""", stamp="After Fajr"),

    # 3. Lockdown: the phone waits.
    "03-lockdown": page(f"""
<div class="head"><h1 class="h">The phone can wait<br><em>a few minutes.</em></h1>
<p class="sub">Lockdown pauses your other apps until the adhkaar are read, or their time ends. Calls and emergencies always work.</p></div>
{phone("lockdown", "left:538px; top:560px")}
<div class="note" style="left:72px; top:860px; width:400px"><div class="k">Lockdown</div><div class="v">For the days the phone keeps winning.</div></div>
""", stamp="The strongest help"),

    # 4. Salah: the reminder that pops up.
    "04-salah": page(f"""
<div class="head"><h1 class="h">A reminder<br><em>you won't scroll past.</em></h1>
<p class="sub">At your masjid's time, a card comes up over whatever you're doing, even the lock screen.</p></div>
{phone("popup", "left:538px; top:560px")}
<div class="hadith" style="position:absolute; left:72px; top:840px; width:400px"><div class="orn">✦</div><div class="ar">الصَّلَاةُ عَلَى وَقْتِهَا</div><div class="en">The most beloved deed to Allah: salah at its proper time.</div><div class="src">Sahih al-Bukhari 527</div></div>
""", night=True, stamp="6:35 PM · Maghrib"),

    # 5. Busy: a pause, never a skip.
    "05-break": page(f"""
<div class="head"><h1 class="h">A pause,<br><em>never a skip.</em></h1>
<p class="sub">Busy when the adhkaar open? Take 10, 20, 30 or 60 minutes, and they come back. Pausing takes a five-second hold.</p></div>
{phone("b2", "left:500px; top:600px; width:380px; transform:rotate(-6deg); z-index:1")}
{phone("b3", "left:660px; top:640px; width:380px; transform:rotate(5deg); z-index:2")}
<div class="hadith" style="position:absolute; left:72px; top:800px; width:400px"><div class="orn">✦</div><div class="ar" style="font-size:32px">مَثَلُ الَّذِي يَذْكُرُ رَبَّهُ وَالَّذِي لَا يَذْكُرُ رَبَّهُ مَثَلُ الْحَيِّ وَالْمَيِّتِ</div><div class="en" style="font-size:27px">The one who remembers his Lord and the one who does not are like the living and the dead.</div><div class="src">Sahih al-Bukhari 6407</div></div>
""", stamp="Life gets busy"),

    # 6. The habit, as a streak.
    "06-streak": page(f"""
<div class="head"><h1 class="h"><em>22 days</em><br>in a row.</h1>
<p class="sub">Every finished morning and evening adds to your streak. A missed day becomes something you notice, and don't want.</p></div>
{phone("streak", "left:538px; top:560px")}
<div class="note" style="left:72px; top:860px; width:400px"><div class="k">The habit</div><div class="v">Don't let today be the gap.</div></div>
""", night=True, stamp="Consistency"),
}


def main():
    os.makedirs(POSTS, exist_ok=True)
    os.makedirs(EXPORT, exist_ok=True)
    for name, html in POST_LIST.items():
        src = os.path.join(POSTS, name + ".html")
        open(src, "w", encoding="utf-8").write(html)
        out = os.path.join(EXPORT, name + ".png")
        subprocess.run([EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
                        "--window-size=1080,1350", "--virtual-time-budget=4000", f"--screenshot={out}", "file:///" + src.replace("\\", "/")],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(name, os.path.exists(out))


if __name__ == "__main__":
    main()
