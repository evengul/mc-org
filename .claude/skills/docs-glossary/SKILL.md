---
name: docs-glossary
description: Technical and domain terminology for MC-ORG. Use when clarifying what Pipeline, Step, Result, handlePipeline, SafeSQL, DatabaseSteps, World, Project, Task, or other MC-ORG terms mean.
user-invocable: false
---

# Glossary

Technical and domain terminology for MC-ORG.

---

## Technical Terms

**Pipeline** — Composition of Steps that transforms input → output with explicit error handling. Uses railway-oriented programming. Short-circuits on first failure. Executed via `handlePipeline { }`.

**Step** — Single unit of business logic: `Step<I, E, S>`. Takes input `I`, returns `Result<E, S>`. Testable in isolation. Use `object` for stateless steps, `class` for parameterized.

**Result** — Railway-oriented type: `Result.success(value)` or `Result.failure(error)`. Never throws for business logic errors. Operations: `.map()`, `.flatMap()`, `.getOrElse{}`, `.getOrNull()`.

**handlePipeline** — Handler helper that executes a block of Steps with `onSuccess`/`onFailure` callbacks. Default error handler handles most cases automatically.

**PipelineScope** — DSL scope inside `handlePipeline { }`. Use `.run()` to execute a Step and short-circuit on failure. Use `parallel()` for concurrent independent operations.

**pipelineResult** — Like `handlePipeline` but returns `Result<E, O>` directly instead of callbacks. Use when you need the result value in calling code.

**SafeSQL** — SQL wrapper preventing injection. Factory methods: `.select()`, `.insert()`, `.update()`, `.delete()`, `.with()`. Private constructor — must use factories.

**DatabaseSteps** — Utility for DB operations: `.query()` (SELECT), `.update()` (INSERT/UPDATE/DELETE), `.transaction()` (multi-step atomic), `.batchUpdate()` (bulk).

**AppFailure** — Sealed interface hierarchy for all app errors: `ValidationError`, `DatabaseError`, `AuthError`, `ApiError`, `Redirect`, `FileError`, `IllegalConfigurationError`.

**handlePipeline default error handler** — Automatically matches `AppFailure` subtypes to appropriate HTTP responses (400, 403, 404, 500). Logs errors. Rarely need to override.

**Plugin (Ktor)** — Intercepts requests before handler. Used for auth, param extraction, role checking. Installed with `install(PluginName)` on a route. Order matters.

**Handler** — `suspend fun ApplicationCall.handleXxx()` function. Extracts params, executes pipeline, returns HTML.

**respondHtml** — Extension on `ApplicationCall` to respond with HTML. Signature: `respondHtml(htmlString: String)`. Use `createHTML().div { ... }.toString()` or `createPage(user, title) { }`.

**createHTML** — Must use `kotlinx.html.stream.createHTML` (NOT `kotlinx.html.createHTML`). Produces server-side HTML strings.

**HTMX** — JS library for dynamic HTML updates. Server returns HTML fragments; HTMX swaps them. Key attributes: `hx-get/post/put/patch/delete`, `hx-target`, `hx-swap`.

**ApiProvider** — Sealed class for external HTTP calls. Methods return Step types. Built-in rate limiting and error handling. Config objects: `ModrinthApiConfig`, `MinecraftApiConfig`, etc.

---

## Domain Terms

**World** — A Minecraft server/save containing projects and members. Private by default (invitation-only). Has OWNER, ADMIN, MEMBER, BANNED roles.

**Project** — A building task or system within a World. Types: BUILDING, CONTRAPTION, INFRASTRUCTURE, DECORATION, OTHER. Stages: PLANNING → DESIGN → RESOURCE_GATHERING → BUILDING → REVIEW → COMPLETE → ARCHIVED.

**ItemTask** — Task for collecting materials. Has `List<ItemRequirement>` (itemId, quantityRequired, quantityDone). Done when all quantities met.

**ActionTask** — Task for performing work. Binary `completed: Boolean`. Has `ActionRequirement(description)`.

**TaskPriority** — CRITICAL / NORMAL / NICE_TO_HAVE.

**Idea** — Global design template. Has category (FARM, CONTRAPTION, BUILDING, DECORATION, UTILITY, OTHER), difficulty (EASY, MEDIUM, HARD, EXPERT), `categoryData: Map<String, CategoryValue>` (JSONB).

**Invite** — World membership request. Status: PENDING → ACCEPTED / DECLINED / CANCELLED.

**Notification** — System message to user. Types: INVITE_RECEIVED, INVITE_ACCEPTED, INVITE_DECLINED, PROJECT_COMPLETED, TASK_ASSIGNED, DEPENDENCY_READY, ROLE_CHANGED.

**ProjectDependency** — `dependentProject` requires `dependencyProject` to complete first. No circular deps.

**TokenProfile** — Decoded JWT as Kotlin data class. Access via `call.getUser()`. Key: `user.id` (NOT `user.userId`).

**WorldMember** — User's membership in a world. Has `role: Role` (OWNER/ADMIN/MEMBER/BANNED).

---

## Role Levels

| Role | Level | `isHigherThanOrEqualTo` |
|------|-------|------------------------|
| OWNER | 0 | all roles |
| ADMIN | 10 | ADMIN, MEMBER, BANNED |
| MEMBER | 100 | MEMBER, BANNED |
| BANNED | 1000 | BANNED only |

---

## Acronyms

- **ADR** — Architecture Decision Record
- **BEM** — Block Element Modifier (CSS naming)
- **CTE** — Common Table Expression (SQL `WITH` clause)
- **DSL** — Domain-Specific Language
- **HTMX** — HTML eXtensions library
- **JWT** — JSON Web Token
- **NBT** — Named Binary Tag (Minecraft file format)
- **SSR** — Server-Side Rendering
