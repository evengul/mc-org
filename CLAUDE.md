# MC-ORG Project Context

Minecraft World Collaboration Platform — managing building projects, tasks, and team coordination.

## Tech Stack

| Layer    | Technology                 | Notes                        |
|----------|----------------------------|------------------------------|
| Backend  | Ktor 3.0.3 + Kotlin 2.1.10 | JVM 21, Netty, port 8080     |
| Database | PostgreSQL + Flyway         | Current migration: V2_21_0   |
| Frontend | Kotlin HTML DSL + HTMX     | Server-side rendering only   |
| Build    | Maven                      | NOT Gradle                   |
| Deploy   | Docker + Fly.io            |                              |

## Build Commands

```bash
mvn clean compile     # Compile (must pass with zero errors)
mvn test              # Run tests
mvn flyway:migrate    # Apply database migrations
mvn exec:java         # Start development server
```

## Environment (WSL2)

- `localhost` in WSL2 ≠ Windows localhost. IntelliJ MCP: use Windows host IP (`cat /etc/resolv.conf | grep nameserver`)
- Docker requires systemd as PID 1. Config at `/etc/wsl.conf`
- MCP: check `/mcp` before DB operations

## Critical Rules

**Imports:** `import kotlinx.html.stream.createHTML` — NOT `import kotlinx.html.createHTML`

**Responses:** All responses are HTML fragments — NEVER JSON

**Auth:** Authorization via Ktor plugins at route level — NEVER inside pipelines

**SQL:** Use `SafeSQL.select/insert/update/delete/with()` — NEVER constructor or string interpolation

**Styles:** Use CSS utility classes — NEVER inline `style =`

**User:** `user.id` — NOT `user.userId`

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
| `/add-endpoint`     | Creating a new HTTP endpoint                              |
| `/add-migration`    | Adding a database migration                               |
| `/add-step`         | Creating a new pipeline Step                              |

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
