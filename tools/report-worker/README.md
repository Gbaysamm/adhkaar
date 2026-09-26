# Report worker

A tiny web service that receives messages from the app's **Settings > Contact us** screen and files each one as a GitHub issue, labelled `user-report` and its topic (`problem`, `suggestion`, `correction` or `other`). Users never see GitHub; they just type and press Send.

It runs on Cloudflare Workers. The free plan (100,000 requests a day) is far more than enough.

## What it accepts

`POST` with a JSON body:

```json
{"topic": "problem", "message": "…", "contact": "", "device": "Tecno KI5k", "android": "13 (API 33)",
 "appVersion": "0.1.0 (1)", "mode": "Lockdown", "language": "ha-NG"}
```

- `message` must be 10 to 5,000 characters; the whole body at most 20 KB.
- At most 5 messages an hour from one IP address (a simple in-memory limit, reset when Cloudflare restarts the worker).
- Replies `201` when the issue was created; anything else makes the app show "Couldn't send" and keep the user's draft.

## Contact details stay private

The repository is public, so **anything in an issue is public**. The worker never puts the user's email or phone number in the issue. Instead:

- If the `CONTACT_WEBHOOK` secret is set, the worker POSTs the contact details and the issue number there, e.g. a private Discord or Slack channel's incoming webhook, or your own endpoint. The JSON has `content` (Discord), `text` (Slack), and `contact`, `issue`, `url`, `topic`.
- If it isn't set, **contact details are dropped**. The issue says whether the sender left any.

## Deploy (about 10 minutes, free)

1. **Make a GitHub token.** GitHub > Settings > Developer settings > Fine-grained tokens > Generate new token. Repository access: only the Adhkaar repository. Permissions: **Issues: Read and write**. Nothing else. Copy the token.
2. **Create the labels once** in the repository (Issues > Labels): `user-report`, `problem`, `suggestion`, `correction`, `other`. (GitHub usually creates missing labels itself, but creating them lets you pick colours.)
3. **Make a Cloudflare account** at <https://dash.cloudflare.com/sign-up> (free).
4. Install Node.js (18 or newer), then in this folder:

   ```sh
   npx wrangler login
   npx wrangler secret put GITHUB_TOKEN        # paste the token from step 1
   npx wrangler secret put CONTACT_WEBHOOK     # optional: a private webhook URL
   npx wrangler deploy
   ```

   If the repository isn't `adhkaar-app/adhkaar`, change `GITHUB_REPO` in `wrangler.toml` first.
5. `wrangler deploy` prints the worker's address, like `https://adhkaar-report.<your-subdomain>.workers.dev`. Check it:

   ```sh
   curl -X POST https://adhkaar-report.<your-subdomain>.workers.dev \
     -H "content-type: application/json" \
     -d '{"topic":"other","message":"Test from curl, please close"}'
   ```

   A new issue should appear in the repository.
6. **Point the app at it.** In the project's `gradle.properties`:

   ```properties
   adhkaar.reportUrl=https://adhkaar-report.<your-subdomain>.workers.dev
   ```

   and rebuild. With `adhkaar.reportUrl` blank, the app's Send button opens the user's email app instead (if `adhkaar.contactEmail` is set), or shows "Couldn't send".

Logs: `npx wrangler tail` shows each request live, including GitHub's reply when something fails.

To rotate the token, make a new one and run `npx wrangler secret put GITHUB_TOKEN` again; no app update needed.
