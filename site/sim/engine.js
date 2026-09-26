/* Adhkaar simulator: the app's real screens (rendered from its code by SiteCaptureTest) with the
   places you can tap, wired to what the app does. Longer pages scroll a screen at a time. */
(function () {
  "use strict";
  var SCREENS = window.SIM_SCREENS || {};
  var screenEl = document.getElementById("screen");
  var pager = document.getElementById("pager"), posEl = document.getElementById("pos");
  var upBtn = document.getElementById("up"), downBtn = document.getElementById("down");
  var toastEl = document.getElementById("toast");
  var W = 786, H = 1704;

  // A view is one screen of the app; a page that scrolls has several frames.
  function frames(view) {
    if (SCREENS[view]) return [view];
    var list = [], i = 1;
    while (SCREENS[view + "-" + i]) { list.push(view + "-" + i); i++; }
    return list;
  }

  var NOTES = {
    lock: ["6:00 AM · Lock screen", "The morning adhkaar arrive", "In Full screen and Lockdown the phone rings for a minute and the adhkaar open by themselves, even with the screen locked.", "Tap the notification, or wait a moment."],
    today: ["Today", "Your day at a glance", "The sky shows where the day is, the card shows what's due, and below it are your salah reminders, collections and the Hijri calendar.", "Tap Continue, or scroll down."],
    "session-0": ["Morning adhkaar", "Read, then tap to count", "Tap the ring or the card to count. Each dhikr moves on by itself when its count is reached. The app waits for the words to be read before counting.", "Tap the counter."],
    session: ["Morning adhkaar", "Counting", "Surah al-Ikhlas is said three times in the morning and evening. Watch the ring fill.", "Keep tapping, or take a break."],
    "break-1": ["Take a break", "Need a moment?", "Only breaks that end before the adhkaar time closes are offered, so they're still said today.", "Choose 20 min."],
    "break-2": ["Take a break", "Back at a set time", "The alert rings again when the break ends.", "Tap Pause for 20 minutes."],
    "break-3": ["Before you pause", "One more gentle ask", "How much is left, your streak, and a hadith. Pausing takes a five-second hold.", "Press and hold the bottom button for 5 seconds, or Stay and finish."],
    onbreak: ["On a break", "Your phone is free", "Nothing rings or blocks until the break ends. Then the alert rings again, and in Full screen and Lockdown the adhkaar come back.", "Tap the notification to go back early."],
    complete: ["Done", "Your day begins in His care", "The session is recorded, the streak grows, and any Lockdown lifts.", "Tap Done."],
    other: ["Another app", "Opening something else…", "In Lockdown, any other app is covered until the adhkaar are done or their time ends.", "Wait a moment."],
    lockdown: ["Lockdown", "Your morning adhkaar is waiting", "There's no close button and back doesn't work. Calls and emergencies always do. The only way out is a short break.", "Return to your adhkaar, or tap Call or emergency."],
    dialer: ["Phone", "Calls always work", "Even in Lockdown the phone app opens and stays open for as long as you need it.", "Tap anywhere to go back."],
    "popup-salah": ["6:35 PM · Salah reminder", "Time to get ready for Maghrib", "A frosted card over whatever is open (or the lock screen), with a chime at alarm volume and a vibration.", "Tap I'm getting ready."],
    "popup-after": ["1:22 PM · After Dhuhr", "Adhkaar after salah", "Collections remind you with a card like this. After Fajr and Maghrib the three Quls are said three times.", "Tap Open or Later."],
    adhkaar: ["Adhkaar tab", "Every collection", "Morning and evening, after salah, before sleep, on waking and everyday duas, with your favourites and your own duas.", "Open Morning."],
    "shelf-morning": ["Morning adhkaar", "The list, with sources", "Every dhikr with its count and reference. Tap Begin to read them as a session.", "Tap Begin, or scroll."],
    insights: ["Insights", "Your progress", "Streak, sessions this month, the calendar with Hijri dates, and your week.", "Scroll down."],
    settings: ["Settings", "Make it yours", "Pick the mode, the times, the reminders and the reading options. Help is at the top.", "Open Guide, Test kit or Contact us, or scroll to Salah reminders."],
    "salah-reminders": ["Salah reminders", "Your masjid's times", "Tap a time to change it. Your times replace the defaults, and each reminder can be switched off.", "Tap Back."],
    guide: ["Guide", "How it all works", "The times, the three modes with their trade-offs, breaks and the rest, in plain words.", "Scroll, then tap Back."],
    "test-kit": ["Test kit", "Try every alert now", "For testers who can't wait for Fajr: each alert arrives ten seconds after tapping, and the phone's permissions are checked.", "Scroll, then tap Back."],
    contact: ["Contact us", "Tell us, in the app", "A message goes straight to the maintainers with your phone's details. No account, no GitHub.", "Tap Back."],
    gentle: ["Gentle mode", "One soft reminder", "Gentle plays one chime and shows a notification. It never rings again.", "Tap the notification to begin."],
  };

  var SCENARIOS = [
    { title: "The morning alarm", sub: "6:00 AM, after Fajr is prayed", run: function () { go("lock"); } },
    { title: "Count the adhkaar", sub: "Read, tap, and it moves on", run: function () { go("session-0", { hint: "0 of 1" }); } },
    { title: "Take a break", sub: "A pause, never a skip", run: function () { go("session-4", { hint: "Take a break" }); } },
    { title: "Lockdown", sub: "Try to open another app", run: function () { go("other"); setTimeout(function () { if (state.view === "other") go("lockdown"); }, 1600); } },
    { title: "Salah reminder", sub: "6:35 PM, Maghrib", run: function () { go("popup-salah", { hint: "I'm getting ready" }); } },
    { title: "After salah", sub: "1:22 PM, after Dhuhr", run: function () { go("popup-after"); } },
    { title: "Gentle mode", sub: "Just a chime and a notification", run: function () { go("today", { banner: true }); } },
    { title: "Explore the app", sub: "Today, Adhkaar, Insights, Settings", run: function () { go("today"); } },
  ];

  var state = { view: "today", frame: 0, history: [], scenario: 7 };

  function toast(msg) { toastEl.textContent = msg || ""; }

  function notes(view) {
    var key = NOTES[view] ? view : /^session-[1-4]$/.test(view) ? "session" : view;
    var n = NOTES[key] || ["", "", "", ""];
    document.getElementById("n-where").textContent = n[0];
    document.getElementById("n-title").textContent = n[1];
    document.getElementById("n-text").textContent = n[2];
    document.getElementById("n-next").textContent = n[3];
  }

  function stripMarks(s) { return (s || "").replace(/[⁦-⁩]/g, ""); }

  // What tapping [label] does on [view]. Returns a function, or null for things outside the demo.
  function action(view, label) {
    var L = stripMarks(label);
    var starts = function (p) { return L.indexOf(p) === 0; };
    var tabs = { Today: "today", Adhkaar: "adhkaar", Insights: "insights", Settings: "settings" };
    if (tabs[L] && frames(tabs[L]).length && /^(today|adhkaar|insights|settings|shelf-morning)$/.test(view)) return function () { go(tabs[L], { replace: true }); };
    if (L === "Back") return back;
    switch (view) {
      case "today":
        if (starts("Continue") || starts("It's time")) return function () { go("session-0"); };
        break;
      case "adhkaar":
        if (starts("Morning")) return function () { go("shelf-morning"); };
        break;
      case "shelf-morning":
        if (starts("Begin")) return function () { go("session-0"); };
        break;
      case "settings":
        if (starts("Guide")) return function () { go("guide"); };
        if (starts("Test kit")) return function () { go("test-kit"); };
        if (starts("Contact us")) return function () { go("contact"); };
        if (starts("Salah reminders")) return function () { go("salah-reminders"); };
        if (starts("Gentle") || starts("Full screen") || starts("Lockdown")) return function () { toast("Mode chosen. Try the moments on the left to see each mode."); };
        break;
      case "session-0": if (starts("0 of 1")) return count("session-1"); break;
      case "session-1": if (starts("0 of 3")) return count("session-2"); break;
      case "session-2": if (starts("1 of 3")) return count("session-3"); break;
      case "session-3": if (starts("2 of 3")) return count("session-4"); break;
      case "session-4":
        if (starts("0 of 3")) return function () { toast("Skipping ahead to the end of the session."); go("complete"); };
        break;
      case "break-1":
        if (starts("20 min")) return function () { go("break-2", { replace: true }); };
        if (/^(10 min|30 min|1 hour)/.test(L)) return function () { toast("In this demo, choose 20 min."); };
        if (starts("Keep reading")) return function () { go("session-4", { replace: true }); };
        return "dead";
      case "break-2":
        if (starts("Pause for 20")) return function () { go("break-3", { replace: true }); };
        if (starts("Keep reading")) return function () { go("session-4", { replace: true }); };
        if (/^(10 min|30 min|1 hour)/.test(L)) return function () { toast("In this demo, choose 20 min."); };
        return "dead";
      case "break-3":
        if (starts("Stay and finish")) return function () { toast("Good. Keep going."); go("session-4", { replace: true }); };
        return "dead";
      case "complete": if (starts("Done")) return function () { go("today", { reset: true }); }; break;
      case "lockdown":
        if (starts("Return")) return function () { go("session-4"); };
        if (starts("Call")) return function () { go("dialer"); };
        break;
      case "popup-salah": if (starts("I'm getting ready")) return function () { go("today", { replace: true }); }; return "dead";
      case "popup-after": if (starts("Open") || starts("Later")) return function () { if (starts("Open")) toast("In the app this opens the after-salah adhkaar."); go("today", { replace: true }); }; return "dead";
      case "test-kit": if (starts("Try it")) return function () { toast("In the app, the alert arrives in 10 seconds."); setTimeout(function () { go("popup-salah"); }, 1200); }; break;
    }
    if (/^session-/.test(view)) {
      if (starts("Take a break")) return function () { go("break-1"); };
      if (starts("Close")) return function () { toast("Closed. In Full screen the adhkaar come back at your next unlock."); go("today", { reset: true }); };
    }
    return null;
  }

  function count(next) { return function () { vibrate(12); go(next, { replace: true, quiet: true }); }; }
  function vibrate(p) { try { navigator.vibrate && navigator.vibrate(p); } catch (e) {} }

  function go(view, opts) {
    opts = opts || {};
    if (!opts.replace && !opts.reset && state.view !== view) state.history.push(state.view);
    if (opts.reset) state.history = [];
    state.view = view; state.frame = 0;
    render(opts);
  }
  function back() { var v = state.history.pop() || "today"; state.view = v; state.frame = 0; render({}); }

  function pct(v, of) { return (v / of * 100).toFixed(3) + "%"; }

  function render(opts) {
    opts = opts || {};
    var view = state.view;
    notes(view);
    if (!opts.keepToast) toast(opts.toast || "");
    screenEl.innerHTML = "";
    var mock = mockFor(view);
    var fr = frames(view);
    pager.hidden = fr.length < 2;
    if (mock) { screenEl.appendChild(mock); return; }
    if (!fr.length) return;
    var name = fr[state.frame];
    var data = SCREENS[name];
    var div = document.createElement("div");
    div.className = "frame " + (opts.dir || (opts.quiet ? "" : "enter"));
    var img = document.createElement("img");
    img.src = "sim/img/" + name + ".webp"; img.alt = (NOTES[view] || NOTES[view.replace(/-\d+$/, "")] || ["", ""])[1] || view;
    img.draggable = false;
    div.appendChild(img);
    (data.hot || []).forEach(function (h) {
      var act = action(view, h.label);
      if (act === "dead" || (act === null && /^(break-|popup-)/.test(view))) return;
      var b = document.createElement("button");
      b.type = "button"; b.className = "hot" + (act ? "" : " dead");
      b.style.left = pct(h.x, W); b.style.top = pct(h.y, H); b.style.width = pct(h.w, W); b.style.height = pct(h.h, H);
      b.setAttribute("aria-label", stripMarks(h.label).slice(0, 80));
      if (opts.hint && stripMarks(h.label).indexOf(opts.hint) === 0) b.classList.add("hint");
      b.addEventListener("click", function (e) {
        ripple(e, div);
        if (act) act(); else toast("That opens more of the app than this demo covers.");
      });
      div.appendChild(b);
    });
    if (view === "break-3") addHold(div, data);
    if (opts.banner) addBanner(div, "Morning adhkaar", "It's time · one soft chime, it won't ring again", function () { go("session-0"); }, "gentle");
    screenEl.appendChild(div);
    if (fr.length > 1) {
      var bar = document.createElement("div"); bar.className = "scrollbar";
      var thumb = document.createElement("i"); var hgt = 100 / fr.length;
      thumb.style.height = hgt + "%"; thumb.style.top = (state.frame * hgt) + "%";
      bar.appendChild(thumb); screenEl.appendChild(bar);
      posEl.textContent = (state.frame + 1) + " / " + fr.length;
      upBtn.disabled = state.frame === 0; downBtn.disabled = state.frame === fr.length - 1;
    }
    if (opts.banner) notes("gentle");
  }

  function ripple(e, host) {
    var r = host.getBoundingClientRect();
    var s = document.createElement("span"); s.className = "ripple";
    s.style.left = (e.clientX - r.left) + "px"; s.style.top = (e.clientY - r.top) + "px";
    host.appendChild(s); setTimeout(function () { s.remove(); }, 520);
  }

  // The hold-to-pause pill sits under "Stay and finish" and isn't a tap target, so it's placed from it.
  function addHold(div, data) {
    var stay = (data.hot || []).filter(function (h) { return h.label.indexOf("Stay and finish") === 0; })[0];
    if (!stay) return;
    var y = stay.y + stay.h + 16, h = 104;
    var fill = document.createElement("div"); fill.className = "holdfill";
    [fill].forEach(function (el) { el.style.left = pct(stay.x, W); el.style.top = pct(y, H); el.style.width = pct(stay.w, W); el.style.height = pct(h, H); });
    fill.innerHTML = "<i></i>";
    var b = document.createElement("button");
    b.type = "button"; b.className = "hot hint"; b.setAttribute("aria-label", "Hold to pause for 20 minutes");
    b.style.left = pct(stay.x, W); b.style.top = pct(y, H); b.style.width = pct(stay.w, W); b.style.height = pct(h, H); b.style.borderRadius = "999px";
    var raf = 0, start = 0, q = 0;
    function step(t) {
      var p = Math.min(1, (t - start) / 5000);
      fill.firstChild.style.width = (p * 100) + "%";
      var nq = Math.floor(p * 4); if (nq > q && nq < 4) { q = nq; vibrate(10); }
      if (p >= 1) { vibrate(40); go("onbreak", { replace: true }); return; }
      raf = requestAnimationFrame(step);
    }
    function down(e) { e.preventDefault(); b.classList.remove("hint"); start = performance.now(); q = 0; toast("Keep holding…"); raf = requestAnimationFrame(step); }
    function up() { if (!raf) return; cancelAnimationFrame(raf); raf = 0; if (state.view === "break-3") { fill.firstChild.style.width = "0"; toast("Let go early, so nothing happened. You're still reading."); } }
    b.addEventListener("pointerdown", down);
    b.addEventListener("pointerup", up); b.addEventListener("pointerleave", up); b.addEventListener("pointercancel", up);
    b.addEventListener("keydown", function (e) { if ((e.key === " " || e.key === "Enter") && !e.repeat) down(e); });
    b.addEventListener("keyup", function (e) { if (e.key === " " || e.key === "Enter") up(); });
    b.addEventListener("click", function (e) { e.preventDefault(); });
    div.appendChild(fill); div.appendChild(b);
  }

  function addBanner(host, title, text, onTap, note) {
    var wrap = document.createElement("div"); wrap.className = "banner";
    wrap.innerHTML = '<div class="n-card" role="button" tabindex="0"><img src="img/icon.webp" alt=""><div><b>' + title + "</b><span>" + text + "</span></div></div>";
    var card = wrap.firstChild;
    card.addEventListener("click", onTap);
    card.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onTap(); } });
    host.appendChild(wrap);
  }

  // Phone states the app doesn't draw itself: the lock screen, another app, the dialer, a break.
  function mockFor(view) {
    var el = document.createElement("div");
    if (view === "lock") {
      el.className = "mock lock enter";
      el.innerHTML = '<div class="t">6:00</div><div class="d">Sunday, 27 September</div>' +
        '<div class="n-card ringing" role="button" tabindex="0"><img src="img/icon.webp" alt=""><div><b>Morning adhkaar</b><span>It\'s time · ringing · tap to begin</span></div></div>' +
        '<div class="bottom">Swipe up to unlock</div>';
      var open = function () { if (state.view === "lock") go("session-0", { hint: "0 of 1" }); };
      el.querySelector(".n-card").addEventListener("click", open);
      setTimeout(open, 3500);
      return el;
    }
    if (view === "other") {
      el.className = "mock other enter";
      el.innerHTML = "<header>Chats</header>" + ["Aisha", "Study group", "Ibrahim", "Family", "Musa"].map(function (n, i) {
        return '<div class="row"><span class="av" style="background:hsl(' + (i * 70 + 20) + ',45%,45%)"></span><div><b>' + n + "</b><span>Did you see the message from…</span></div></div>";
      }).join("");
      return el;
    }
    if (view === "dialer") {
      el.className = "mock dialer enter";
      el.innerHTML = '<div style="font-size:28px;letter-spacing:2px">112</div><div style="color:var(--text-3);font-size:13px">Emergency number</div><div class="keys">' +
        "123456789*0#".split("").map(function (k) { return "<i>" + k + "</i>"; }).join("") + "</div>";
      el.addEventListener("click", back);
      return el;
    }
    if (view === "onbreak") {
      el.className = "mock other enter";
      el.innerHTML = "<header>Chats</header>" + ["Aisha", "Study group", "Ibrahim"].map(function (n, i) {
        return '<div class="row"><span class="av" style="background:hsl(' + (i * 70 + 20) + ',45%,45%)"></span><div><b>' + n + "</b><span>Your phone is free during the break</span></div></div>";
      }).join("");
      addBanner(el, "On a break", "Until 6:32 AM · must be done by 7:29 AM. Tap to go back early.", function () { go("session-4"); });
      return el;
    }
    return null;
  }

  // Scrolling a long page: a wheel turn, a swipe, or the arrows move a screen at a time.
  function scrollBy(d) {
    var fr = frames(state.view);
    var next = Math.max(0, Math.min(fr.length - 1, state.frame + d));
    if (next === state.frame) return;
    state.frame = next;
    render({ dir: d > 0 ? "up" : "down", keepToast: true });
  }
  var wheelLock = 0;
  screenEl.addEventListener("wheel", function (e) {
    if (frames(state.view).length < 2) return;
    e.preventDefault();
    var now = Date.now(); if (now - wheelLock < 450 || Math.abs(e.deltaY) < 8) return;
    wheelLock = now; scrollBy(e.deltaY > 0 ? 1 : -1);
  }, { passive: false });
  var touchY = null;
  screenEl.addEventListener("touchstart", function (e) { touchY = e.touches[0].clientY; }, { passive: true });
  screenEl.addEventListener("touchend", function (e) {
    if (touchY === null) return;
    var dy = touchY - e.changedTouches[0].clientY; touchY = null;
    if (Math.abs(dy) > 40) scrollBy(dy > 0 ? 1 : -1);
  }, { passive: true });
  upBtn.addEventListener("click", function () { scrollBy(-1); });
  downBtn.addEventListener("click", function () { scrollBy(1); });

  // The moments list.
  var list = document.getElementById("scenarios");
  SCENARIOS.forEach(function (s, i) {
    var li = document.createElement("li");
    li.innerHTML = '<button type="button" class="scenario"><span class="n">' + (i + 1) + "</span><span><b>" + s.title + "</b><span>" + s.sub + "</span></span></button>";
    li.firstChild.addEventListener("click", function () {
      list.querySelectorAll(".scenario").forEach(function (b) { b.removeAttribute("aria-current"); });
      li.firstChild.setAttribute("aria-current", "true");
      state.history = [];
      s.run();
      if (innerWidth < 720) document.querySelector(".device").scrollIntoView({ behavior: "smooth", block: "center" });
    });
    list.appendChild(li);
  });
  list.querySelectorAll(".scenario")[7].setAttribute("aria-current", "true");
  render({});
})();
