---
name: verify
description: Verify a change actually works by driving the running Seam app end-to-end — start it, sign in, exercise the affected flow in a real browser, and observe behaviour. Use before committing any nontrivial change with a runtime surface; tests and compile alone are not verification.
---

# Verify a Change in the Running App

Exercise the affected flow in the real app and observe the result. A change
is verified when you have *seen* the new behaviour, not when tests pass.

## 1. Make sure the app can run

- **Worktree** (the normal case): `webapp/local.env` was written by
  `worktree-db.sh` and points at the worktree's own Neon branch — no local
  Docker needed. If `local.env` is missing, run `bash webapp/scripts/worktree-db.sh`.
- **Main checkout**: `./webapp/scripts/start-db.sh` +
  `./webapp/scripts/migrate-locally.sh` first if using the local container DB.

## 2. Start it (skip if already up)

```bash
ss -tlnp | grep :8080 || (./webapp/scripts/run.sh > /tmp/seam-run.log 2>&1 &)
sleep 15 && curl -s -o /dev/null -w '%{http_code}' localhost:8080
```

The default `--env local` **skips Microsoft sign-in** — the sign-in page
offers a demo sign-in (`/auth/oidc/demo-redirect`), so a browser session can
authenticate without real OAuth.

## 3. Drive the affected flow

Use the `/playwright` skill:

1. Open `localhost:8080`, sign in via the demo sign-in.
2. Navigate to the page the change affects (URL scheme: `/worlds/:worldId/...`).
3. Exercise the actual interaction — submit the form, toggle the toggle,
   trigger the HTMX swap. Verify the DOM updated (fragment landed in the
   right target) rather than only that the request returned 200.
4. For UI changes, screenshot at 375 and 1440 widths.

For backend-only changes with no page to click: hit the endpoint with
`curl -s localhost:8080/...` (responses are HTML fragments — check the
markup, and remember auth is cookie-based, so unauthenticated curl gets a
redirect; verifying through the browser flow is usually easier).

## 4. Check the evidence

- App log (`/tmp/seam-run.log` or the run.sh terminal): no new stack traces
  or 5xx during the flow
- The behaviour you shipped is observably different in the way the task
  intended — before/after if practical

## 5. Report honestly

State what you drove, what you observed, and anything that did not behave
as expected. "Started app, created a project via the form, saw it appear in
the list with the new badge, no log errors" is verification; "tests pass"
is not. Kill the app afterwards only if you started it
(`pkill -f mcorg` — don't kill a server the user was already running).
