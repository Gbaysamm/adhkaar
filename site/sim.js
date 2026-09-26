/* Adhkaar simulator: a small recreation of the Android app, driven by a clock.
   Times, wording and adhkaar come from the app itself (data.js). */
(function () {
  "use strict";
  const DATA = window.ADHKAAR_DATA || { strings: { en: {} }, adhkaar: [] };
  const root = document.getElementById("sim");
  if (!root) return;
  const $ = (sel, el = root) => el.querySelector(sel);
  const h = (html) => { const t = document.createElement("template"); t.innerHTML = html.trim(); return t.content.firstElementChild; };
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  // ---- the day, in minutes after midnight (the app's defaults; editable in Settings) ----
  const PRAYERS = [
    { id: "fajr", name: "Fajr", ar: "الفجر", t: 5 * 60 + 30 },
    { id: "sunrise", name: "Sunrise", ar: "الشروق", t: 6 * 60 + 30, notSalah: true },
    { id: "dhuhr", name: "Dhuhr", ar: "الظهر", t: 13 * 60 },
    { id: "asr", name: "Asr", ar: "العصر", t: 15 * 60 + 55 },
    { id: "maghrib", name: "Maghrib", ar: "المغرب", t: 18 * 60 + 35 },
    { id: "isha", name: "Isha", ar: "العشاء", t: 19 * 60 + 50 },
  ];
  const P = (id) => PRAYERS.find((p) => p.id === id);
  // Congregation 20 minutes after the time; morning opens 10 after it, evening 30 after it.
  const morningOpens = () => P("fajr").t + 30;
  const MORNING_ENDS = 7 * 60 + 30;
  const eveningOpens = () => P("asr").t + 50;
  const eveningBest = () => P("maghrib").t;
  const eveningCloses = () => P("isha").t;

  const S = {
    min: 5 * 60 + 50, mode: "full", locked: false, lang: "en",
    screen: "app", tab: "today", pending: null, // {type, enforced, paused}
    done: { morning: false, evening: false }, streak: 22,
    fired: new Set(), overlay: null, idx: 0, counts: {}, chosen: null,
  };

  const L = () => DATA.strings[S.lang] || DATA.strings.en || {};
  const t = (key, fallback) => (L()[key] || (DATA.strings.en || {})[key] || fallback || key).replace(/\\'/g, "'");
  const fmt = (m) => {
    m = ((m % 1440) + 1440) % 1440;
    let hh = Math.floor(m / 60), mm = m % 60;
    const ap = hh < 12 ? "AM" : "PM";
    hh = hh % 12 || 12;
    const s = `${hh}:${String(mm).padStart(2, "0")} ${ap}`;
    return S.lang === "ar" ? s.replace(/\d/g, (d) => "٠١٢٣٤٥٦٧٨٩"[d]).replace("AM", "ص").replace("PM", "م") : s;
  };
  const pname = (p) => (S.lang === "ar" ? p.ar : p.name);
  const buzz = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };

  // ---- rendering ----
  const phone = $(".phone");
  const screen = $(".screen");
  const log = document.getElementById("sim-log");
  const say = (msg) => { if (log) log.textContent = msg; };

  function aura() { return S.min >= 12 * 60 && S.min < 24 * 60 && !(S.pending && S.pending.type === "morning") ? "night" : "dawn"; }

  function status() { return `<div class="statusbar"><span>${fmt(S.min).replace(/ (AM|PM|ص|م)$/, "")}</span><span>● ● ▮</span></div>`; }

  function heroState() {
    const m = S.min;
    if (S.pending && !S.pending.paused) return { type: S.pending.type, due: true };
    if (!S.done.morning && m < MORNING_ENDS) return { type: "morning", due: m >= morningOpens(), at: morningOpens(), end: MORNING_ENDS };
    if (!S.done.evening && m < eveningCloses()) return { type: "evening", due: m >= eveningOpens(), at: eveningOpens(), end: eveningBest() };
    return { type: "morning", due: false, at: morningOpens(), end: MORNING_ENDS, tomorrow: true };
  }

  function prayerPills() {
    const m = S.min;
    const five = PRAYERS.filter((p) => !p.notSalah);
    const active = five.find((p) => m >= p.t && m < p.t + 30);
    const next = active || five.find((p) => p.t > m) || five[0];
    return `<div class="prayers">${PRAYERS.map((p) => {
      const lit = p === next;
      const label = lit ? (active ? (S.lang === "ar" ? "الآن" : "Now") : (S.lang === "ar" ? "التالية" : "Next")) : pname(p);
      return `<div class="pr ${lit ? "lit" : ""}"><span>${label}</span>${lit ? `<i>${pname(p)}</i>` : ""}<b>${fmt(p.t).replace(/ .*/, "")}</b></div>`;
    }).join("")}</div>`;
  }

  function skyArc() {
    const m = S.min, rise = P("sunrise").t, set = P("maghrib").t;
    const day = m >= rise && m <= set;
    const f = day ? (m - rise) / (set - rise) : (m > set ? (m - set) / (1440 - set + rise) : (m + 1440 - set) / (1440 - set + rise));
    const x = 8 + f * 84, y = 92 - Math.sin(f * Math.PI) * 72;
    return `<div class="skyarc"><svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><path d="M8 92 Q50 -40 92 92" fill="none" stroke="rgba(255,255,255,.35)" stroke-width=".6" stroke-dasharray="1.2 2.4" vector-effect="non-scaling-stroke"/></svg>
      <div class="sunmoon ${day ? "sun" : "moon"}" style="left:${x}%;top:${y}%"></div></div>
      <div class="arcfoot"><span>${t("today_morning", "Morning")}<b>${fmt(morningOpens())}</b></span><span style="text-align:end">${t("today_evening", "Evening")}<b>${fmt(eveningOpens())}</b></span></div>`;
  }

  function greeting() {
    if (S.lang === "ar") return `<div class="greet">السلام <em>عليكم</em></div>`;
    return `<div class="greet">Assalamu <em>alaikum</em></div>`;
  }

  function todayScreen() {
    const hs = heroState();
    const name = hs.type === "morning" ? t("today_hero_morning", "Morning adhkaar") : t("today_hero_evening", "Evening adhkaar");
    const five = PRAYERS.filter((p) => !p.notSalah);
    const last = [...five].reverse().find((p) => S.min >= p.t + 20 && S.min < p.t + 35);
    const moment = last ? `<div class="g card moment"><div class="badge">🕌</div><div><div class="cap acc">After ${pname(last)} · now</div><div style="font-weight:600">${t("moment_title_after_salah", "Adhkaar after salah")}</div><div class="sub" style="margin:0">${t("collection_after_salah_subtitle", "After each obligatory prayer")}</div></div></div>` : "";
    const hero = `<div class="g card">
        <div class="cap">${esc(name)}</div>
        ${hs.due ? `<div class="hero-time it">${S.lang === "ar" ? "حان الوقت" : "It's time"}</div>` : `<div class="hero-time">${fmt(hs.at)}</div>`}
        <div class="sub">${hs.tomorrow ? "Tomorrow · " : ""}${(t("hero_best_read_times", "Best read from %1$s until %2$s.")).replace("%1$s", fmt(hs.at || S.min)).replace("%2$s", fmt(hs.end || S.min))}</div>
        <button class="pill-btn" data-act="begin" ${hs.due ? "" : "disabled"}>${hs.due ? (S.pending ? "Continue" : "Begin now") + " →" : "Opens at " + fmt(hs.at)}</button>
      </div>`;
    const momentFirst = moment && !hs.due;
    return `<div class="scroll">${skyArc()}${greeting()}${momentFirst ? moment + hero : hero + moment}
      <div class="g card"><div class="cap">${t("today_day_reminder", "Reminder for today")}</div>
        <div class="ser" style="font-size:18px;margin-top:6px">Fasting three days of every month is like fasting all the time.</div>
        <div class="cap acc" style="margin-top:8px">Sahih al-Bukhari 1979</div></div>
      <div class="sec"><b>${S.lang === "ar" ? "تذكيرات الصلاة" : "Salah reminders"}</b><span>${nextSalahLabel()}</span></div>
      ${prayerPills()}
    </div>`;
  }

  function nextSalahLabel() {
    const five = PRAYERS.filter((p) => !p.notSalah);
    const active = five.find((p) => S.min >= p.t && S.min < p.t + 30);
    if (active) return `${pname(active)} now`;
    const n = five.find((p) => p.t > S.min) || five[0];
    let d = n.t - S.min; if (d < 0) d += 1440;
    return `${pname(n)} in ${Math.floor(d / 60)}h ${d % 60}m`;
  }

  function adhkaarScreen() {
    const shelf = (title, sub, done) => `<div class="list-row"><div class="badge">✦</div><div class="grow"><b>${title}</b><small>${sub}</small></div>${done ? `<span class="chip" style="color:var(--success)">✓ ${t("library_done_today", "Done today")}</span>` : "›"}</div>`;
    return `<div class="scroll"><div class="greet" style="font-size:30px">${t("tab_adhkaar", "Adhkaar")}</div>
      <div class="g card" style="padding:0">${shelf("Morning adhkaar", "After Fajr, until 7:30 AM · 21 adhkaar", S.done.morning)}${shelf("Evening adhkaar", "After Asr, until Maghrib · 20 adhkaar", S.done.evening)}</div>
      <div class="g card" style="padding:0">${shelf("After salah", "After each obligatory prayer")}${shelf("Before sleep", "As you lie down at night")}${shelf("On waking", "The first words of the day")}${shelf("Everyday duas", "For the moments of the day")}</div>
      <div class="g card" style="padding:0">${shelf("My duas", "Your own, added to morning or evening")}${shelf("Favourites", "The ones you return to")}</div></div>`;
  }

  function insightsScreen() {
    const cells = Array.from({ length: 28 }, (_, i) => `<i class="${["b", "b", "m", "b", "e", "b", "", "b", "m", "b", "b", "b", "e", "b", "b", "m", "b", "b", "b", "", "b", "b", "b", "e", "b", "b", "b", "b"][i]}"></i>`).join("");
    const bars = [80, 92, 70, 100, 85, 95, 60].map((v) => `<i style="height:${v}%"></i>`).join("");
    return `<div class="scroll"><div class="greet" style="font-size:30px">${t("tab_insights", "Insights")}</div>
      <div class="g card"><div class="cap">Streak</div><div class="hero-time">${S.streak + (S.done.morning && S.done.evening ? 1 : 0)} days</div><div class="sub" style="margin:0">Last 30 days: 26 of 30</div></div>
      <div class="g card"><div class="cap">This month</div><div class="heat">${cells}</div></div>
      <div class="g card"><div class="cap">By weekday</div><div class="weekbars">${bars}</div><div class="weeklab"><span>S</span><span>M</span><span>T</span><span>W</span><span>T</span><span>F</span><span>S</span></div></div></div>`;
  }

  function settingsScreen() {
    const opt = (id, title, desc) => `<button class="modeopt" role="radio" aria-checked="${S.mode === id}" data-mode="${id}"><b>${title}</b><span>${desc}</span></button>`;
    const rows = PRAYERS.map((p) => `<div class="list-row"><div class="grow"><b>${pname(p)}</b></div><button class="timechip" data-edit="${p.id}">✎ ${fmt(p.t)}</button></div>`).join("");
    return `<div class="scroll"><div class="greet" style="font-size:30px">${t("settings_title", "Settings")}</div>
      <div class="sec"><b>${t("settings_group_mode", "Mode")}</b></div>
      <div class="pad">${opt("gentle", t("mode_gentle", "Gentle"), t("mode_gentle_description", ""))}${opt("full", t("mode_full_screen", "Full screen"), t("mode_full_screen_description", ""))}${opt("lockdown", t("mode_lockdown", "Lockdown"), t("mode_lockdown_description", ""))}</div>
      <div class="sec"><b>${t("salah_reminders_title", "Salah reminders")}</b><span>Tap a time to change it</span></div>
      <div class="g card" style="padding:0">${rows}</div>
      <div class="sec"><b>Test kit</b></div>
      <div class="pad" style="display:grid;gap:8px"><button class="pill-btn soft" data-kit="morning">Test morning</button><button class="pill-btn soft" data-kit="salah">Salah reminder</button><button class="pill-btn soft" data-kit="after">After salah</button></div></div>`;
  }

  function tabs() {
    const b = (id, icon, key, fb) => `<button role="tab" aria-selected="${S.tab === id}" data-tab="${id}"><span>${icon}</span>${t(key, fb)}</button>`;
    return `<nav class="tabs" role="tablist">${b("today", "☀", "tab_today", "Today")}${b("adhkaar", "❖", "tab_adhkaar", "Adhkaar")}${b("insights", "↗", "tab_insights", "Insights")}${b("settings", "⚙", "tab_settings", "Settings")}</nav>`;
  }

  function sessionScreen() {
    const items = DATA.adhkaar;
    const d = items[S.idx] || items[0];
    const n = S.counts[d.id] || 0;
    const lock = S.mode === "lockdown" && S.pending && S.pending.enforced;
    const p = (S.idx + (n >= d.count ? 1 : 0)) / items.length;
    const circ = 2 * Math.PI * 46, off = circ * (1 - n / d.count);
    const typeName = (S.pending && S.pending.type) === "evening" ? t("session_overline_evening", "EVENING ADHKAAR") : t("session_overline_morning", "MORNING ADHKAAR");
    return `<div class="sess-top"><span class="cap acc">${typeName} · ${S.idx + 1}/${items.length}</span>${lock ? "" : `<button class="x" data-act="close" aria-label="Close">✕</button>`}</div>
      <div class="bar"><i style="width:${p * 100}%"></i></div>
      <div class="dhikr"><h4>${esc(d.title)}</h4><div class="ar">${esc(d.arabic).replace(/\n/g, "<br>")}</div>
        <div class="tr">${esc(d.translation || "")}</div><div class="ref">${esc(d.reference || "")}</div></div>
      <div class="counter"><button class="tapring" data-act="count" aria-label="Count">${n}<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="46" fill="none" stroke="rgba(255,255,255,.1)" stroke-width="3"/><circle cx="50" cy="50" r="46" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-dasharray="${circ}" stroke-dashoffset="${off}" style="color:var(--pa);transition:stroke-dashoffset .25s"/></svg></button>
        <span class="cap">× ${d.count}</span>
        ${S.pending && S.pending.enforced ? `<button class="chip breakpill" data-act="break">❙❙ Take a break</button><span style="font-size:10.5px;color:var(--text-3)">Must be done by ${fmt(S.pending.type === "morning" ? MORNING_ENDS : eveningCloses())}</span>` : ""}</div>`;
  }

  function doneScreen() {
    return `<div style="margin:auto;text-align:center;padding:24px;display:grid;gap:10px;justify-items:center">
      <div class="badge" style="width:64px;height:64px;font-size:28px;border-radius:50%">✓</div>
      <div class="ser" style="font-size:34px">Done</div><div class="sub" style="color:var(--text-2)">${DATA.adhkaar.length} adhkaar · 4 min · ${S.streak + 1}-day streak</div>
      <button class="pill-btn" data-act="home" style="width:200px">Back to today</button></div>`;
  }

  function lockScreen() {
    return `<div class="scr lock">${status()}<div class="big">${fmt(S.min).replace(/ .*/, "")}</div><div style="color:var(--text-2)">Saturday, 26 September</div>
      <button class="chip unlock" style="margin:auto 0 40px" data-act="unlock">Swipe up to unlock</button></div>`;
  }

  function chatScreen() {
    return `<div class="scr chat">${status()}<header><span>Chats</span><span style="color:var(--text-3)">A stand-in for any other app</span></header>
      <div class="msg">Did you see the match last night?</div><div class="msg me">Not yet, send the highlights</div><div class="msg">Here 👇 20 minutes of it</div>
      <div style="margin-top:auto;padding:14px"><button class="pill-btn soft" data-act="open-app">Open Adhkaar</button></div></div>`;
  }

  function overlays() {
    const o = S.overlay;
    if (!o) return "";
    if (o.kind === "banner") return `<div class="banner" data-act="banner"><div class="badge">🔔</div><div><b>${esc(o.title)}</b><span>${esc(o.text)}</span></div></div>`;
    if (o.kind === "salah") {
      return `<div class="scrim deep" data-act="dismiss"><div class="frost" data-stop>
        <div class="badge" style="width:48px;height:48px;font-size:22px">🕌</div>
        <h5>Time to get ready for ${pname(P(o.prayer))}</h5>
        <div style="color:var(--text-2);font-size:12.5px">Leave what you are doing for a few minutes. Make wudu, and head to the masjid if you can.</div>
        <div class="row"><span class="chip">💧 Wudu</span><span class="chip">🕌 Masjid</span><span class="chip">📵 Phone down</span></div>
        <div class="ar">الصَّلَاةُ عَلَى وَقْتِهَا</div>
        <div style="font-size:12.5px">The most beloved deed to Allah: salah at its proper time.</div>
        <div class="cap acc">Sahih al-Bukhari 527</div>
        <button class="pill-btn" data-act="dismiss">I'm getting ready</button></div></div>`;
    }
    if (o.kind === "after") {
      return `<div class="scrim deep" data-act="dismiss"><div class="frost" data-stop>
        <div class="badge" style="width:48px;height:48px;font-size:22px">🕌</div><h5>${t("moment_title_after_salah", "Adhkaar after salah")}</h5>
        <div style="color:var(--text-2);font-size:12.5px">A few words after the prayer: istighfar, Ayat al-Kursi, the three Quls (three times after Fajr and Maghrib) and tasbih.</div>
        <button class="pill-btn" data-act="dismiss">Open</button><button class="quiet" data-act="dismiss">Later</button></div></div>`;
    }
    if (o.kind === "break1") {
      const opts = [10, 20, 30, 60].filter((v) => S.min + v < (S.pending.type === "morning" ? MORNING_ENDS : eveningCloses()));
      const left = DATA.adhkaar.length - S.idx;
      return `<div class="scrim"><div class="dialog"><h5>Need a moment?</h5><div class="muted">${left} adhkaar left</div>
        <div style="margin-top:8px"><span class="chip">🕒 Must be done by ${fmt(S.pending.type === "morning" ? MORNING_ENDS : eveningCloses())}</span></div>
        ${opts.length ? `<div class="grid2">${opts.map((v) => `<button class="tile-t" aria-pressed="${S.chosen === v}" data-pick="${v}">${v === 60 ? 1 : v}<small>${v === 60 ? "hour" : "min"}</small></button>`).join("")}</div>` : `<div class="muted" style="margin-top:10px">Not enough time left for a break</div>`}
        <div class="muted" style="text-align:center;margin-top:10px">${S.chosen ? `Back at ${fmt(S.min + S.chosen)}. Then the alert rings again.` : "Your phone is free until the break ends. Then the alert rings again."}</div>
        <div style="margin-top:12px;display:grid;gap:4px"><button class="pill-btn" data-act="keep">Keep reading</button>
        ${opts.length ? `<button class="quiet" data-act="next" ${S.chosen ? "" : "disabled"}>${S.chosen ? "Pause for " + S.chosen + " minutes" : "Choose a time"}</button>` : ""}</div></div></div>`;
    }
    if (o.kind === "break2") {
      const left = DATA.adhkaar.length - S.idx;
      return `<div class="scrim"><div class="dialog"><h5>Before you pause</h5>
        <div style="font-size:14px">Just ${left} adhkaar left. A few minutes, and today's adhkaar are done.</div>
        <div class="muted" style="margin-top:6px">You have kept them for ${S.streak} days in a row. Don't let today be the gap.</div>
        <div class="hq"><div class="ar">مَثَلُ الَّذِي يَذْكُرُ رَبَّهُ وَالَّذِي لَا يَذْكُرُ رَبَّهُ مَثَلُ الْحَيِّ وَالْمَيِّتِ</div>
          <div style="font-size:12px">The one who remembers his Lord and the one who does not are like the living and the dead.</div><div class="src">Sahih al-Bukhari 6407</div></div>
        <div style="margin-top:12px;display:grid;gap:8px"><button class="pill-btn" data-act="keep">Stay and finish</button>
        <button class="hold" data-hold><i></i><span>Hold to pause for ${S.chosen} minutes</span></button></div></div></div>`;
    }
    if (o.kind === "block") {
      return `<div class="block"><div class="aura dawn"></div><div class="orb"></div><h5>Your ${S.pending.type} adhkaar is waiting</h5>
        <div style="color:var(--text-2)">Other apps are paused for now. A few calm minutes with Allah, then they're yours again.</div>
        <span class="chip">● ${DATA.adhkaar.length - S.idx} adhkaar left</span>
        <button class="pill-btn" style="margin-top:18px" data-act="return">Return to your adhkaar →</button>
        <button class="quiet" data-act="call">📞 Call or emergency</button></div>`;
    }
    if (o.kind === "edit") {
      const p = P(o.id);
      return `<div class="scrim" data-act="dismiss"><div class="dialog" data-stop><h5>${pname(p)}</h5><div class="muted">Your masjid's time replaces the default.</div>
        <div style="display:flex;align-items:center;justify-content:space-between;margin:14px 0"><button class="x" data-shift="-5" aria-label="5 minutes earlier">−</button><span class="ser" style="font-size:32px">${fmt(p.t)}</span><button class="x" data-shift="5" aria-label="5 minutes later">+</button></div>
        <button class="pill-btn" data-act="dismiss">Done</button></div></div>`;
    }
    return "";
  }

  function render() {
    phone.dataset.aura = aura();
    phone.dir = S.lang === "ar" ? "rtl" : "ltr";
    let body;
    if (S.locked && !(S.screen === "session" && S.mode !== "gentle")) body = lockScreen();
    else if (S.screen === "chat") body = chatScreen();
    else if (S.screen === "session") body = `<div class="scr"><div class="aura ${aura()}"></div>${status()}${sessionScreen()}</div>`;
    else if (S.screen === "done") body = `<div class="scr"><div class="aura ${aura()}"></div>${status()}${doneScreen()}</div>`;
    else {
      const content = { today: todayScreen, adhkaar: adhkaarScreen, insights: insightsScreen, settings: settingsScreen }[S.tab]();
      body = `<div class="scr"><div class="aura ${aura()}"></div>${status()}${content}${tabs()}</div>`;
    }
    screen.innerHTML = body + overlays();
    syncControls();
  }

  // ---- behaviour ----
  function startSession(type, enforced) {
    if (!S.pending || S.pending.type !== type) { S.pending = { type, enforced, paused: false }; S.idx = 0; S.counts = {}; }
    S.pending.enforced = S.pending.enforced || enforced;
    S.pending.paused = false;
  }

  function sessionTime(type) {
    const name = type === "morning" ? "Morning adhkaar" : "Evening adhkaar";
    if (S.done[type]) return;
    if (S.mode === "gentle") {
      S.pending = { type, enforced: false, paused: false }; S.idx = 0; S.counts = {};
      S.overlay = { kind: "banner", title: name, text: "It's time. One soft chime; it won't ring again." };
      say(`${fmt(S.min)}: Gentle plays one chime and shows a notification.`);
      return;
    }
    startSession(type, true);
    S.overlay = null;
    S.screen = "session";
    say(S.mode === "lockdown"
      ? `${fmt(S.min)}: Lockdown opens the adhkaar, even on the lock screen. Try "Open another app".`
      : `${fmt(S.min)}: Full screen opens the adhkaar, even on the lock screen. Close it and it returns at your next unlock.`);
  }

  function salahTime(id) {
    S.overlay = { kind: "salah", prayer: id };
    say(`${fmt(S.min)}: the ${P(id).name} reminder pops up${S.locked ? " over the lock screen" : " over whatever is open"}, with a chime and vibration.`);
    buzz([200, 100, 200]);
  }

  // Fires what the clock has just reached. Jumping far ahead only fires the latest event.
  function events() {
    const m = S.min;
    const list = [];
    PRAYERS.filter((p) => !p.notSalah).forEach((p) => list.push({ key: "salah-" + p.id, t: p.t, run: () => salahTime(p.id) }));
    list.push({ key: "morning", t: morningOpens(), run: () => sessionTime("morning") });
    list.push({ key: "evening", t: eveningOpens(), run: () => sessionTime("evening") });
    list.push({ key: "morning-end", t: MORNING_ENDS, run: () => windowClosed("morning") });
    list.push({ key: "evening-end", t: eveningCloses(), run: () => windowClosed("evening") });
    if (S.pending && S.pending.paused) list.push({ key: "break-end-" + S.pending.pausedUntil, t: S.pending.pausedUntil, run: breakEnded });
    list.forEach((e) => { if (e.t > m) S.fired.delete(e.key); });
    const due = list.filter((e) => e.t <= m && !S.fired.has(e.key)).sort((a, b) => a.t - b.t);
    due.forEach((e) => S.fired.add(e.key));
    const recent = due.filter((e) => m - e.t < 45);
    if (recent.length) recent[recent.length - 1].run();
  }

  function windowClosed(type) {
    if (S.pending && S.pending.type === type && !S.done[type]) {
      S.pending = null; if (S.screen === "session") S.screen = "app";
      S.overlay = null;
      say(`${fmt(S.min)}: the ${type} adhkaar time has ended, so the lock lifts. The day counts as missed.`);
    }
  }

  function breakEnded() {
    if (!S.pending) return;
    S.pending.paused = false;
    say(`${fmt(S.min)}: the break is over, so the alert rings again.`);
    sessionTime(S.pending.type);
  }

  function setMin(v) {
    const back = v < S.min;
    S.min = Math.max(0, Math.min(1439, v));
    if (back) {
      if (S.min < morningOpens()) { S.done.morning = false; }
      if (S.min < eveningOpens()) { S.done.evening = false; }
      if (S.pending && ((S.pending.type === "morning" && S.min < morningOpens()) || (S.pending.type === "evening" && S.min < eveningOpens()))) { S.pending = null; if (S.screen === "session") S.screen = "app"; }
    }
    events();
    render();
  }

  function count() {
    const d = DATA.adhkaar[S.idx];
    const n = (S.counts[d.id] || 0) + 1;
    S.counts[d.id] = Math.min(n, d.count);
    buzz(12);
    if (n >= d.count) {
      if (S.idx + 1 >= DATA.adhkaar.length) {
        const type = S.pending ? S.pending.type : "morning";
        S.done[type] = true; S.pending = null; S.screen = "done";
        say(`Done. The ${type} adhkaar are recorded, the streak grows, and any lock lifts.`);
        buzz([30, 60, 30]);
      } else {
        render();
        setTimeout(() => { S.idx++; render(); }, 380);
        return;
      }
    }
    render();
  }

  function startHold(btn) {
    const fill = btn.querySelector("i");
    const start = performance.now();
    let q = 0, raf;
    const step = (now) => {
      const p = Math.min(1, (now - start) / 5000);
      fill.style.width = p * 100 + "%";
      const nq = Math.floor(p * 4);
      if (nq > q && nq < 4) { q = nq; buzz(10); }
      if (p >= 1) {
        buzz(40);
        S.pending.paused = true; S.pending.pausedUntil = S.min + S.chosen;
        S.overlay = null; S.screen = "chat";
        say(`On a break until ${fmt(S.pending.pausedUntil)}. Your phone is free; move the clock past it and the alert returns.`);
        render();
        return;
      }
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    const stop = () => { cancelAnimationFrame(raf); if (S.overlay && S.overlay.kind === "break2") fill.style.width = "0"; };
    btn.addEventListener("pointerup", stop, { once: true });
    btn.addEventListener("pointerleave", stop, { once: true });
    btn.addEventListener("pointercancel", stop, { once: true });
  }

  screen.addEventListener("pointerdown", (e) => {
    const hold = e.target.closest("[data-hold]");
    if (hold) { e.preventDefault(); hold.setPointerCapture && hold.setPointerCapture(e.pointerId); startHold(hold); }
  });
  screen.addEventListener("keydown", (e) => {
    const hold = e.target.closest("[data-hold]");
    if (hold && (e.key === " " || e.key === "Enter") && !e.repeat) { e.preventDefault(); startHold(hold); hold.addEventListener("keyup", () => hold.dispatchEvent(new Event("pointerup")), { once: true }); }
  });

  screen.addEventListener("click", (e) => {
    const el = e.target.closest("[data-act],[data-tab],[data-mode],[data-edit],[data-shift],[data-pick],[data-kit]");
    if (!el) return;
    if (el.dataset.act === "dismiss" && e.target.closest("[data-stop]") && !e.target.closest("button")) return;
    if (el.dataset.tab) { S.tab = el.dataset.tab; return render(); }
    if (el.dataset.mode) { setMode(el.dataset.mode); return; }
    if (el.dataset.edit) { S.overlay = { kind: "edit", id: el.dataset.edit }; return render(); }
    if (el.dataset.shift) { const p = P(S.overlay.id); p.t = Math.max(0, Math.min(1435, p.t + Number(el.dataset.shift))); buildJumps(); return render(); }
    if (el.dataset.pick) { S.chosen = Number(el.dataset.pick); return render(); }
    if (el.dataset.kit) {
      if (el.dataset.kit === "morning") { S.fired.add("morning"); sessionTime("morning"); }
      if (el.dataset.kit === "salah") salahTime("maghrib");
      if (el.dataset.kit === "after") { S.overlay = { kind: "after" }; }
      return render();
    }
    switch (el.dataset.act) {
      case "begin": startSession(heroState().type, false); S.screen = "session"; break;
      case "count": return count();
      case "close": S.screen = "app"; say(S.pending && S.pending.enforced ? "Closed. In Full screen the adhkaar come back at your next unlock." : "Closed. Your place is kept."); break;
      case "home": S.screen = "app"; S.tab = "today"; break;
      case "break": S.chosen = null; S.overlay = { kind: "break1" }; break;
      case "keep": S.overlay = null; S.chosen = null; say("Good. Keep going."); break;
      case "next": if (S.chosen) S.overlay = { kind: "break2" }; break;
      case "dismiss": S.overlay = null; break;
      case "banner": S.overlay = null; S.locked = false; startSession(S.pending ? S.pending.type : "morning", false); S.screen = "session"; break;
      case "unlock": S.locked = false; if (S.pending && S.pending.enforced && !S.pending.paused && S.mode !== "gentle") { S.screen = "session"; say("Unlocked: the adhkaar come up first."); } break;
      case "open-app": S.screen = "app"; break;
      case "return": S.overlay = null; S.screen = "session"; break;
      case "call": S.overlay = null; say("The phone app opens and stays open. Calls and emergencies always work."); break;
    }
    render();
  });

  // ---- controls beside the phone ----
  const range = document.getElementById("sim-clock");
  const out = document.getElementById("sim-time");
  const where = document.getElementById("sim-where");
  const jumps = document.getElementById("sim-jumps");

  function buildJumps() {
    const marks = [
      [P("fajr").t, "Fajr"], [morningOpens(), "Morning opens"], [MORNING_ENDS, "Morning ends"], [P("dhuhr").t, "Dhuhr"],
      [P("asr").t, "Asr"], [eveningOpens(), "Evening opens"], [P("maghrib").t, "Maghrib"], [P("isha").t, "Isha"],
    ];
    jumps.innerHTML = marks.map(([m, n]) => `<button type="button" data-to="${m}">${fmt(m)} · ${n}</button>`).join("");
  }
  jumps.addEventListener("click", (e) => { const b = e.target.closest("[data-to]"); if (b) setMin(Number(b.dataset.to)); });
  range.addEventListener("input", () => setMin(Number(range.value)));

  function describe() {
    const m = S.min;
    if (m >= morningOpens() && m < MORNING_ENDS) return "Morning adhkaar time";
    if (m >= eveningOpens() && m < eveningBest()) return "Evening adhkaar, best before Maghrib";
    if (m >= eveningBest() && m < eveningCloses()) return "Evening adhkaar, still open until Isha";
    const act = PRAYERS.filter((p) => !p.notSalah).find((p) => m >= p.t && m < p.t + 30);
    if (act) return `${act.name}: getting ready and praying`;
    return "Between times";
  }

  function setMode(m) { S.mode = m; say(`Mode: ${{ gentle: "Gentle", full: "Full screen", lockdown: "Lockdown" }[m]}. Move the clock to an opening time to see it.`); render(); }
  document.querySelectorAll("[data-sim-mode]").forEach((b) => b.addEventListener("click", () => setMode(b.dataset.simMode)));
  const lockBtn = document.getElementById("sim-lock");
  lockBtn.addEventListener("click", () => { S.locked = !S.locked; if (!S.locked && S.pending && S.pending.enforced && !S.pending.paused && S.mode !== "gentle") S.screen = "session"; render(); });
  document.getElementById("sim-other").addEventListener("click", () => {
    S.locked = false; S.screen = "chat";
    if (S.mode === "lockdown" && S.pending && S.pending.enforced && !S.pending.paused) { S.overlay = { kind: "block" }; say("Lockdown covers any other app until the adhkaar are done or their time ends."); }
    else if (S.mode === "full" && S.pending && S.pending.enforced && !S.pending.paused) say("Full screen doesn't block apps; the adhkaar return at your next unlock.");
    render();
  });
  document.querySelectorAll("[data-sim-lang]").forEach((b) => b.addEventListener("click", () => { S.lang = b.dataset.simLang; buildJumps(); render(); }));

  function syncControls() {
    range.value = S.min;
    out.textContent = fmt(S.min);
    where.textContent = describe();
    document.querySelectorAll("[data-sim-mode]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.simMode === S.mode)));
    document.querySelectorAll("[data-sim-lang]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.simLang === S.lang)));
    lockBtn.textContent = S.locked ? "Unlock phone" : "Lock phone";
  }

  buildJumps();
  // Opens on the Today screen: what the clock has already passed is taken as seen.
  events();
  S.overlay = null;
  render();
  say("Drag the clock, or tap a time. The phone does what the app would do on your phone.");
})();
