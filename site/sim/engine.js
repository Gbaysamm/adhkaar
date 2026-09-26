/* Adhkaar simulator. The screens are the app's own, and so is what each tap does: SiteCrawlTest
   explored the app, tapping every button and recording where it led (sim/crawl.js). A few phone
   states the app doesn't draw itself (lock screen, another app, the dialer) are drawn here. */
(function () {
  "use strict";
  var CRAWL = window.SIM_CRAWL || {};
  var STATIC = window.SIM_SCREENS || {};
  var screenEl = document.getElementById("screen");
  var pager = document.getElementById("pager"), posEl = document.getElementById("pos");
  var upBtn = document.getElementById("up"), downBtn = document.getElementById("down");
  var toastEl = document.getElementById("toast");
  var W = 786, H = 1704;

  // A place in the simulator: a crawled screen ("app"/"session" + id), a static render, or a mock.
  var cur = { src: "app", id: "today-1" };
  var history = [];

  function st(p) { return CRAWL[p.src] && CRAWL[p.src][p.id]; }
  function clean(s) { return (s || "").replace(/[⁦-⁩]/g, ""); }
  function toast(m) { toastEl.textContent = m || ""; }
  function vibrate(p) { try { navigator.vibrate && navigator.vibrate(p); } catch (e) {} }
  function pct(v, of) { return (v / of * 100).toFixed(3) + "%"; }

  // ---- what the panel says about where you are ----
  var NOTE = {
    today: ["Today", "Your day at a glance", "The sky shows where the day is, the card shows what's due, then your salah reminders, collections and the Hijri calendar.", "Tap Continue, the streak, the day's reminder, or scroll."],
    adhkaar: ["Adhkaar", "Every collection", "Morning and evening, after salah, before sleep, on waking, everyday duas, favourites and your own duas.", "Open a collection, or tap the heart on any dhikr."],
    insights: ["Insights", "Your progress", "Your streak, this month's sessions, the calendar with Hijri dates, and your week.", "Tap a day in the calendar, or switch Hijri and Gregorian."],
    settings: ["Settings", "Make it yours", "The mode, your times, reminders, the Hijri calendar, language and reading options. Help is at the top.", "Open the Guide, Test kit or Contact us, or change a setting."],
    session: ["Morning adhkaar", "Read, then tap to count", "Tap the ring or the card to count; each dhikr moves on by itself. The heart saves a favourite; Tr changes the reading options.", "Count, or tap Take a break."],
    brk: ["Take a break", "A pause, never a skip", "Only breaks that end before the adhkaar time closes are offered. Before pausing, the app asks once more.", "Choose a time, then hold to pause."],
    lock: ["6:00 AM · Lock screen", "The morning adhkaar arrive", "In Full screen and Lockdown the phone rings for a minute and the adhkaar open by themselves, even with the screen locked.", "Tap the notification, or wait a moment."],
    other: ["Another app", "Opening something else…", "In Lockdown, any other app is covered until the adhkaar are done or their time ends.", "Wait a moment."],
    lockdown: ["Lockdown", "Your morning adhkaar is waiting", "No close button, and back doesn't work. Calls and emergencies always do. The only way out is a short break.", "Return to your adhkaar, or tap Call or emergency."],
    dialer: ["Phone", "Calls always work", "Even in Lockdown the phone app opens and stays open as long as you need it.", "Tap anywhere to go back."],
    onbreak: ["On a break", "Your phone is free", "Nothing rings or blocks until the break ends. Then the alert rings again, and the adhkaar come back.", "Tap the notification to go back early."],
    "popup-salah": ["6:35 PM · Salah reminder", "Time to get ready for Maghrib", "A frosted card over whatever is open, or the lock screen, with a chime at alarm volume and a vibration.", "Tap I'm getting ready."],
    "popup-after": ["1:22 PM · After Dhuhr", "Adhkaar after salah", "Collections remind you with a card like this. After Fajr and Maghrib the three Quls are said three times.", "Tap Open or Later."],
    complete: ["Done", "Your day begins in His care", "The session is recorded, the streak grows, and any Lockdown lifts.", "Tap Done."],
    gentle: ["Gentle mode", "One soft reminder", "Gentle plays one chime and shows a notification. It never rings again.", "Tap the notification to begin."],
  };
  function noteKey() {
    if (cur.src === "mock" || cur.src === "static") return cur.id;
    var id = String(cur.id);
    if (/^b\d/.test(id)) return "brk";
    if (/^(s\d|s-)/.test(id)) return "session";
    if (/^(adhkaar|shelf|after-salah|before-sleep|on-waking|everyday|favourites|my-duas|dua-editor)/.test(id)) return "adhkaar";
    if (/^insights/.test(id)) return "insights";
    if (/^(settings|guide|test-kit|contact|salah-)/.test(id)) return "settings";
    return "today";
  }
  function notes(key) {
    var n = NOTE[key] || NOTE.today;
    document.getElementById("n-where").textContent = n[0];
    document.getElementById("n-title").textContent = n[1];
    document.getElementById("n-text").textContent = n[2];
    document.getElementById("n-next").textContent = n[3];
  }

  function go(place, opts) {
    opts = opts || {};
    if (!opts.replace) history.push(cur);
    cur = place;
    render(opts);
  }
  function back() { cur = history.pop() || HOME(); render({}); }
  function HOME() { return { src: "app", id: "today-1" }; }
  function SESSION() { return { src: "app", id: "s0" }; }

  function link(to, label) {
    if (to === "@back") return back;
    if (to === "@home") return function () { toast("Closed. In Full screen the adhkaar come back at your next unlock."); history = []; go(HOME(), { replace: true }); };
    if (to === "@complete") return function () { toast("Skipping ahead to the end of the session."); go({ src: "static", id: "complete" }); };
    if (to && CRAWL.app[to]) {
      var count = /^\d+ of \d+$/.test(clean(label));
      return function () { if (count) vibrate(12); go({ src: "app", id: to }, { quiet: count }); };
    }
    if (/^Share/.test(clean(label))) return function () { toast("In the app this opens your phone's share sheet."); };
    return null;
  }

  function render(opts) {
    opts = opts || {};
    if (!opts.keepToast) toast(opts.toast || "");
    screenEl.innerHTML = "";
    pager.hidden = true;
    notes(opts.note || noteKey());
    if (cur.src === "mock") { screenEl.appendChild(mock(cur.id)); return; }
    var frame = document.createElement("div");
    frame.className = "frame " + (opts.dir || (opts.quiet ? "" : "enter"));
    var img = document.createElement("img");
    img.draggable = false;
    frame.appendChild(img);
    if (cur.src === "static") {
      img.src = "sim/img/" + cur.id + ".webp";
      img.alt = (NOTE[cur.id] || ["", ""])[1];
      staticHots(frame);
    } else {
      var s = st(cur);
      img.src = "sim/app/" + s.id + ".webp";
      img.alt = NOTE[noteKey()][1];
      s.hot.forEach(function (h) {
        addHot(frame, h, h.to === s.id ? null : link(h.to, h.l), opts.hint);
      });
      if (s.hot.some(function (h) { return clean(h.l).indexOf("Stay and finish") === 0; })) addHold(frame, s.hot);
      if (s.scroll || s.up) {
        pager.hidden = false;
        upBtn.disabled = !s.up;
        downBtn.disabled = !s.scroll;
        var n = 1, u = s; while (u.up) { n++; u = CRAWL.app[u.up]; }
        var total = n, d = s; while (d.scroll) { total++; d = CRAWL.app[d.scroll]; }
        posEl.textContent = n + " / " + total;
      }
    }
    if (opts.banner) banner(frame, "Morning adhkaar", "It's time · one soft chime, it won't ring again", function () { go(SESSION()); });
    screenEl.appendChild(frame);
  }

  function addHot(frame, h, act, hint) {
    var b = document.createElement("button");
    b.type = "button"; b.className = "hot" + (act ? "" : " dead");
    b.style.left = pct(h.x, W); b.style.top = pct(h.y, H); b.style.width = pct(h.w, W); b.style.height = pct(h.h, H);
    var label = clean(h.l || h.label);
    b.setAttribute("aria-label", label.slice(0, 80));
    if (hint && label.indexOf(hint) === 0) b.classList.add("hint");
    b.addEventListener("click", function (e) {
      ripple(e, frame);
      if (act) act(); else toast("Nothing more to see there in this demo.");
    });
    frame.appendChild(b);
  }

  // Static renders (Lockdown, the reminder cards, Done) keep their hand-wired buttons.
  function staticHots(frame) {
    var data = STATIC[cur.id]; if (!data) return;
    data.hot.forEach(function (h) {
      var L = clean(h.label), act = null;
      if (cur.id === "lockdown") act = /^Return/.test(L) ? function () { go(SESSION()); } : /^Call/.test(L) ? function () { go({ src: "mock", id: "dialer" }); } : null;
      if (cur.id === "popup-salah") { if (!/^I.m getting ready/.test(L)) return; act = function () { history = []; go(HOME(), { replace: true }); }; }
      if (cur.id === "popup-after") { if (!/^(Open|Later)/.test(L)) return; act = function () { if (/^Open/.test(L)) toast("In the app this opens the after-salah adhkaar."); history = []; go(HOME(), { replace: true }); }; }
      if (cur.id === "complete") act = /^Done/.test(L) ? function () { history = []; go(HOME(), { replace: true }); } : null;
      addHot(frame, h, act, cur.id === "popup-salah" ? "I'm getting ready" : null);
    });
  }

  function ripple(e, host) {
    var r = host.getBoundingClientRect();
    var s = document.createElement("span"); s.className = "ripple";
    s.style.left = (e.clientX - r.left) + "px"; s.style.top = (e.clientY - r.top) + "px";
    host.appendChild(s); setTimeout(function () { s.remove(); }, 520);
  }

  // Hold to pause sits under "Stay and finish" and isn't a tap target, so it's placed from it.
  function addHold(frame, hots) {
    var stay = hots.filter(function (h) { return clean(h.l).indexOf("Stay and finish") === 0; })[0];
    var y = stay.y + stay.h + 16, h = 104;
    var fill = document.createElement("div"); fill.className = "holdfill"; fill.innerHTML = "<i></i>";
    var b = document.createElement("button"); b.type = "button"; b.className = "hot hint"; b.setAttribute("aria-label", "Hold to pause");
    [fill, b].forEach(function (el) { el.style.left = pct(stay.x, W); el.style.top = pct(y, H); el.style.width = pct(stay.w, W); el.style.height = pct(h, H); });
    b.style.borderRadius = "999px";
    var raf = 0, t0 = 0, q = 0;
    function step(t) {
      var p = Math.min(1, (t - t0) / 5000);
      fill.firstChild.style.width = p * 100 + "%";
      var nq = Math.floor(p * 4); if (nq > q && nq < 4) { q = nq; vibrate(10); }
      if (p >= 1) { raf = 0; vibrate(40); go({ src: "mock", id: "onbreak" }, { replace: true }); return; }
      raf = requestAnimationFrame(step);
    }
    function down(e) { e.preventDefault(); b.classList.remove("hint"); t0 = performance.now(); q = 0; toast("Keep holding…"); raf = requestAnimationFrame(step); }
    function up() { if (!raf) return; cancelAnimationFrame(raf); raf = 0; fill.firstChild.style.width = "0"; toast("Let go early, so nothing happened. You're still reading."); }
    b.addEventListener("pointerdown", down);
    ["pointerup", "pointerleave", "pointercancel"].forEach(function (ev) { b.addEventListener(ev, up); });
    b.addEventListener("keydown", function (e) { if ((e.key === " " || e.key === "Enter") && !e.repeat) down(e); });
    b.addEventListener("keyup", function (e) { if (e.key === " " || e.key === "Enter") up(); });
    frame.appendChild(fill); frame.appendChild(b);
  }

  function banner(host, title, text, onTap) {
    var wrap = document.createElement("div"); wrap.className = "banner";
    wrap.innerHTML = '<div class="n-card" role="button" tabindex="0"><img src="img/icon.webp" alt=""><div><b>' + title + "</b><span>" + text + "</span></div></div>";
    wrap.firstChild.addEventListener("click", onTap);
    wrap.firstChild.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onTap(); } });
    host.appendChild(wrap);
  }

  function chats(extra) {
    return "<header>Chats</header>" + ["Aisha", "Study group", "Ibrahim", "Family", "Musa"].map(function (n, i) {
      return '<div class="row"><span class="av" style="background:hsl(' + (i * 70 + 20) + ',45%,45%)"></span><div><b>' + n + "</b><span>" + extra + "</span></div></div>";
    }).join("");
  }

  function mock(kind) {
    var el = document.createElement("div");
    if (kind === "lock") {
      el.className = "mock lock enter";
      el.innerHTML = '<div class="t">6:00</div><div class="d">Sunday, 27 September</div><div class="n-card ringing" role="button" tabindex="0"><img src="img/icon.webp" alt=""><div><b>Morning adhkaar</b><span>It\'s time · ringing · tap to begin</span></div></div><div class="bottom">Swipe up to unlock</div>';
      var open = function () { if (cur.src === "mock" && cur.id === "lock") go(SESSION(), { replace: true, hint: "0 of 1" }); };
      el.querySelector(".n-card").addEventListener("click", open);
      setTimeout(open, 3500);
    } else if (kind === "other") {
      el.className = "mock other enter"; el.innerHTML = chats("Did you see the message from…");
      setTimeout(function () { if (cur.src === "mock" && cur.id === "other") go({ src: "static", id: "lockdown" }, { replace: true }); }, 1600);
    } else if (kind === "dialer") {
      el.className = "mock dialer enter";
      el.innerHTML = '<div style="font-size:28px;letter-spacing:2px">112</div><div style="color:var(--text-3);font-size:13px">Emergency number</div><div class="keys">' + "123456789*0#".split("").map(function (k) { return "<i>" + k + "</i>"; }).join("") + "</div>";
      el.addEventListener("click", back);
    } else if (kind === "onbreak") {
      el.className = "mock other enter"; el.innerHTML = chats("Your phone is free during the break");
      banner(el, "On a break", "Until 6:32 AM · must be done by 7:29 AM. Tap to go back early.", function () { go(SESSION(), { replace: true }); });
    }
    return el;
  }

  // Scrolling: a wheel turn, a swipe or the arrows move a screen at a time.
  // Scrolling moves between the frames of one page, so it doesn't add to Back's history.
  function scroll(d) {
    if (cur.src !== "app") return;
    var s = st(cur), to = d > 0 ? s.scroll : s.up;
    if (!to) return;
    cur = { src: "app", id: to };
    render({ dir: d > 0 ? "up" : "down", keepToast: true });
  }
  var wheelAt = 0;
  screenEl.addEventListener("wheel", function (e) {
    if (cur.src !== "app" || !(st(cur).scroll || st(cur).up)) return;
    e.preventDefault();
    var now = Date.now(); if (now - wheelAt < 450 || Math.abs(e.deltaY) < 8) return;
    wheelAt = now; scroll(e.deltaY > 0 ? 1 : -1);
  }, { passive: false });
  var y0 = null;
  screenEl.addEventListener("touchstart", function (e) { y0 = e.touches[0].clientY; }, { passive: true });
  screenEl.addEventListener("touchend", function (e) { if (y0 === null) return; var dy = y0 - e.changedTouches[0].clientY; y0 = null; if (Math.abs(dy) > 40) scroll(dy > 0 ? 1 : -1); }, { passive: true });
  upBtn.addEventListener("click", function () { scroll(-1); });
  downBtn.addEventListener("click", function () { scroll(1); });

  // Moments of the day.
  var SCENARIOS = [
    ["The morning alarm", "6:00 AM, after Fajr is prayed", function () { go({ src: "mock", id: "lock" }); }],
    ["Count the adhkaar", "Read, tap, and it moves on", function () { go(SESSION(), { hint: "0 of 1" }); }],
    ["Take a break", "A pause, never a skip", function () { go(SESSION(), { hint: "Take a break" }); }],
    ["Lockdown", "Try to open another app", function () { go({ src: "mock", id: "other" }); }],
    ["Salah reminder", "6:35 PM, Maghrib", function () { go({ src: "static", id: "popup-salah" }); }],
    ["After salah", "1:22 PM, after Dhuhr", function () { go({ src: "static", id: "popup-after" }); }],
    ["Gentle mode", "Just a chime and a notification", function () { go(HOME(), { banner: true, note: "gentle" }); }],
    ["Explore the app", "Every tab, button and setting", function () { go(HOME()); }],
  ];
  var list = document.getElementById("scenarios");
  SCENARIOS.forEach(function (s, i) {
    var li = document.createElement("li");
    li.innerHTML = '<button type="button" class="scenario"><span class="n">' + (i + 1) + "</span><span><b>" + s[0] + "</b><span>" + s[1] + "</span></span></button>";
    li.firstChild.addEventListener("click", function () {
      list.querySelectorAll(".scenario").forEach(function (b) { b.removeAttribute("aria-current"); });
      li.firstChild.setAttribute("aria-current", "true");
      history = [];
      s[2]();
      if (innerWidth < 720) document.querySelector(".device").scrollIntoView({ behavior: "smooth", block: "center" });
    });
    list.appendChild(li);
  });
  list.querySelectorAll(".scenario")[7].setAttribute("aria-current", "true");
  render({});
})();
