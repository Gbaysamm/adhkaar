// Receives messages from the app's "Contact us" screen and files each one as a GitHub issue.
// See README.md for setup. Secrets: GITHUB_TOKEN (required), CONTACT_WEBHOOK (optional).
// Vars (wrangler.toml): GITHUB_REPO = "owner/name".

const TOPICS = ["problem", "suggestion", "correction", "other"];
const MAX_MESSAGE = 5000;
const MAX_BODY_BYTES = 20_000;
const MAX_FIELD = 200;

// Naive per-IP limit: at most LIMIT messages per WINDOW_MS from one address. It lives in the
// worker's memory, so it resets when Cloudflare recycles the instance and isn't shared between
// data centres. Enough to stop a stuck retry loop or a casual flood, not a determined attacker.
const LIMIT = 5;
const WINDOW_MS = 60 * 60 * 1000;
const recent = new Map();

function limited(ip) {
  const now = Date.now();
  const times = (recent.get(ip) || []).filter((t) => now - t < WINDOW_MS);
  if (times.length >= LIMIT) {
    recent.set(ip, times);
    return true;
  }
  times.push(now);
  recent.set(ip, times);
  if (recent.size > 10_000) recent.clear();
  return false;
}

const json = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const field = (value, max = MAX_FIELD) => (typeof value === "string" ? value.trim().slice(0, max) : "");

// Shown inside a code fence, so nothing in a message can @mention people, embed images or links.
const fenced = (text) => "```text\n" + text.replace(/```/g, "'''") + "\n```";

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "POST") return json(405, { error: "POST only" });
    if (!env.GITHUB_TOKEN || !env.GITHUB_REPO) return json(500, { error: "Not configured" });

    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    if (limited(ip)) return json(429, { error: "Too many messages. Please try again later." });

    const length = Number(request.headers.get("content-length") || 0);
    if (length > MAX_BODY_BYTES) return json(413, { error: "Too large" });
    const raw = await request.text();
    if (raw.length > MAX_BODY_BYTES) return json(413, { error: "Too large" });

    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      return json(400, { error: "Not JSON" });
    }
    if (typeof data !== "object" || data === null) return json(400, { error: "Not an object" });

    const message = typeof data.message === "string" ? data.message.trim() : "";
    if (message.length < 10) return json(400, { error: "Message too short" });
    if (message.length > MAX_MESSAGE) return json(413, { error: "Message too long" });
    const topic = TOPICS.includes(data.topic) ? data.topic : "other";
    const contact = field(data.contact);
    const details = {
      Phone: field(data.device),
      Android: field(data.android),
      App: field(data.appVersion),
      Mode: field(data.mode),
      Language: field(data.language),
    };

    const firstLine = message.split("\n")[0].replace(/\s+/g, " ").slice(0, 70);
    const title = `[${topic}] ${firstLine}${firstLine.length < message.length ? "…" : ""}`;
    const body = [
      `Sent from the app's **Contact us** screen (${topic}).`,
      "",
      fenced(message),
      "",
      "| | |",
      "|---|---|",
      ...Object.entries(details).map(([k, v]) => `| ${k} | ${v.replace(/\|/g, "/") || "-"} |`),
      "",
      contact
        ? "_The sender left contact details; they were sent privately, not posted here._"
        : "_No contact details were left._",
    ].join("\n");

    const response = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/issues`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${env.GITHUB_TOKEN}`,
        accept: "application/vnd.github+json",
        "x-github-api-version": "2022-11-28",
        "user-agent": "adhkaar-report-worker",
        "content-type": "application/json",
      },
      body: JSON.stringify({ title, body, labels: ["user-report", topic] }),
    });
    if (!response.ok) {
      console.error("GitHub said", response.status, await response.text());
      return json(502, { error: "Could not file the report" });
    }
    const issue = await response.json();

    // Contact details never go in the (public) issue. With a webhook set, they go there;
    // without one they are dropped.
    if (contact && env.CONTACT_WEBHOOK) {
      const text = `Contact for #${issue.number} (${topic}): ${contact}\n${issue.html_url}`;
      ctx.waitUntil(
        fetch(env.CONTACT_WEBHOOK, {
          method: "POST",
          headers: { "content-type": "application/json" },
          // "content" suits Discord, "text" suits Slack and most others; the rest is for your own endpoint.
          body: JSON.stringify({ content: text, text, contact, issue: issue.number, url: issue.html_url, topic }),
        }).catch((e) => console.error("Contact webhook failed", e)),
      );
    }

    return json(201, { ok: true, issue: issue.number });
  },
};
