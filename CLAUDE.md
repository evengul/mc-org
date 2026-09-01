# Seam Project Context

> **Naming:** The product is called **Seam**. The internal name `mc-org` (and derivatives `app.mcorg`, Maven module names `mc-*`, Fly app, Neon project, DB) remains unchanged and should NOT be renamed — these are infrastructure and package identifiers, not the product brand.

Minecraft resource planning tool — players define projects (builds, farms, contraptions),
the app resolves their resource dependencies by traversing a graph built from Minecraft's crafting and loot data,
and generates an ordered project roadmap.

## Developer

Even — solo developer, expert in the full stack. Communicate concisely:
short explanations with rationale on non-obvious decisions.
Ask when uncertain; follow-up questions welcome.
Implement directly for well-understood tasks — don't plan when you can act.

## Tech Stack

| Layer    | Technology                 | Notes                                                          |
|----------|----------------------------|----------------------------------------------------------------|
| Backend  | Ktor 3.5.0 + Kotlin 2.3.21 | JVM 25, Netty, port 8080 unless `PORT` says otherwise — see [Worktree Port Isolation](#worktree-port-isolation) (versions pinned in `webapp/mc-bom/pom.xml` / `webapp/pom.xml`) |
| Database | PostgreSQL + Flyway        | Migrations in `webapp/mc-web/src/main/resources/db/migration/` |
| Frontend | Kotlin HTML DSL + HTMX     | Server-side rendering only                                     |
| Build    | Maven                      | NOT Gradle                                                     |
| Deploy   | Docker + Fly.io            |                                                                |

## Modules

Multi-module Maven build under `webapp/`. Dependency flow: `mc-domain` and `mc-pipeline` are leaves. `mc-engine`,
`mc-nbt`, `mc-data` depend on those. `mc-web` depends on all.

| Module        | Purpose                                                                                                                                                                                 |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `mc-domain`   | Pure domain models and value objects. No logic, no dependencies. Changes here ripple everywhere — keep minimal and stable.                                                              |
| `mc-pipeline` | Generic railway-oriented pipeline framework — `Step<I, E, S>`, `Result<E, V>`, `PipelineScope`. Foundation for all other modules.                                                       |
| `mc-engine`   | **Core of the product.** Bipartite item-source graph (`ItemNode` <-> `SourceNode`), graph traversal queries, and path scoring. See `mc-engine/CLAUDE.md` before touching anything here. |
| `mc-data`     | Minecraft JAR extraction — parses recipes, loot tables, items, and tags from JSON data files into `ServerData`. Feeds `mc-engine`'s graph builder. See `mc-data/CLAUDE.md`.             |
| `mc-nbt`      | NBT binary format parser for Minecraft schematics and Litematica files.                                                                                                                 |
| `mc-web`      | HTTP layer — Ktor routes, handlers, Kotlin HTML templates, database access, auth, Flyway migrations. Entry point: `app.mcorg.ApplicationKt`.                                            |

## Build Commands

```bash
mvn clean compile                   # Compile (must pass with zero errors)
./webapp/scripts/test.sh            # Run tests — see "Running Tests" below (NOT bare `mvn test`)
sudo service docker start           # Start Docker if not running (passwordless)
./webapp/scripts/start-db.sh        # Start the database
./webapp/scripts/migrate-locally.sh # Apply migrations to the localhost Docker DB (main checkout)
./webapp/scripts/migrate-worktree.sh # Apply migrations to the DB local.env points at (worktree Neon branch)
./webapp/scripts/run.sh             # Start development server (builds first)
./webapp/scripts/ingest-locally.sh  # Ingest Minecraft data into the local/worktree DB
```

Read-only diagnostic for "why did the planner pick *that* source?" — prints the scorer's factor
breakdown per candidate against the real ingested graph. Required reading before any
`SelectionScorer` change; see `mc-engine/CLAUDE.md` for args and the `mvn install` prerequisite:

```bash
cd webapp && mvn -q -pl mc-web exec:java@score-diagnostics -Dexec.args="world=<id> demand=64 <item ids>"
```

Module-scoped builds:

```bash
cd webapp && mvn compile -pl mc-engine   # Single module
```

### Running Tests

**Use `webapp/scripts/test.sh`. Do NOT reach for bare `mvn test`** — it runs the unit
tier only. The mc-web database/integration tests carry the JUnit 5 tag `database`, and
`pom.xml` sets `<surefire.excludedGroups>database</surefire.excludedGroups>`, so a plain
`mvn test` (or `mvn test -Dtest=SomeIT`) **silently skips every `*IT` test and reports 0
matches** — the name filter never overrides the excluded group. `test.sh` is the single
entry point that knows how to run each tier:

```bash
./webapp/scripts/test.sh                       # Unit tests only (no Docker) — the default
./webapp/scripts/test.sh --database            # + database-tagged tests (mc-web ITs; needs Docker)
./webapp/scripts/test.sh --integration         # + failsafe integration tests (needs Docker + app running)
./webapp/scripts/test.sh --database --exclude-unit-tests   # database tier only

# Everything after a literal `--` is forwarded verbatim to the underlying `mvn` runs.
# Narrow to one class (note the -pl, since passthrough targets the reactor):
./webapp/scripts/test.sh --database -- -pl mc-web -Dtest=GetProjectListIT -Dsurefire.failIfNoSpecifiedTests=false
```

What each flag maps to (run these by hand only if you must bypass the script):

| Tier        | test.sh flag    | Equivalent maven invocation                                                      |
|-------------|-----------------|----------------------------------------------------------------------------------|
| Unit        | *(default)*     | `mvn test`                                                                        |
| Database    | `--database`    | `mvn test -pl mc-web -Dsurefire.excludedGroups= -Dgroups=database`                |
| Integration | `--integration` | `mvn failsafe:integration-test failsafe:verify -pl mc-web`                        |

This mirrors CI (`.github/workflows/dev.yml`): the `unit-tests` job runs the default
tier; the `integration-tests` job runs `-Dgroups=database`.

Notes:
- It auto-generates JWT signing keys (`mc-web/create-keys.sh`) on first run if missing.
- `--integration` (failsafe) expects a running server; the `--database` tier is
  self-contained (Testcontainers spins up its own PostgreSQL).

### Stale classes in a module-scoped build (MCO-285)

**Symptom:** `NoSuchMethodError` or `NoClassDefFoundError` at *runtime*, naming a class or
constructor that plainly exists in your working tree, from a file you did not touch.

**Cause:** `-pl <module>` resolves siblings from `~/.m2`, not the reactor, so a module-scoped
build runs against the last *installed* jars. Add `-am` to pull the siblings into the reactor,
or `mvn install` the whole thing first. Local-only — CI always builds the full reactor.

`run.sh` installs before it runs, and `test.sh`'s module-scoped tiers pass `-am`. If you bypass
the scripts, remember the rule yourself.

In a worktree those installs go to the worktree's **own** Maven repository, so a sibling
worktree's build can't swap the jars underneath you — see
[Worktree Maven Repository Isolation](#worktree-maven-repository-isolation).

There used to be a second cause here — Kotlin incremental compilation dropping cross-module ABI
changes — which is why `run.sh` forced `mvn clean` and `test.sh` had a `--clean` flag. MCO-378
turned incremental compilation off (`webapp/pom.xml`) and removed both workarounds. A full
rebuild of all six modules is ~13s; don't re-add the property.

## Minecraft Data Ingestion

The app's Minecraft data (items, recipes, loot tables, tags, villager trades) is
extracted from Mojang server JARs by `mc-data` and stored in the DB. In production a
daily Fly machine (`webapp/scripts/ingest-machine.sh`) runs the CLI entry point
`app.mcorg.cli.IngestServerFilesKt`, which executes `GetServerFilesPipeline` once and
exits — no Ktor server.

To run the same ingestion **locally** against your local/worktree DB:

```bash
./webapp/scripts/ingest-locally.sh   # sources local.env, compiles, runs the ingest CLI
```

It is ledger-driven and idempotent (`minecraft_version_ingestion` table + server-JAR
SHA check, guarded by a pg advisory lock): only new or changed versions are downloaded
and stored, so re-runs are cheap. Note a worktree's Neon branch is forked from `master`
and already carries production's ingested data, so a local run often mostly no-ops.

## CI / PR Previews

`.github/workflows/dev.yml` runs `compile` + `unit-tests` + `integration-tests` on
**every** PR touching source paths. The `deploy-dev` job (Neon branch + Docker image +
ephemeral Fly app `mcorg-dev-<PR#>` + preview-URL comment) is **opt-in**: it only runs
when the PR carries the **`preview`** label.

- **No `preview` label** → tests run, no Fly preview deploy. This is the default; use it
  for backend / data / logic changes with nothing to eyeball.
- **Want a preview** → add the **`preview`** label. Applying it fires `dev.yml`'s
  `labeled` trigger and runs compile → test → deploy with the preview included. Apply it
  at PR-open time to avoid a redundant second run. `cleanup-dev.yml` tears the preview
  down (Fly app + Neon branch) when a `preview`-labelled PR closes.

## Environment (WSL2)

- **What may go in a log line is governed by [documentation/logging.md](documentation/logging.md)**
  — pseudonymous posture (Minecraft UUID/username fine; emails, query strings, raw driver
  messages and user-authored content not), plus the two libraries whose exception messages carry
  payloads. Read it before adding a `logger.error(..., e)`.
- **The outbound webhook wire contract is [documentation/webhook-contract.md](documentation/webhook-contract.md)**
  — mc-org is the producer, so that file is canonical rather than the consumer's README. Covers the
  signature's raw-bytes rule, the single-vs-batch body duality, the envelope, the retry schedule and
  the 10-failure auto-deactivation. Read it before changing anything under `webhook/` or
  `EventEnvelope`; a change there is a change to another repo's input.
- **Every environment variable is listed in [documentation/configuration.md](documentation/configuration.md)**
  — what it does, which environments require it, its default, and where it is set. `readConfig()`
  in `mc-web/.../config/ConfigLoader.kt` is the only place in `src/main` that calls
  `System.getenv`; a bad configuration exits at startup rather than surfacing later.
- `localhost` in WSL2 ≠ Windows localhost.
- **Database access:** Use `psql`. There are two databases depending on where you are, and
  one client reaches both:
  - **Main checkout** — the localhost Docker postgres (`webapp/scripts/start-db.sh`).
  - **Worktree** — that worktree's Neon branch (`wt/<git-branch>`), see
    [Worktree Database Isolation](#worktree-database-isolation).

  Either way, read the credentials from the `local.env` next to you rather than
  hardcoding a host — that file is the single source of truth for which DB you are
  pointed at, and it is what the app itself reads:

  ```bash
  set -a && . webapp/local.env && set +a
  # DB_URL already ends in ?sslmode=require, so the credentials append with & (not ?).
  psql "$(printf '%s' "$DB_URL" | sed 's|^jdbc:||')&user=$DB_USER&password=$DB_PASSWORD" -c '\dt'
  ```

  Sourcing `local.env` and building that URL in one shell command can trip the
  worktree-isolation guard. If it does, put the two lines in a throwaway script and run
  that instead — same result, and it keeps the password out of the transcript.
- **To check which DB you actually have**, compare the host in `local.env`'s `DB_URL`
  against `neonctl connection-string <branch> --project-id sweet-dust-00910797`; a
  worktree's host differs from `master`'s. The startup log names the pool profile and the
  environment (`... with the local profile (ENV=Local)`), which since MCO-335 is keyed off
  `ENV` rather than a hostname substring.

  **Writes:** free in a worktree — the Neon branch is a disposable fork. Against the main
  checkout's DB, treat `INSERT`/`UPDATE`/`DELETE`/DDL as you would any shared dev data:
  prefer the app or a migration, and say what you are about to run before running it.

  **Schema changes always go through a Flyway migration**, never a hand-typed `ALTER` —
  a manual change drifts the DB from `db/migration/` and the next `flyway:migrate`
  will not know about it.

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
   - Runs `flyway:migrate` against the isolated branch
3. The worktree's app and tests now target the worktree's own branch — never the
   shared dev DB — and any migrations you run land on the isolated branch.
4. On `ExitWorktree`, `webapp/scripts/worktree-db-cleanup.sh --prune` reconciles
   live `wt/*` Neon branches against `git worktree list` and deletes orphans.
5. **Manual worktrees** (e.g. `claude -w`, where the EnterWorktree hook may not fire):
   run `bash webapp/scripts/worktree-db.sh` yourself from the worktree root.

**Why fork production?** Same as the CI previews — a copy-on-write branch inherits
the real ingested Minecraft data instantly (no re-ingestion) and matches CI exactly.

**Caveats:**

- `webapp/local.env` is **gitignored**. The main checkout's copy is the single
  source for non-DB local config (Microsoft, Modrinth, `ENV`, …); the setup script
  writes each worktree's `local.env` fresh from it, swapping in the branch's Neon
  `DB_*` values. A fresh clone has no `local.env` — copy `webapp/local.env.example`
  (the committed template) to create it; `run.sh` seeds it for you on first run, and
  `worktree-db.sh` falls back to the template so worktrees provision even before the
  main checkout's `local.env` exists.
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
needs a flag, and `score-diagnostics` and friends are covered automatically.

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

## Issue Tracking

Linear — workspace: evegul, team: Mcorg. Do NOT create GitHub issues.

## Critical Rules

**Worktree first:** Before making ANY code change, confirm you are NOT in the `master`
checkout. If `git rev-parse --abbrev-ref HEAD` is `master`, STOP and start a worktree
(`git worktree add`, or an agent's `isolation: "worktree"`) before editing — do not edit
files on `master`. If a worktree is impractical, at minimum create a feature branch first
(`git checkout -b <linear-branch>`); never commit work directly onto `master`. See
[Worktree Database Isolation](#worktree-database-isolation). This is the FIRST thing to
check when work begins, not an afterthought at commit time.

**Imports:** `import kotlinx.html.stream.createHTML` — NOT `import kotlinx.html.createHTML`

**Responses:** All responses are HTML fragments — NEVER JSON

**Auth:** Authorization via Ktor plugins at route level — NEVER inside pipelines

**SQL:** Use `SafeSQL.select/insert/update/delete/with()` — NEVER constructor or string interpolation

**Styles:** Use CSS utility classes — NEVER inline `style =`

## Graph & Scoring — Restricted Area

`mc-engine` (`ItemSourceGraph`, `PlanSelector`, `SelectionScorer`, `PlanQuantifier`) and `mc-data`
extraction steps are the intellectual core of the product. Rules for touching these:

- **Always read `mc-engine/CLAUDE.md` and `mc-data/CLAUDE.md` in full before making any changes.**
- General agents (web layer, UI, pipeline steps) should not modify graph construction or scoring logic without explicit
  instruction.
- `SelectionScorer` in particular — scoring logic changes require a human checkpoint before committing. Flag
  proposed changes and rationale; don't just apply them. Its weights were chosen by feel rather than derived, and
  the windows between them are narrow: verify a change against `CuratedSelectionTest` (which pins real acquisition
  chains) and the `score-diagnostics` CLI against real ingested data, not against reasoning alone.
- Graph shape changes (new edge types, new node types) require reviewing `ItemSourceGraphBuilder` and all existing query
  code for impact.

## Autonomous Agent Guidance

**Act freely on:**

- `mc-web` pipeline steps, handlers, templates, routes
- New database migrations (follow Flyway naming: `V{n}__{description}.sql`)
- CSS, HTMX patterns, template components
- `mc-nbt` — isolated parser, low blast radius
- `mc-domain` additions (new fields, new models) — but no removals without checking all consumers

**Read sub-module CLAUDE.md first, then act:**

- `mc-data` — extraction steps, parsers, new recipe/loot types
- `mc-engine` — graph queries, new traversal logic

**Flag before acting (human checkpoint):**

- `SelectionScorer` — any scoring weight or ranking changes
- `ItemSourceGraph` structure changes — new edge or node types
- Flyway migrations that drop columns or tables
- Auth plugin changes

**Always:**

- Run `mvn clean compile` before considering any task complete
- Run `./webapp/scripts/test.sh` (add `--database` when the change touches mc-web routes,
  handlers, or DB access) and ensure all tests pass before committing — bare `mvn test`
  skips the `database`-tagged ITs (see [Running Tests](#running-tests))
- Write tests for new functionality (see Test Expectations below)
- Load the relevant skill before starting a task (see Skills table)

## Test Expectations

Tests are not optional. Minimum expectations per task type:

| Task type            | Expected tests                                                    |
|----------------------|-------------------------------------------------------------------|
| New pipeline step    | Unit test covering success path + each distinct failure case      |
| New HTTP endpoint    | Integration test: success, validation failure, auth failure       |
| New graph query      | Unit test with a constructed test graph covering edge cases       |
| New migration        | No test required, but verify locally with `migrate-locally.sh`    |
| Template-only change | Compile passes, existing tests still pass — no new tests required |

Integration tests in `mc-web` use Testcontainers PostgreSQL. Use `WithUser` for auth context and `TestDataFactory` for
fixtures. They are tagged `database` and run via `./webapp/scripts/test.sh --database` — bare `mvn test` skips them (see
[Running Tests](#running-tests)). The `docs-testing` skill is auto-loaded when writing or running tests.

## Skills

Two kinds of skills live under `.claude/skills/`:

### Reference docs — auto-loaded by Claude, not user-invocable

These carry project patterns and conventions. Claude auto-loads the matching one when the task matches its
description — do not rely on memory for patterns covered by a skill, and do not try to invoke these as slash
commands (they have `user-invocable: false`).

| Skill               | Auto-loads when...                                            |
|---------------------|---------------------------------------------------------------|
| `docs-development`  | Pipeline steps, `handlePipeline`, DB ops, validation          |
| `docs-architecture` | Domain model, file structure, plugin chain, route setup       |
| `docs-frontend`     | DSL component functions, CSS classes, design tokens, layout, page shell — writing/editing templates |
| `docs-product`      | Design system tokens, component patterns, motion, mobile behaviour — UI review and design intent |
| `docs-htmx`         | HTMX helper functions, `hx*` attributes, HTMX patterns        |
| `docs-ia`           | Information architecture, URL structure, navigation, personas |
| `docs-planning`     | Scoping features/epics, validating approach, decomposing large work |
| `docs-review`       | Reviewing a diff or PR — pattern compliance, test coverage, restricted areas |
| `docs-testing`      | Writing or running tests — unit, integration, pipeline step   |
| `docs-business`     | Business rules, roles, project stages, workflows              |
| `docs-troubleshoot` | Debugging errors, compile failures                            |

### Action commands — user-invocable slash commands

These run workflows and are meant to be triggered explicitly with `/name` by the user (or by Claude when the
request clearly maps to the action).

| Slash command   | Use when...                                                |
|-----------------|------------------------------------------------------------|
| `/add-endpoint` | Creating a new HTTP endpoint                               |
| `/add-migration`| Adding a database migration                                |
| `/add-step`     | Creating a new pipeline Step                               |
| `/commit`       | Compile → test → stage → commit with a good message        |
| `/devstart`     | Full dev environment startup (Docker → DB → migrate → run) |
| `/run`          | Start the application only (with optional flags)           |
| `/migrate`      | Run Flyway migrations locally                              |
| `/start-db`     | Start the PostgreSQL container                             |
| `/verify`       | Drive the running app end-to-end to confirm a change works |

The `linear` (issue management) and `playwright` (browser automation) skills are
**user-level** (`~/.claude/skills/`) — available here and in the other Seam repos,
not vendored per repo.

## Working Style

- Implement, don't plan. Make code changes directly for well-understood tasks.
- Break large refactors into committed phases. Compile and test between phases.
- Read error logs and stack traces before guessing. Never diagnose blind.
- When multiple valid approaches exist, pick the one consistent with existing patterns in the codebase — don't introduce
  new patterns without flagging it.
- Interview format for missing information — one focused question at a time, not a list.
- **Do not spelunk inside `~/.m2`, vendored jars, or other dependency caches to discover library APIs.** If you need to
  know the exact signature, class hierarchy, or DSL shape of a third-party library (e.g. `kotlinx.html`), either fetch
  the official docs/source from the web with WebFetch/WebSearch, or ask the user directly with a concise, specific
  question. Cracking open jars is slow, noisy, and often gives outdated or obfuscated output.

## Before Committing

- [ ] `mvn clean compile` passes with zero errors
- [ ] `./webapp/scripts/test.sh` passes (with `--database` if mc-web routes/handlers/DB changed) — not bare `mvn test`
- [ ] Tests written for new functionality (see Test Expectations)
- [ ] No inline styles — use CSS classes
- [ ] Authorization in plugins, not pipelines
- [ ] HTMX targets match response element IDs
- [ ] Correct import: `stream.createHTML`
- [ ] Linear issue linked if applicable
- [ ] Graph/scoring changes flagged if `mc-engine` was touched