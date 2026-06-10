# MC-ORG Project Context

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
| Backend  | Ktor 3.4.0 + Kotlin 2.2.21 | JVM 21, Netty, port 8080                                       |
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
mvn test                            # Run all tests
sudo service docker start           # Start Docker if not running (passwordless)
./webapp/scripts/start-db.sh        # Start the database
./webapp/scripts/migrate-locally.sh # Apply database migrations
./webapp/scripts/run.sh             # Start development server
```

Module-scoped builds:

```bash
cd webapp && mvn compile -pl mc-engine   # Single module
cd webapp && mvn test -pl mc-web         # Single module tests
```

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

- `localhost` in WSL2 ≠ Windows localhost.
- **Database access:** Verify the postgres MCP server is available (`/mcp`) before any database read or query. Always
  use MCP tools for database access — never `psql` or other CLI tools.

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
worktree, using the same Neon project (`morning-fog-11467472`).

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

`mc-engine` (`ItemSourceGraph`, `ItemSourceGraphQueries`, `PathSuggestionScorer`, `PathSuggestionService`) and `mc-data`
extraction steps are the intellectual core of the product. Rules for touching these:

- **Always read `mc-engine/CLAUDE.md` and `mc-data/CLAUDE.md` in full before making any changes.**
- General agents (web layer, UI, pipeline steps) should not modify graph construction or scoring logic without explicit
  instruction.
- `PathSuggestionScorer` in particular — scoring logic changes require a human checkpoint before committing. Flag
  proposed changes and rationale; don't just apply them.
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

- `PathSuggestionScorer` — any scoring weight or ranking changes
- `ItemSourceGraph` structure changes — new edge or node types
- Flyway migrations that drop columns or tables
- Auth plugin changes

**Always:**

- Run `mvn clean compile` before considering any task complete
- Run `mvn test` and ensure all tests pass before committing
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
fixtures. The `docs-testing` skill is auto-loaded when writing or running tests.

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
| `docs-testing`      | Writing or running tests — unit, integration, pipeline step   |
| `docs-business`     | Business rules, roles, project stages, workflows              |
| `docs-troubleshoot` | Debugging errors, compile failures                            |
| `docs-glossary`     | Technical/domain terminology                                  |

### Action commands — user-invocable slash commands

These run workflows and are meant to be triggered explicitly with `/name` by the user (or by Claude when the
request clearly maps to the action).

| Slash command   | Use when...                                                |
|-----------------|------------------------------------------------------------|
| `/add-endpoint` | Creating a new HTTP endpoint                               |
| `/add-migration`| Adding a database migration                                |
| `/add-step`     | Creating a new pipeline Step                               |
| `/commit`       | Compile → test → stage → commit with a good message        |
| `/linear`       | Create, update, or link Linear issues                      |
| `/devstart`     | Full dev environment startup (Docker → DB → migrate → run) |
| `/run`          | Start the application only (with optional flags)           |
| `/migrate`      | Run Flyway migrations locally                              |
| `/start-db`     | Start the PostgreSQL container                             |

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
- [ ] `mvn test` passes with all tests
- [ ] Tests written for new functionality (see Test Expectations)
- [ ] No inline styles — use CSS classes
- [ ] Authorization in plugins, not pipelines
- [ ] HTMX targets match response element IDs
- [ ] Correct import: `stream.createHTML`
- [ ] Linear issue linked if applicable
- [ ] Graph/scoring changes flagged if `mc-engine` was touched