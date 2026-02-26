# MC-ORG Project Context

Minecraft World Collaboration Platform — managing building projects, tasks, and team coordination.

## Developer

Even — solo developer, expert in the full stack (built this project from the ground up). Communicate concisely: short explanations with rationale on non-obvious decisions. Ask when uncertain; follow-up questions are welcome.

## Tech Stack

| Layer    | Technology                 | Notes                        |
|----------|----------------------------|------------------------------|
| Backend  | Ktor 3.0.3 + Kotlin 2.1.10 | JVM 21, Netty, port 8080     |
| Database | PostgreSQL + Flyway         | Migrations in `webapp/src/main/resources/db/migration/` |
| Frontend | Kotlin HTML DSL + HTMX     | Server-side rendering only   |
| Build    | Maven                      | NOT Gradle                   |
| Deploy   | Docker + Fly.io            |                              |

## Build Commands

```bash
mvn clean compile                   # Compile (must pass with zero errors)
mvn test                            # Run tests
sudo service docker start           # Start docker if not running (can run without password)
./webapp/scripts/start-db.sh        # Start the database
./webapp/scripts/migrate-locally.sh # Apply database migrations
./webapp/scripts/run.sh             # Start development server
```

## Environment (WSL2)

- `localhost` in WSL2 ≠ Windows localhost.
- **Database access:** Before any database read or query, verify the postgres MCP server is available (`/mcp`). Always use MCP tools for database access — never `psql` or other CLI database tools.

## Issue Tracking

Linear — workspace: evegul, team: Mcorg. Do NOT create GitHub issues.

## Critical Rules

**Imports:** `import kotlinx.html.stream.createHTML` — NOT `import kotlinx.html.createHTML`

**Responses:** All responses are HTML fragments — NEVER JSON

**Auth:** Authorization via Ktor plugins at route level — NEVER inside pipelines

**SQL:** Use `SafeSQL.select/insert/update/delete/with()` — NEVER constructor or string interpolation

**Styles:** Use CSS utility classes — NEVER inline `style =`

## Skills — Load On Demand

Invoke with the Skill tool when the task matches:

| Skill               | Load when...                                              |
|---------------------|-----------------------------------------------------------|
| `/docs-development` | Pipeline steps, `handlePipeline`, DB ops, validation      |
| `/docs-architecture`| Domain model, file structure, plugin chain, route setup   |
| `/docs-css`         | CSS component classes, layout, notices, cards             |
| `/docs-htmx`        | HTMX helper functions, `hx*` attributes, HTMX patterns    |
| `/docs-business`    | Business rules, roles, project stages, workflows          |
| `/docs-troubleshoot`| Debugging errors, compile failures                        |
| `/docs-glossary`    | Technical/domain terminology                              |
| `/docs-testing`     | Test patterns, `testApplication`, auth helpers, DB tests  |
| `/add-endpoint`     | Creating a new HTTP endpoint                              |
| `/add-migration`    | Adding a database migration                               |
| `/add-step`         | Creating a new pipeline Step                              |
| `/commit`           | Compile → test → stage → commit with a good message       |
| `/linear`           | Create, update, or link Linear issues                     |
| `/devstart`         | Full dev environment startup (Docker → DB → migrate → run)|
| `/run`              | Start the application only (with optional flags)          |
| `/migrate`          | Run Flyway migrations locally                             |
| `/start-db`         | Start the PostgreSQL container                            |

## Working Style

- Implement, don't just plan. Make code changes directly for well-understood tasks.
- Break large refactors into committed phases. Run tests and commit between phases.
- Analyze logs before guessing. Read error logs and stack traces first.

## Before Committing

- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all tests
- [ ] Tests written for new functionality
- [ ] No inline styles — use CSS classes
- [ ] Authorization in plugins, not pipelines
- [ ] HTMX targets match response element IDs
- [ ] Correct import: `stream.createHTML`
- [ ] Linear issue linked if applicable
