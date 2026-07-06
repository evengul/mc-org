# mc-web

HTTP layer — Ktor routes, handlers, Kotlin HTML templates, database access, auth, and Flyway migrations. This is the application's entry point and the only module with framework/infrastructure dependencies.

## Purpose

Wires everything together: HTTP routing, authentication, database access, server-side HTML rendering, and all business pipeline steps.

## Tech

- Depends on: all other modules (`mc-domain`, `mc-pipeline`, `mc-data`, `mc-nbt`, `mc-engine`)
- Ktor 3.4.0 (Netty), Kotlin HTML DSL, HTMX
- PostgreSQL (HikariCP connection pool), Flyway migrations
- Auth: JWT + Microsoft OAuth
- Test: Testcontainers (PostgreSQL), WireMock, MockK, Ktor test host
- Maven build, JVM 21 target
- Entry point: `app.mcorg.ApplicationKt`

## Structure

```
config/              — App configuration, API providers, cache management
domain/idea/         — Idea-specific domain extensions
pipeline/
  admin/             — Admin pipeline steps
  auth/              — Authentication pipeline (Microsoft OAuth -> Minecraft profile)
  failure/           — Shared failure types (PipelineFailure)
  idea/              — Idea CRUD pipelines (create, read, filter, rate, comment)
  invitation/        — Invitation pipeline steps
  minecraftfiles/    — File upload processing (litematica parsing)
  notification/      — Notification pipeline steps
  profile/           — User profile pipelines
  project/           — Project CRUD, settings, resources, dependencies
  resources/         — Resource management pipelines
  task/              — Task pipeline steps
  world/             — World CRUD, settings, members, invitations, roadmap
presentation/
  consts/            — Route constants
  handler/           — HTTP route handlers (one per feature area)
  plugins/           — Ktor plugins (auth, role-based access)
  router/            — Route registration
  security/          — Security utilities
  templated/         — Kotlin HTML templates
    common/          — Reusable components (buttons, forms, modals, tabs, breadcrumbs, etc.)
    idea/            — Idea page templates
    profile/         — Profile templates
    project/         — Project templates
    utils/           — Template utilities
  utils/             — Response helpers, URL utilities
```

## Critical Rules

**Imports:** `import kotlinx.html.stream.createHTML` — NEVER `import kotlinx.html.createHTML`

**Responses:** All responses are HTML fragments — NEVER JSON

**Auth:** Authorization via Ktor plugins at route level — NEVER inside pipelines

**SQL:** Use `SafeSQL.select/insert/update/delete/with()` — NEVER constructor or string interpolation

**Styles:** Use CSS utility classes — NEVER inline `style =`

## Database

- Migrations: `src/main/resources/db/migration/` (Flyway naming: `V{n}__{description}.sql`)
- Access via `SafeSQL` and `DatabaseSteps` — type-safe query builder
- Connection pool: HikariCP

## Build

```bash
cd webapp && mvn compile -pl mc-web
mvn test -pl mc-web
```

## Tests

- **Unit tests**: Pipeline step tests with MockK
- **Integration tests**: `*IT.kt` files using Ktor test host + Testcontainers PostgreSQL + WireMock
- **Test helpers**: `WithUser` for authenticated test contexts, `TestDataFactory` for fixtures
- **Database tests**: `DatabaseTestExtension` provides a fresh Testcontainers PostgreSQL per test class

## Skills

Project-specific skills provide detailed guidance for mc-web development. The `docs-*` reference skills are
auto-loaded by Claude when the task matches their description (they are not user-invocable slash commands).
The action commands below are slash-invocable workflows.

**Reference docs (auto-loaded):**

| Skill              | Auto-loads when...                                        |
|--------------------|-----------------------------------------------------------|
| `docs-development` | Pipeline steps, `handlePipeline`, DB ops, validation      |
| `docs-architecture`| Domain model, file structure, plugin chain, route setup   |
| `docs-frontend`    | DSL component functions, CSS classes, design tokens, layout, page shell — writing/editing templates |
| `docs-product`     | Design system tokens, component patterns, motion, mobile behaviour — UI review and design intent |
| `docs-htmx`        | HTMX helper functions, `hx*` attributes, HTMX patterns    |
| `docs-business`    | Business rules, roles, project stages, workflows          |
| `docs-troubleshoot`| Debugging errors, compile failures                        |
| `docs-testing`     | Writing or running tests (unit, integration, pipeline)    |

**Action commands (slash-invocable):**

| Slash command   | Use when...                 |
|-----------------|-----------------------------|
| `/add-endpoint` | Creating a new HTTP endpoint|
| `/add-migration`| Adding a database migration |
| `/add-step`     | Creating a new pipeline Step|
