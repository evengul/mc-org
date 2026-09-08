---
name: worktree-infrastructure
description: >-
  How a Seam/mc-org git worktree is isolated from the main checkout and from sibling
  worktrees, and how to fix it when it breaks. Load when working in or provisioning a
  worktree; when a worktree has no database, no demo user, "You don't have permission"
  on every world, or a port/bind collision; when Flyway must run against a worktree's
  own Neon branch; when a `-pl` build fails to resolve app.mcorg jars; when the box is
  thrashing or out of memory (idle Kotlin compile daemons and Playwright/Chromium
  sessions are the usual cause); or when playwright-cli appears to be driving the wrong
  worktree's app. Covers worktree-db.sh, worktree-port.sh, worktree-m2.sh,
  worktree-playwright.sh, migrate-worktree.sh, and the cleanup/prune scripts.
user-invocable: false
---

# Worktree Infrastructure

A worktree is isolated on five axes — database, HTTP port, Maven repository, Kotlin
compile daemon, and browser session. Each was added because two worktrees collided on
that resource; the sections below carry the failure that motivated it, which is the part
you cannot recover from reading the scripts.

The **rule** that a code change starts in a worktree lives in `CLAUDE.md` (Critical
Rules) and is always in context. This skill is the *mechanics*.

## Worktree Database Isolation

**Start new work in a git worktree, not the main checkout — check this BEFORE the
first edit, not at commit time.** The main checkout (`master`) shares the
production-backed dev database; worktrees each get their own isolated copy (below).
Working in a worktree keeps `master` clean, lets features run in parallel without
database or migration collisions, and matches how CI builds each PR. Use an agent's
`isolation: "worktree"` or `git worktree add` to start. If you only realise mid-task
that you are on `master`, immediately branch off (`git checkout -b <linear-branch>`)
so the work leaves `master` clean — a branch is the fallback floor; a worktree is the
default. Never edit or commit on `master` directly.

Each git worktree gets its **own** database so migrations and data never collide
with the main checkout or sibling worktrees. This mirrors what CI already does
per pull request (`.github/workflows/dev.yml` forks `dev/pr-<N>` Neon branches for
`preview`-labelled PRs — see [CI / PR Previews](#ci--pr-previews)) — only locally, per
worktree, using the same Neon project (`sweet-dust-00910797`).

**Workflow:**

1. Start a worktree (an agent's `isolation: "worktree"`, or `git worktree add`).
2. On `EnterWorktree`, a PostToolUse hook runs `webapp/scripts/worktree-db.sh`. It:
   - Forks a copy-on-write Neon branch `wt/<git-branch>` from production (`master`)
   - Writes the worktree's `webapp/local.env` (main checkout's non-DB config + the
     branch's `DB_*` credentials)
   - Runs `mvn -DskipTests install` over the whole reactor, so the worktree's own
     Maven repository actually holds the `app.mcorg` jars (without this, the very
     next `-pl` command fails to resolve them — see MCO-510 below)
   - Runs `flyway:migrate` against the isolated branch
   - Seeds the local sign-in user (`DEMO_USER`) as an owner of every world, then
     **verifies the rows landed** and reports failures at the end rather than
     aborting partway
3. The worktree's app and tests now target the worktree's own branch — never the
   shared dev DB — and any migrations you run land on the isolated branch.
4. On `ExitWorktree`, `webapp/scripts/worktree-db-cleanup.sh --prune` reconciles
   live `wt/*` Neon branches against `git worktree list` and deletes orphans.
5. **Manual worktrees** (e.g. `claude -w`, where the EnterWorktree hook may not fire):
   run `bash webapp/scripts/worktree-db.sh` yourself from the worktree root.

**Why fork production?** Same as the CI previews — a copy-on-write branch inherits
the real ingested Minecraft data instantly (no re-ingestion) and matches CI exactly.

**Caveats:**

- **Provisioning builds the project, and that is load-bearing (MCO-510).** The
  worktree's Maven repository starts with an *empty* `app/mcorg`, so until something
  installs into it every `-pl <module>` command fails to resolve its siblings. The
  `flyway:migrate -pl mc-web` step used to hit that, and because the script is
  `set -euo pipefail` it exited there — **before** the demo-user seeding. Everything
  else looked fine (`local.env` written, `PORT` allocated, hook output discarded),
  so two worktrees ran for days with no demo user and "You don't have permission" on
  every world. The install step is what stops that; the seeding no longer depends on
  the build or migration succeeding, and the script verifies the rows before
  claiming success. Provisioning now takes ~86s on a fresh worktree with a cold
  Kotlin daemon, which is why the hook's timeout is 600s and not 180s.
- `webapp/local.env` is **gitignored**. The main checkout's copy is the single
  source for non-DB local config (Microsoft, Modrinth, `ENV`, …); the setup script
  writes each worktree's `local.env` fresh from it, swapping in the branch's Neon
  `DB_*` values. A fresh clone has no `local.env` — copy `webapp/local.env.example`
  (the committed template) to create it; `run.sh` seeds it for you on first run, and
  `worktree-db.sh` falls back to the template so worktrees provision even before the
  main checkout's `local.env` exists.
- **The local sign-in user is seeded, so you are not locked out of your own fork.**
  The Neon branch inherits production's worlds and their `world_members`, but not
  your local user — `DemoSignInPipeline` mints one on first sign-in, and a brand-new
  user belongs to no world, so every world reads "You don't have permission". The
  role check is cached per process too, so granting access afterwards needs a server
  restart. `worktree-db.sh` avoids both by seeding the row up front: the demo profile
  is deterministic (`"${DEMO_USER}-uuid"`, looked up by `minecraft_profiles.uuid`), so
  the first sign-in finds this user rather than creating another. Idempotent — re-run
  the script to pick up a world added later.
- **Migration number collisions are orthogonal to DB isolation.** If two branches
  each add `V{n}__*.sql` with the same `{n}`, Flyway errors on merge (out-of-order
  / checksum). Fix: renumber the later-merged migration to the next free number and
  re-run `migrate-locally.sh`. No DB-branching scheme prevents this — it's a git
  conflict, not a data one.

**Scripts:**

- `webapp/scripts/worktree-db.sh` — fork Neon branch + point `local.env` at it + migrate
- `webapp/scripts/worktree-db-cleanup.sh` — delete the current worktree's branch
- `webapp/scripts/worktree-db-cleanup.sh --prune` — delete all orphaned `wt/*` branches
- `webapp/scripts/migrate-worktree.sh` — apply Flyway migrations to the DB `local.env`
  points at (the worktree's Neon branch), reading its `DB_*` creds. Use this after
  adding a migration in a worktree — `migrate-locally.sh` hardcodes the localhost
  Docker DB and will not touch the branch.
- `webapp/scripts/worktree-m2.sh` — give this worktree its own Maven repository
  (see [Worktree Maven Repository Isolation](#worktree-maven-repository-isolation))
- `webapp/scripts/worktree-m2.sh --all` — retrofit every existing worktree

## Worktree Port Isolation

**A worktree's dev server binds its own HTTP port, not 8080.** Two worktrees could not both run
`run.sh` before MCO-476 — the second died on bind, which is the last thing they shared after the
database and the Maven repository.

The port comes from `PORT`, read by `readConfig()` like every other variable and defaulting to
8080. The main checkout, Docker, Fly and CI set nothing and stay on 8080.

- **Allocated once, then stable** by `webapp/scripts/worktree-port.sh`, which writes `PORT=<n>`
  into the worktree's `local.env` and picks from 8081–8179. Re-running returns the same port; a
  port that moved between runs would break bookmarks. `worktree-db.sh` carries it across its
  `local.env` rewrites.
- **Run automatically** by `worktree-db.sh` on `EnterWorktree`, and by `run.sh` if `PORT` is still
  unset (covers `claude -w` and hand-made worktrees). `run.sh` prints the URL on startup.
- **The claim set is not just what is listening** — a stopped worktree still owns its port, so
  allocation unions `ss -tln` with the `PORT=` lines of every worktree's `local.env`.
- **The JVM debug port follows it** (`5005 + PORT - 8080`), since `run.sh --debug` collided the
  same way. `--debug-port` still overrides.
- **Real Microsoft sign-in stays on 8080.** Only `http://localhost:8080/...` is registered as a
  `redirect_uri` in the Azure app registration, so `run.sh --env microsoft` needs the main
  checkout. Demo sign-in — the local default — works on any port.

## Worktree Maven Repository Isolation

**A worktree installs the `app.mcorg` modules into its own Maven repository, not
into `~/.m2`.** Concurrent worktrees otherwise overwrite each other: the project
version is a fixed `0.0.1` (not a per-branch SNAPSHOT), so every worktree's `mvn
install` writes the same `~/.m2/repository/app/mcorg/mc-domain/0.0.1/...` path.
Worktree B's build replaces the jars worktree A is about to run against — the
MCO-285 symptom, except `-am` does not save you, because the jar was correct
when it was written and wrong a second later.

The isolated repository lives at `webapp/.m2/repository` and is a **symlink farm
over `~/.m2/repository`**: every third-party groupId is a symlink back to the
shared cache (release artifacts are immutable — sharing them is the point, and
nothing is re-downloaded or duplicated on disk; a fresh worktree costs ~16 KB).
Only `app/mcorg` is a real directory, private to the worktree.

Maven is pointed at it by `webapp/.mvn/maven.config` (gitignored, written per
worktree), so a bare `mvn` typed by hand gets the isolation too — no script
needs a flag, and `cost-diagnostics` and friends are covered automatically.

- **Set up automatically** by the `EnterWorktree` hook, and by `run.sh` /
  `test.sh` if `webapp/.mvn/maven.config` is missing (covers `claude -w` and
  hand-made worktrees). Idempotent — re-run it any time.
- **The main checkout deliberately keeps the shared `~/.m2`**, as does CI (which
  caches `~/.m2/repository` and restores the module jars into it). The script
  refuses to touch the main checkout.
- **No teardown** — the repository dies with the worktree, and deleting a
  symlink never touches what it points at.
- **Maven 3.8 caveat:** `maven.config` is fed straight to the CLI parser, so a
  `#` comment line makes every `mvn` exit 1. The file holds nothing but the two
  flags. (Maven 3.9's `maven.repo.local.tail` would do the repo half natively; we
  are on 3.8.7.)

### Kotlin daemon fan-out (MCO-477)

**The isolation above costs you one Kotlin compile daemon per worktree, and each
one is capped for that reason.** The daemon is reused by *compiler classpath
identity*. While every worktree resolved `kotlin-compiler-embeddable` from the
shared `~/.m2`, the classpath was identical and all builds shared one daemon.
Each worktree now resolves it from its own `webapp/.m2/repository/...`, so the
paths differ, and so does the daemon. Daemons scale with worktrees.

That would be harmless if they were small and short-lived. Uncapped they are
neither: a daemon inherits the launching JVM's `-Xmx` — Maven's default quarter
of RAM, `-Xmx3990m` on a 16 GB box — and survives 7200s of idle. Four worktrees
built and left alone is ~12 GB resident doing nothing, which is enough to OOM a
16 GB WSL2 box. Measured before the cap: 3930 + 3107 + 2867 MB across three idle
worktree daemons.

*(Those figures are from a 16 GB box and stay as measured. **The box is 23.5 GB
now** — size a new cap against that, not against the 16 GB the paragraph above
argues from. With the cap in force a daemon measures ~2.1 GB RSS, above the
1500m heap because RSS also carries metaspace, code cache and thread stacks.)*

Two settings hold it down, and they live apart because Maven reads them
differently:

- **Heap** — `<kotlin.compiler.daemon.jvmArgs>` in `webapp/pom.xml`, currently
  `Xmx1500m`. **No leading dash**: the plugin prepends one, so `-Xmx1500m` here
  becomes `--Xmx1500m` and the daemon dies at startup with "Unrecognized option".
  Beware writing that doubled form into the surrounding XML comment to warn the
  next person — XML forbids `--` inside a comment, and Maven then rejects the file
  outright as a Non-parseable POM before any module compiles.
- **Idle life** — `-Dkotlin.daemon.options=autoshutdownIdleSeconds=900`, written
  into each worktree's `maven.config` by `worktree-m2.sh`. This one is **only**
  read as a system property; a pom `<properties>` entry is silently ignored (it
  looks like it worked — the build passes and the daemon keeps the old value). It
  is also the right scope: the main checkout has no `maven.config`, one daemon,
  and no reason to reap it early.

**Daemon reuse hides changes to both.** An already-running daemon is accepted as
compatible, so editing either setting and rebuilding tells you nothing. Kill the
worktree's daemon first, then rebuild and read the new process's arguments:

```bash
pgrep -af KotlinCompileDaemon        # one per worktree; the classpath names it
kill <pid>                           # only if no build is running in that worktree
```

**If the box is thrashing**, that list is the first thing to look at — idle
daemons from worktrees you finished with hours ago are the usual answer, not the
app or the editor.

## Worktree Browser Isolation (MCO-515)

**A worktree drives its own browser, not the one every other worktree is using.**
Before this, `/verify` in one worktree could navigate, click through and screenshot
*another* worktree's app — and look entirely plausible doing it, which is the
worst property a verification tool can have.

`playwright-cli` derives **both** the daemon socket and the browser profile from a
single session name:

```
/tmp/playwright-cli/<installHash>/<session>.sock
~/.cache/ms-playwright/daemon/<installHash>/ud-<session>-<browser>
```

resolved as `--session=X` → `$PLAYWRIGHT_CLI_SESSION` → `"default"`. Nothing set
either, so every worktree fell through to `default` and shared one Chromium —
one cookie jar, one set of tabs. (The daemon is also spawned with
`cwd: process.cwd() // Will be used as root`, so a shared one is *rooted* in
whichever worktree opened it first.)

- **A PATH shim sets the session name per call.** `~/.local/bin/playwright-cli`
  is a symlink to `webapp/scripts/playwright-cli-shim.sh`; `~/.local/bin` is
  already first on `PATH` via `.bashrc`, so no profile edit. Names are
  `<repo>` for a main checkout and `<repo>__<worktree>` for a worktree —
  `mc_org`, `mc_org__mco_515_playwright_session_isolation`. Outside a git repo
  nothing is set and playwright-cli's own `default` applies.
- **Session names carry no hyphen, and that is load-bearing (MCO-517).** Two
  playwright-cli bugs make one dangerous. `SessionManager.create` recovers names
  from profile directories with `file.split("-")[1]`, so a hyphenated
  `ud-mc-org--foo-chromium` invents a phantom session called `mc` in every
  `session-list`. `Session.delete` then matches
  `file.startsWith("ud-" + name + "-")` — so deleting that phantom, which looks
  exactly like cruft worth tidying, matches `ud-mc-org--*` and wipes **every**
  mc-org worktree's browser profile. Underscores make both impossible: the split
  returns the whole real name, and no name can be a hyphen-delimited prefix of
  another. Don't "tidy" the naming back.
- **The symlink targets the MAIN CHECKOUT's copy**, never a worktree's — a
  worktree gets deleted and a dangling shim would break `playwright-cli`
  everywhere on the box, in every repo.
- **It is on PATH for every repo**, not just this one, which is why names carry
  the repo prefix: worktree basenames collide across repos.
- **`--session=X` is NOT a substitute, and documenting it was not the fix.** It
  was already documented in the user-level playwright skill and the worktrees
  collided anyway, because the flag only works for the *first* command against a
  session: `session` is one of program.js's `globalArgs`, and `SessionManager.run`
  exits 1 with "The session is already configured" if any global arg reaches a
  session that already exists. `PLAYWRIGHT_CLI_SESSION` does not populate
  `args.session`, so the env var is the only mechanism that survives repeated use.
- **The shim also sets `PLAYWRIGHT_MCP_BROWSER=chromium`.** playwright-cli's
  default is `browserName: "chromium"` with `launchOptions.channel: "chrome"`, and
  Google Chrome is not installed here — that is what the playwright skill's "run
  `config --browser=chromium` once per session" step worked around, and with a
  session per worktree it would have become once per *worktree*. Don't run that
  step; on an existing session it restarts the daemon and loses your page.
- **You get one Chromium per worktree** — the [Kotlin daemon fan-out](#kotlin-daemon-fan-out-mco-477)
  shape again, and see [Reaping idle browsers](#reaping-idle-browsers-mco-517)
  for what holds it down.

Overrides, both respected: `PLAYWRIGHT_CLI_SESSION=foo playwright-cli ...` pins a
name by hand, and an explicit `--session=` flag still wins.

**Scripts:**

- `webapp/scripts/playwright-cli-shim.sh` — the shim itself (not run directly)
- `webapp/scripts/worktree-playwright.sh` — install/refresh the symlink; idempotent
- `webapp/scripts/worktree-playwright.sh --prune` — stop + delete sessions whose
  worktree is gone (only ever inside this repo's `mc_org*` namespace)
- `webapp/scripts/worktree-playwright.sh --status` — the shim, this directory's
  session name, and every live session

Installed automatically by the `EnterWorktree` hook. On a fresh machine, or the
first time after this merged, run it once by hand from the main checkout:

```bash
bash webapp/scripts/worktree-playwright.sh
```

It warns if `~/.local/bin` is not ahead of the real `playwright-cli` on `PATH` —
worth heeding, because the failure is silent: every worktree quietly shares one
browser again.

### Reaping idle browsers (MCO-517)

**A browser you have stopped using is the most expensive idle thing on the box
after a Kotlin daemon, and unlike one it never reaps itself.** Measured: **~960
MB per session** — 789 MB of Chromium across 10 processes, plus a 170 MB node
daemon. The compile daemon has `autoshutdownIdleSeconds`; a playwright daemon
runs until something stops it.

`--prune` does **not** cover this. It only reaps sessions whose *worktree is
gone*; a live worktree you last looked at three hours ago keeps its browser.

```bash
webapp/scripts/worktree-playwright.sh --reap-idle        # stop browsers idle >30m
webapp/scripts/worktree-playwright.sh --reap-idle 5      # stricter
webapp/scripts/worktree-playwright.sh --status           # what is running, and how idle
```

- **Runs from a `Stop` hook**, so it happens without anyone remembering it. It
  prints nothing unless it actually reaps something.
- **Stop, never delete.** The profile is that worktree's cookie jar and page
  state, and a stopped session restarts from it in ~2s. `--prune` deletes
  because there is no worktree left to sign back in to; `--reap-idle` does not.
- **Idleness comes from a stamp the shim writes** on every real command, under
  `~/.cache/seam/playwright-sessions/`. Housekeeping (`session-*`, `close`,
  `config`) deliberately does not stamp — otherwise the reaper would refresh the
  stamps it is about to read. A session with no stamp is stamped, not reaped:
  never reap something whose age is unknown.
- **Same namespace guarantee as `--prune`** — only `mc_org*` sessions.

**When you finish verifying, close the browser yourself** — that frees the full
~960 MB now instead of up to 30 minutes from now:

```bash
playwright-cli close        # closes THIS worktree's session
```

That is a hint, not the guarantee; the reaper is the guarantee. If the box is
thrashing, `--status` and `pgrep -af KotlinCompileDaemon` are the two lists to
look at, and note that closing finished worktrees entirely beats both — an
active worktree is ~3 GB all in (2.1 GB daemon + ~1 GB browser).
