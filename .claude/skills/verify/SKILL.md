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

A worktree runs on its **own port**, not 8080 (MCO-476) — read it from the
`PORT` line in this worktree's `webapp/local.env`; the main checkout has no
such line and uses 8080. `run.sh` prints the URL on startup.

```bash
PORT=$(grep -E '^PORT=' webapp/local.env | cut -d= -f2 || true); PORT=${PORT:-8080}
ss -tlnp | grep ":$PORT" || (./webapp/scripts/run.sh > /tmp/seam-run.log 2>&1 &)
sleep 15 && curl -s -o /dev/null -w '%{http_code}' localhost:$PORT
```

The default `--env local` **skips Microsoft sign-in** — the sign-in page
offers a demo sign-in (`/auth/oidc/demo-redirect`), so a browser session can
authenticate without real OAuth.

## 3. Drive the affected flow

Use the `/playwright` skill. This worktree drives **its own browser** (MCO-515) —
the session name comes from the worktree, so the page you see is yours and not a
sibling worktree's. Skip the skill's `config --browser=chromium` setup step: the
browser is already set, and re-running `config` restarts the daemon and loses
your page.

1. Open `localhost:$PORT`, sign in via the demo sign-in.
2. Navigate to the page the change affects (URL scheme: `/worlds/:worldId/...`).
3. Exercise the actual interaction — submit the form, toggle the toggle,
   trigger the HTMX swap. Verify the DOM updated (fragment landed in the
   right target) rather than only that the request returned 200.
4. For UI changes, screenshot at 375 and 1440 widths.

For backend-only changes with no page to click: hit the endpoint with
`curl -s localhost:$PORT/...` (responses are HTML fragments — check the
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
is not.

Then clean up what you started:

```bash
playwright-cli close   # this worktree's browser — frees ~960 MB immediately
pkill -f mcorg         # ONLY if you started the app; not one the user was running
```

`close` stops the browser but keeps its profile, so signing in again is not
needed — the next `open` restarts from the same cookie jar in ~2s. Skipping it
is not fatal (a `Stop` hook reaps browsers idle over 30 minutes), but that is
up to 30 minutes of ~960 MB you did not need to hold.
