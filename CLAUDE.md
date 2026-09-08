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

Read-only diagnostic for "why did the planner pick *that* source?" — prints what each price is
made of, in minutes, against the real ingested graph. Required reading before any change to
`UnitCostModel` or its effort table; see `mc-engine/CLAUDE.md` for the other modes and the
`mvn install` prerequisite:

```bash
cd webapp && mvn -q -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=<id> why <item ids>"
```

*(`score-diagnostics` was its predecessor and is gone with `SelectionScorer` — MCO-490.)*

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
- It auto-generates JWT signing keys (`mc-web/create-keys.sh`) on first run if missing. They are
  one pair per *machine* (`~/.config/seam/keys`), copied into each checkout — see footnote ² in
  [documentation/configuration.md](documentation/configuration.md).
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
the `worktree-infrastructure` skill (Maven repository isolation).

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
    the `worktree-infrastructure` skill.

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

## Worktree Infrastructure

A worktree is isolated from the main checkout and from sibling worktrees on five axes —
database (its own Neon branch), HTTP port, Maven repository, Kotlin compile daemon, and
browser session. **The mechanics, the scripts, and the failure each one prevents are in
the `worktree-infrastructure` skill** — load it when provisioning a worktree, when one
misbehaves (no demo user, "You don't have permission", a port collision, a `-pl` build
that cannot resolve `app.mcorg` jars, playwright driving the wrong app), or **when the
box is thrashing** — idle Kotlin daemons (~2.1 GB each) and Playwright browsers (~960 MB
each) from finished worktrees are the usual cause, and each has its own reaper.

## Issue Tracking

Linear — workspace: evegul, team: Mcorg. Do NOT create GitHub issues.

## Critical Rules

**Worktree first:** Before making ANY code change, confirm you are NOT in the `master`
checkout. If `git rev-parse --abbrev-ref HEAD` is `master`, STOP and start a worktree
(`git worktree add`, or an agent's `isolation: "worktree"`) before editing — do not edit
files on `master`. If a worktree is impractical, at minimum create a feature branch first
(`git checkout -b <linear-branch>`); never commit work directly onto `master`. See
the `worktree-infrastructure` skill for the mechanics. This is the FIRST thing to
check when work begins, not an afterthought at commit time.

**Imports:** `import kotlinx.html.stream.createHTML` — NOT `import kotlinx.html.createHTML`

**Responses:** All responses are HTML fragments — NEVER JSON

**Auth:** Authorization via Ktor plugins at route level — NEVER inside pipelines

**SQL:** Use `SafeSQL.select/insert/update/delete/with()` — NEVER constructor or string interpolation

**Styles:** Use CSS utility classes — NEVER inline `style =`

## Graph & Scoring — Restricted Area

`mc-engine` (`ItemSourceGraph`, `PlanSelector`, `UnitCostModel`, `PlanQuantifier`) and `mc-data`
extraction steps are the intellectual core of the product. Rules for touching these:

- **Always read `mc-engine/CLAUDE.md` and `mc-data/CLAUDE.md` in full before making any changes.**
- General agents (web layer, UI, pipeline steps) should not modify graph construction or scoring logic without explicit
  instruction.
- `UnitCostModel` and its `EffortTable` in particular — a change to what a source *costs* changes what the
  planner tells a player to do, so it requires a human checkpoint before committing. Flag proposed changes and
  rationale; don't just apply them. The effort numbers are estimates of an unmodelled quantity ("how long does
  this take a player"), so verify against `CuratedSelectionTest` (which pins real acquisition chains) and the
  `cost-diagnostics` CLI on real ingested data, not against reasoning alone. **Check the version carries the
  source type you are reasoning about** — no 1.x version ingests villager trades, which is how a trade constant
  read as inert for months (MCO-524).
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

- `UnitCostModel` / `EffortTable` — any cost or ranking changes
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

Skills live under `.claude/skills/`. Every one is already listed with its description at the
start of a session — consult that listing rather than a table here. Two conventions are not
visible from the listing: the `docs-*` reference skills set `user-invocable: false`, so Claude
auto-loads them but you cannot type them as slash commands; and `linear` / `playwright` are
user-level (`~/.claude/skills/`), shared with the other Seam repos rather than vendored here.

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
- [ ] Graph/cost changes flagged if `mc-engine` was touched