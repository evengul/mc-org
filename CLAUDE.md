# MC-ORG Project Context

Essential context for AI-assisted development. For detailed documentation, use `/docs-*` commands.

---

## Project Overview

**MC-ORG** is a Minecraft World Collaboration Platform for managing building projects, tasks, and team coordination.

### Tech Stack

| Layer          | Technology                | Notes                           |
|----------------|---------------------------|---------------------------------|
| **Backend**    | Ktor 3.0.3 + Kotlin 2.1.10| JVM 21, Netty server (port 8080)|
| **Database**   | PostgreSQL + Flyway       | Migration version V2_21_0       |
| **Frontend**   | Kotlin HTML DSL + HTMX    | Server-side rendering only      |
| **Build**      | Maven                     | NOT Gradle                      |
| **Deployment** | Docker + Fly.io           |                                 |

### Key Architecture Decisions

- **Server-side HTML** - All responses are HTML (NOT JSON APIs)
- **HTMX for interactivity** - Dynamic updates without full page reload
- **Pipeline pattern** - Railway-oriented programming with `Result<E, S>`
- **Plugin-based auth** - Authorization in Ktor plugins, NOT in pipelines

### Build Commands

```bash
mvn clean compile     # Compile (must pass with zero errors)
mvn test              # Run tests
mvn flyway:migrate    # Apply database migrations
mvn exec:java         # Start development server
```

---

## Critical Patterns

### 1. Pipeline Pattern (Railway-Oriented)

Every endpoint uses pipelines with Steps that return `Result<E, S>`:

```kotlin
suspend fun ApplicationCall.handleCreateProject() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    val pipeline = Pipeline.create<AppFailure, Parameters>()
        .pipe(ValidateInputStep)
        .pipe(CreateProjectStep(user, worldId))
        .pipe(GetUpdatedDataStep)

    pipeline.fold(
        input = parameters,
        onSuccess = { project ->
            respondHtml(createHTML().div {
                projectCard(project)
            })
        },
        onFailure = { failure ->
            // Built-in error handling logs automatically
            respondBadRequest("Failed to create project")
        }
    )
}
```

### 2. Step Interface

```kotlin
interface Step<I, E, S> {
    suspend fun process(input: I): Result<E, S>
}

// Example implementation
object ValidateNameStep : Step<Parameters, AppFailure.ValidationError, ValidatedInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ValidatedInput> {
        val name = input["name"]
        return if (!name.isNullOrBlank() && name.length in 3..100) {
            Result.success(ValidatedInput(name))
        } else {
            Result.failure(AppFailure.ValidationError(
                listOf(ValidationFailure.InvalidValue("name", "Must be 3-100 characters"))
            ))
        }
    }
}
```

### 3. HTMX Response Pattern

**GET endpoints** return full pages via `createPage()`:
```kotlin
respondHtml(createPage(user, "Page Title") {
    // page content
})
```

**PUT/PATCH/POST/DELETE endpoints** return HTML fragments:
```kotlin
respondHtml(createHTML().div {
    id = "target-element"  // Must match hxTarget
    div("notice notice--success") { +"Success!" }
})
```

### 4. Authorization via Plugins (NOT Pipelines)

Authorization is handled at the route level by Ktor plugins BEFORE the handler executes:

```kotlin
fun Route.worldRoutes() {
    route("/worlds/{worldId}") {
        install(WorldParamPlugin)   // Extracts worldId
        install(WorldAdminPlugin)   // Checks admin role

        put {
            call.handleUpdateWorld()  // Only runs if authorized
        }
    }
}
```

**Plugin chain order**: EnvPlugin -> AuthPlugin -> BannedPlugin -> WorldParamPlugin -> RolePlugin -> Handler

---

## Common Imports Cheat Sheet

```kotlin
// HTML Generation - MUST use .stream
import kotlinx.html.stream.createHTML  // NOT kotlinx.html.createHTML
import kotlinx.html.*

// Response Helpers
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondNotFound

// Pipeline & Steps
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Pipeline

// Database
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.DatabaseSteps

// Validation
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure

// Error Handling
import app.mcorg.pipeline.failure.AppFailure

// Auth & Context
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.domain.model.user.TokenProfile

// HTMX Helpers
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxDeleteWithConfirm
```

---

## Database Patterns

### SafeSQL Factory Methods (NEVER use constructor)

```kotlin
// SELECT
val sql = SafeSQL.select("SELECT * FROM projects WHERE world_id = ?")

// INSERT with RETURNING
val sql = SafeSQL.insert("""
    INSERT INTO projects (name, world_id, created_by, created_at, updated_at)
    VALUES (?, ?, ?, NOW(), NOW())
    RETURNING id, name, world_id, created_by, created_at, updated_at
""")

// UPDATE
val sql = SafeSQL.update("UPDATE projects SET name = ?, updated_at = NOW() WHERE id = ?")

// DELETE
val sql = SafeSQL.delete("DELETE FROM projects WHERE id = ?")

// WITH (CTEs)
val sql = SafeSQL.with("WITH ranked AS (SELECT * FROM ...) SELECT * FROM ranked")
```

### DatabaseSteps.query Pattern

```kotlin
val queryStep = DatabaseSteps.query<InputType, OutputType>(
    sql = SafeSQL.select("SELECT * FROM table WHERE id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.id)
    },
    resultMapper = { rs ->
        if (rs.next()) mapToModel(rs) else null
    }
)

val result = queryStep.process(input)
```

### DatabaseSteps.update Pattern

```kotlin
val updateStep = DatabaseSteps.update<InputType>(
    sql = SafeSQL.update("UPDATE table SET name = ? WHERE id = ?"),
    parameterSetter = { statement, input ->
        statement.setString(1, input.name)
        statement.setInt(2, input.id)
    }
)
```

### DatabaseSteps.transaction Pattern

```kotlin
val transactionStep = DatabaseSteps.transaction<InputType, OutputType> { txConn ->
    object : Step<InputType, AppFailure.DatabaseError, OutputType> {
        override suspend fun process(input: InputType): Result<AppFailure.DatabaseError, OutputType> {
            // Step 1: Insert
            val insertStep = DatabaseSteps.update<InputType>(
                sql = SafeSQL.insert("INSERT INTO ... RETURNING id"),
                parameterSetter = { stmt, inp -> stmt.setString(1, inp.name) },
                transactionConnection = txConn
            )
            val id = insertStep.process(input).getOrElse { return Result.failure(it) }

            // Step 2: Related insert
            val relatedStep = DatabaseSteps.update<Int>(
                sql = SafeSQL.insert("INSERT INTO related (parent_id) VALUES (?)"),
                parameterSetter = { stmt, parentId -> stmt.setInt(1, parentId) },
                transactionConnection = txConn
            )
            relatedStep.process(id).getOrElse { return Result.failure(it) }

            return Result.success(output)
        }
    }
}
```

---

## HTML & HTMX Patterns

### HTMX Helper Functions

```kotlin
// Form submission
form {
    id = "create-form"
    hxPost("/app/worlds/${worldId}/projects")
    hxTarget("#create-form")

    input(classes = "form-control") { name = "name" }
    button(classes = "btn btn--action") { +"Create" }
}

// Update action
button(classes = "btn btn--action") {
    hxPatch("/app/worlds/${worldId}/projects/${projectId}/stage")
    hxTarget("#project-stage")
    +"Advance Stage"
}

// Delete with confirmation
button(classes = "btn btn--danger") {
    hxDeleteWithConfirm(
        url = "/app/worlds/${worldId}/projects/${projectId}",
        title = "Delete Project",
        description = "Are you sure?",
        warning = "This cannot be undone.",
        confirmText = "DELETE"  // User types this to confirm
    )
    +"Delete"
}
```

### CSS Component Classes

**Buttons:**
```kotlin
button(classes = "btn btn--action") { +"Primary" }
button(classes = "btn btn--neutral") { +"Secondary" }
button(classes = "btn btn--danger") { +"Delete" }
button(classes = "btn btn--ghost") { +"Transparent" }
button(classes = "btn btn--sm") { +"Small" }
```

**Form Controls:**
```kotlin
input(classes = "form-control")
select(classes = "form-control")
textarea(classes = "form-control")
```

**Layout:**
```kotlin
div("container") { }
div("grid grid--cols-2") { }
div("u-flex u-flex-between u-flex-align-center") { }
```

**Notices:**
```kotlin
div("notice notice--success") { +"Success message" }
div("notice notice--danger") { +"Error message" }
div("notice notice--warning") { +"Warning message" }
div("notice notice--info") { +"Info message" }
```

**Lists:**
```kotlin
div("list") {
    div("list__item") {
        div("list__item-content") {
            h4("list__item-title") { +"Title" }
            p("list__item-meta") { +"Description" }
        }
        div("list__item-actions") {
            button(classes = "btn btn--sm") { +"Edit" }
        }
    }
}
```

**Cards:**
```kotlin
div("card card--elevated") {
    div("card__header") { h3 { +"Title" } }
    div("card__body") { p { +"Content" } }
}
```

**Spacing Utilities:**
```kotlin
div("u-margin-md u-padding-lg") { }
div("u-margin-top-sm u-margin-bottom-lg") { }
```

---

## Domain Model Summary

### Core Entities

```
User (TokenProfile)
  - id, uuid, minecraftUsername, displayName
  - roles: List<String> ("superadmin", "moderator", "idea_creator", "banned")
  - isSuperAdmin, isModerator, isIdeaCreator, isBanned (computed)

World
  - id, name, description, minecraftVersion
  - createdBy, createdAt, updatedAt

WorldMember
  - worldId, userId, role (OWNER/ADMIN/MEMBER/BANNED)

Project
  - id, worldId, name, description
  - type: BUILDING, CONTRAPTION, INFRASTRUCTURE, DECORATION, OTHER
  - stage: PLANNING, DESIGN, RESOURCE_GATHERING, BUILDING, REVIEW, COMPLETE, ARCHIVED
  - ideaId (optional, if imported from Ideas)

ItemTask (material collection)
  - id, projectId, name, description, priority
  - requirements: List<ItemRequirement> (itemId, quantityRequired, quantityDone)

ActionTask (work steps)
  - id, projectId, name, description, priority
  - requirement: ActionRequirement (description)
  - completed: Boolean

Idea (design library)
  - id, name, description, category
  - categoryData: Map<String, CategoryValue> (JSONB)
  - difficulty: EASY, MEDIUM, HARD, EXPERT
  - rating, labels, author

Invite
  - id, worldId, invitedUserId, invitedBy
  - role, status: PENDING, ACCEPTED, DECLINED, CANCELLED

Notification
  - id, userId, type, relatedEntityType, relatedEntityId
  - message, read, createdAt
```

### Role Hierarchy

| Role   | Level | Capabilities                           |
|--------|-------|----------------------------------------|
| OWNER  | 0     | Full control, cannot transfer          |
| ADMIN  | 10    | Manage settings, invite users          |
| MEMBER | 100   | Create/edit projects and tasks         |
| BANNED | 1000  | No access                              |

Lower number = higher authority. Check with: `role.isHigherThanOrEqualTo(Role.ADMIN)`

---

## File Structure Guide

```
app.mcorg/
├── Application.kt                 # Main entry point
├── config/                        # Configuration (AppConfig, ApiProvider)
├── domain/
│   ├── model/                     # Domain entities
│   │   ├── user/                  # User, TokenProfile, Role
│   │   ├── world/                 # World, WorldMember
│   │   ├── project/               # Project, ProjectStage, ProjectType
│   │   ├── task/                  # ItemTask, ActionTask, TaskPriority
│   │   ├── idea/                  # Idea, IdeaCategory, IdeaDifficulty
│   │   ├── invite/                # Invite, InviteStatus
│   │   └── notification/          # Notification, NotificationType
│   └── pipeline/                  # Step, Result, Pipeline
├── pipeline/
│   ├── SafeSQL.kt                 # Type-safe SQL builder
│   ├── DatabaseSteps.kt           # Database operations
│   ├── ValidationSteps.kt         # Input validation
│   └── failure/                   # AppFailure, ValidationFailure
└── presentation/
    ├── handler/                   # Request handlers (e.g., WorldHandler.kt)
    ├── plugins/                   # Ktor plugins (Auth, Role, Param extraction)
    ├── router/                    # Route configuration
    ├── hx.kt                      # HTMX helper functions
    ├── templated/
    │   ├── common/                # Reusable components (button, form, layout)
    │   ├── pages/                 # Full page templates
    │   └── partials/              # Partial templates
    └── utils/                     # authUtils, htmlResponseUtils, paramUtils

src/main/resources/
├── db/migration/                  # Flyway SQL migrations (V2_21_0+)
└── static/
    ├── styles/                    # CSS files
    └── icons/                     # SVG icons
```

### Where to Put New Code

| Type              | Location                                      |
|-------------------|-----------------------------------------------|
| New handler       | `presentation/handler/`                       |
| New Step          | Near related handler or `pipeline/` if shared |
| New entity        | `domain/model/{feature}/`                     |
| New route         | `presentation/router/`                        |
| New page template | `presentation/templated/pages/{feature}/`     |
| New component     | `presentation/templated/common/`              |
| DB migration      | `src/main/resources/db/migration/`            |

---

## Anti-Patterns (NEVER Do These)

### Wrong Import
```kotlin
// WRONG - will cause runtime errors
import kotlinx.html.createHTML

// CORRECT
import kotlinx.html.stream.createHTML
```

### Wrong SafeSQL Usage
```kotlin
// WRONG - constructor is private
SafeSQL("SELECT * FROM users")

// WRONG - SQL injection vulnerability
SafeSQL.select("SELECT * FROM users WHERE id = $userId")

// CORRECT
SafeSQL.select("SELECT * FROM users WHERE id = ?")
```

### Wrong Property Access
```kotlin
// WRONG - TokenProfile uses .id
user.userId

// CORRECT
user.id
```

### Authorization in Pipeline
```kotlin
// WRONG - use Ktor plugins instead
val pipeline = Pipeline.create<AppFailure, Input>()
    .pipe(ValidatePermissionsStep)  // Don't do this!

// CORRECT - install plugin on route
route("/worlds/{worldId}") {
    install(WorldAdminPlugin)
    put { call.handleUpdateWorld() }
}
```

### JSON Response
```kotlin
// WRONG - this is an HTML app
call.respond(mapOf("status" to "ok"))

// CORRECT
respondHtml(createHTML().div {
    div("notice notice--success") { +"Success" }
})
```

### Inline Styles
```kotlin
// WRONG
div { style = "color: red; padding: 10px" }

// CORRECT
div("u-text-danger u-padding-sm") { }
```

### Skipping Tests
```kotlin
// WRONG - always write tests first
// Just implement the feature...

// CORRECT - Test-First Development
// 1. Write failing tests
// 2. Implement to make tests pass
// 3. Run mvn test
```

---

## Quick Reference Tables

### AppFailure Types

| Type                              | Use Case                        |
|-----------------------------------|---------------------------------|
| `AppFailure.ValidationError`      | Input validation failures       |
| `AppFailure.DatabaseError.NotFound`| Entity not found               |
| `AppFailure.AuthError.NotAuthorized`| Permission denied             |
| `AppFailure.ApiError`             | External API failures           |
| `AppFailure.Redirect`             | Redirect response               |

### ValidationFailure Types

| Type                  | Use Case                     |
|-----------------------|------------------------------|
| `MissingParameter`    | Required field missing       |
| `InvalidFormat`       | Wrong format (email, date)   |
| `InvalidValue`        | Value out of range/invalid   |
| `CustomValidation`    | Business rule violation      |

### HTTP Response Helpers

| Function              | Status | Use Case                    |
|-----------------------|--------|-----------------------------|
| `respondHtml()`       | 200    | Success with HTML           |
| `respondBadRequest()` | 400    | Validation/client error     |
| `respondNotFound()`   | 404    | Resource not found          |
| `respondForbidden()`  | 403    | Authorization failed        |

### HTMX Swap Strategies

| Value         | Effect                              |
|---------------|-------------------------------------|
| `innerHTML`   | Replace inner content (default)     |
| `outerHTML`   | Replace entire element              |
| `beforeend`   | Append to end                       |
| `afterbegin`  | Prepend to beginning                |
| `delete`      | Remove element                      |

---

## Development Checklist

Before committing any code:

- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all tests
- [ ] Tests written for new functionality
- [ ] No hardcoded values (use CSS classes, design tokens)
- [ ] Authorization handled by plugins (not in pipelines)
- [ ] HTMX targets match response element IDs
- [ ] Uses correct imports (stream.createHTML, etc.)

---

## Getting Help

For detailed information, use these commands:

- `/docs-architecture` - Full architecture reference
- `/docs-development` - Implementation patterns and guides
- `/docs-business` - Business rules and workflows
- `/docs-css` - CSS architecture and components
- `/docs-troubleshoot` - Common errors and solutions
- `/docs-glossary` - Technical terminology

For task templates:

- `/add-endpoint` - New HTTP endpoint pattern
- `/add-migration` - Database migration pattern
- `/add-step` - Pipeline Step implementation pattern
