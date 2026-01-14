# AI Agent Quickstart Guide for MC-ORG

**Read This First** - 10 minute orientation guide

---

## ✈️ Pre-Implementation Checklist

Before starting ANY task:

- [ ] Read this guide (if first time)
- [ ] `mvn clean compile` passes
- [ ] Located similar existing code
- [ ] Identified required imports
- [ ] Know which Step type needed
- [ ] Tests planned (what to test)

---

## 🎯 What is MC-ORG?

MC-ORG is a **Minecraft world collaboration platform** for managing building projects, tasks, and team coordination.
It's a web application that helps Minecraft builders organize large-scale projects from idea to completion.

**Technology**: Ktor (Kotlin) + PostgreSQL + HTMX + Server-side HTML

---

## 🚀 Quick Start in 3 Steps

### 1. Understand the Domain (2 minutes)

```
World (Minecraft server/world)
  └─ Projects (individual builds: castle, farm, city)
      ├─ Tasks (granular work items)
      │   ├─ ItemTask (collect 64 stacks of stone)
      │   └─ ActionTask (place foundation, wire redstone)
      ├─ Resources (materials needed/produced)
      ├─ Dependencies (this project needs that project first)
      └─ Location (coordinates in world)
      
Ideas (design library)
  └─ Import to create Projects
  
Invitations → World Access → Roles (Owner/Admin/Member)
Notifications → User alerts
```

### 2. Learn the Critical Patterns (3 minutes)

**Pipelines can be sequential or parallel:**

- **Sequential**: Steps execute one after another (most common)
- **Parallel**: Independent steps execute concurrently (for dashboards, multiple data fetches)

**Sequential pipeline pattern:**

```kotlin
suspend fun ApplicationCall.handleFeatureAction() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    // Create pipeline
    executePipeline(
        onSucess = { result ->
            respondHtml(createHTML().div {
                // Return HTML fragment for HTMX
            })
        },
    ) {
        value(parameters)
            .step(ValidateInputStep)
            .step(ExecuteBusinessLogicStep)
            .step(GetUpdatedDataStep)
    }
}
```

**Critical imports:**

```kotlin
import kotlinx.html.stream.createHTML  // NOT kotlinx.html.createHTML
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest
```

### 3. Follow the Safety Protocol (5 minutes)

**Before writing ANY code:**

- [ ] Run `mvn clean compile` to ensure clean state
- [ ] Find similar existing implementation to follow
- [ ] Write failing tests first
- [ ] Follow established patterns

**Before marking complete:**

- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all tests
- [ ] Manual verification completed
- [ ] Error cases tested

---

## 🗺️ Documentation Navigation

**"I need to..."** → **"Read this..."**

| Task                     | Primary Document                                           | Section                          |
|--------------------------|------------------------------------------------------------|----------------------------------|
| Understand the system    | [ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)     | Domain Model + Database          |
| Understand Ideas system  | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md) | Ideas Management                 |
| Understand task workflow | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md) | Task Management                  |
| Create a new feature     | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)               | Implementation Patterns          |
| Understand permissions   | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md) | Permission Model                 |
| Write a database query   | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)               | DatabaseSteps + SafeSQL          |
| Add HTMX interaction     | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)               | HTMX Integration                 |
| Style a component        | [CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)                 | Component Library                |
| Check feature status     | [PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)   | Current Status                   |
| Send notifications       | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md) | Notification System              |
| Work with Minecraft mods | [ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)     | External Integrations (Modrinth) |
| Handle errors            | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)               | Error Handling                   |

---

## 📚 Core Concepts Glossary

### Domain Terms

**World**: A Minecraft server or save file. Private by default, access via invitation only.

**Project**: A specific build within a world (castle, farm, redstone contraption). Has stages: Planning → Design →
Resource Gathering → Building → Review → Complete.

**Task**: Granular work items within projects. Two types:

- **ItemTask**: Collect/gather items (e.g., "64 stacks of stone")
- **ActionTask**: Complete actions (e.g., "Place foundation layer")

**Idea**: Design in the library system. Can be imported to create projects. Has categories with custom schemas.

**Dependency**: Relationship where one project requires another to be completed first. System prevents circular
dependencies.

**Invitation**: How users gain access to worlds. Sent by Admin+, includes role assignment.

**Role Hierarchy**: Owner (0) > Admin (10) > Member (100) > Banned (1000). Lower number = higher authority.

### Technical Terms

**Pipeline**: Request processing flow using Step interface with Result<E, S> pattern.

**Step**: Single processing unit in pipeline. Takes input, returns Result<Failure, Success>.

**AppFailure**: Sealed interface for all error types (AuthError, DatabaseError, ValidationError, etc.).

**HTMX**: JavaScript library for dynamic interactions. PUT/PATCH/POST/DELETE return HTML fragments, not full pages.

**SafeSQL**: Type-safe SQL query builder. Use factory methods: `SafeSQL.select()`, `SafeSQL.insert()`, etc.

**createPage()**: Base template function for full page HTML. Components subdivide the page.

---

## ⚡ Quick Reference Cheat Sheet

### Common Imports

```kotlin
// HTML Generation
import kotlinx.html.stream.createHTML
import kotlinx.html.*

// Response Helpers
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest

// Pipeline & Steps
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.executePipeline

// Database
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.DatabaseSteps

// Validation
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure

// Error Handling
import app.mcorg.pipeline.failure.AppFailure

// Auth
import app.mcorg.presentation.utils.getUser
import app.mcorg.domain.model.user.TokenProfile
```

### Pipeline Template

```kotlin
suspend fun ApplicationCall.handleYourFeature() {
    val params = receiveParameters()
    val user = getUser()
    val worldId = getWorldId()

    executePipeline(
        onSuccess = { result ->
            respondHtml(createHTML().div {
                div("notice notice--success") { +"Success!" }
                // Your content
            })
        },
        onFailure = { failure ->
            when (failure) {
                is AppFailure.ValidationError ->
                    respondBadRequest(failure.errors.joinToString { it.toString() })
                is AppFailure.DatabaseError.NotFound ->
                    respondBadRequest("Resource not found")
                else -> respondBadRequest("Operation failed")
            }
        }
    ) {
        step(Step.value(params))
            .step(YourValidationStep)
            .step(YourBusinessLogicStep)
    }
}
```

### Step Template

```kotlin
object YourValidationStep : Step<Input, YourFailure, Output> {
    override suspend fun process(input: Input): Result<YourFailure, Output> {
        // Validation logic
        return if (valid) {
            Result.success(Output(...))
        } else {
            Result.failure(YourFailure.Invalid)
        }
    }
}
```

### SafeSQL Pattern

```kotlin
// Query
DatabaseSteps.query<Unit, AppFailure.DatabaseError, List<Item>>(
    step = object : Step<Unit, AppFailure.DatabaseError, List<Item>> {
        override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<Item>> {
            val sql = SafeSQL.select(
                """
                SELECT id, name FROM items
                WHERE world_id = ?
                ORDER BY created_at DESC
                """,
                listOf(worldId)
            )
            return Result.success(executeQuery(sql) { rs ->
                Item(rs.getInt("id"), rs.getString("name"))
            })
        }
    }
).process(Unit)

// Insert
val sql = SafeSQL.insert(
    "INSERT INTO items (name, world_id) VALUES (?, ?)",
    listOf(name, worldId)
)
```

### HTMX Helper Functions

```kotlin
// In your HTML template
button {
    hxPost("/api/endpoint")
    hxTarget("#result-container")
    +"Submit"
}

button {
    hxDeleteWithConfirm(
        url = "/api/item/\${item.id}",
        title = "Delete Item",
        description = "Are you sure?",
        warning = "This action cannot be undone"
    )
    +"Delete"
}
```

### CSS Component Classes

```kotlin
// Buttons
button(classes = "btn btn--action") { +"Save" }
button(classes = "btn btn--danger") { +"Delete" }

// Forms
input(classes = "form-control") {
    name = "fieldName"
    type = InputType.text
}

// Notices
div("notice notice--success") { +"Success!" }
div("notice notice--danger") { +"Error!" }

// Layout
div("container") {
    div("grid grid--cols-2") {
        div { +"Column 1" }
        div { +"Column 2" }
    }
}
```

---

## 🚨 Critical "Never Do This" List

### ❌ Wrong Patterns

```kotlin
// ❌ Wrong import
import kotlinx.html.createHTML  // Missing .stream

// ❌ Wrong SafeSQL usage
SafeSQL("SELECT * FROM users")  // Constructor is private

// ❌ Wrong property access
user.userId  // TokenProfile uses .id, not .userId

// ❌ Wrong response type
call.respondText(..., ContentType.Text.Html)  // Use respondHtml()

// ❌ Wrong DatabaseSteps.query signature
DatabaseSteps.query(step = object : Step<...> { ... })  // Takes SafeSQL, parameterSetter, mapper

// ❌ Check authorization in pipeline
    .step(ValidatePermissionsStep)  // Authorization is handled by plugins, not pipelines

// ❌ Return JSON
call.respond(mapOf("status" to "ok"))  // This is HTML app, not JSON API

// ❌ Inline styles
div {
    style = "color: red; padding: 10px"  // Use CSS classes
}

// ❌ Skip tests
// Implement without writing tests first
```

### ✅ Correct Patterns

```kotlin
// ✅ Correct import
import kotlinx.html.stream.createHTML

// ✅ Correct SafeSQL
SafeSQL.select("SELECT * FROM users")  // Use factory methods

// ✅ Correct property
user.id  // TokenProfile.id

// ✅ Correct response
respondHtml(createHTML().div { ... })

// ✅ Correct DatabaseSteps.query
DatabaseSteps.query<InputType, OutputType>(
    sql = SafeSQL.select("..."),
    parameterSetter = { statement, input -> ... },
    resultMapper = { rs -> ... }
)

// ✅ Authorization in plugins
// Check permissions in Ktor plugins before handler, not in pipeline

// ✅ Return HTML
respondHtml(createHTML().div { +"Success" })

// ✅ CSS classes
div("notice notice--danger") { ... }

// ✅ Test first
// Write failing test → Implement → Test passes
```

---

## 🔍 Common Tasks & Solutions

### Task: Add a new endpoint

1. **Find similar endpoint** - Look in `presentation/handler/` for patterns
2. **Check plugin requirements** - Ensure appropriate plugins installed on route (auth, role checks)
3. **Create handler function** - Follow pipeline pattern
4. **Create validation steps** - Validate input and business rules (NOT authorization)
5. **Create business logic steps** - Execute the operation
6. **Add route** - Register in appropriate router with plugins
7. **Write tests** - Unit tests for steps, integration tests for handler
8. **Verify** - `mvn clean compile && mvn test`

### Task: Query the database

```kotlin
// Use DatabaseSteps.query with SafeSQL
val queryStep = DatabaseSteps.query<InputType, OutputType>(
    sql = SafeSQL.select("SELECT * FROM table WHERE id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.id)
    },
    resultMapper = { rs ->
        val results = mutableListOf<YourModel>()
        while (rs.next()) {
            results.add(
                YourModel(
                    id = rs.getInt("id"),
                    name = rs.getString("name")
                )
            )
        }
        results
    }
)

// Use in pipeline
val result = queryStep.process(input)
```

### Task: Return HTMX fragment

```kotlin
pipeline.fold(
    input = params,
    onSuccess = { result ->
        // Return HTML fragment that replaces target element
        respondHtml(createHTML().div {
            id = "target-element-id"  // Must match hxTarget
            // Updated content
            div("notice notice--success") { +"Operation successful" }
            yourUpdatedContent(result)
        })
    },
    onFailure = { failure ->
        respondBadRequest("Error message")
    }
)
```

**Note**: Authorization/permissions are handled by Ktor plugins (AdminPlugin, WorldAdminPlugin, etc.) before the handler
executes, not in pipelines. See RolePlugins.kt.

---

## 🎓 Learning Path

### Day 1: Orientation

1. Read this quickstart guide (10 min)
2. Skim [ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md) domain model (15 min)
3. Study 2-3 existing handlers (30 min)
4. Try modifying an existing feature (1-2 hours)

### Day 2: Deep Dive

1. Read [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) pipeline section (30 min)
2. Read [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md) (20 min)
3. Implement a simple endpoint from scratch (2-3 hours)
4. Write tests for your implementation (1 hour)

### Day 3: Mastery

1. Review [CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md) (20 min)
2. Study error handling patterns (30 min)
3. Implement a complex feature with dependencies (3-4 hours)
4. Review with team (1 hour)

---

## 🆘 When Something Goes Wrong

### Compilation Errors

1. Check imports - use `kotlinx.html.stream.createHTML`
2. Check generic types - DatabaseSteps needs `<Input, Failure, Output>`
3. Check property names - `user.id` not `user.userId`
4. Look at similar working code
5. Run `mvn clean compile` to see all errors

### Tests Failing

1. Verify test data setup
2. Check database state (migrations applied?)
3. Compare with similar working tests
4. Run single test: `mvn test -Dtest=YourTestClass`

### Runtime Errors

1. Check logs for stack traces
2. Verify database connection
3. Check JWT token validity
4. Verify permissions/roles
5. Look at AppFailure type returned

### HTMX Not Updating

1. Check `hxTarget` matches element ID
2. Verify endpoint returns HTML fragment (not full page)
3. Check browser network tab for response
4. Verify no JavaScript errors in console

---

## 🐛 When Things Go Wrong

### Debug Decision Tree

```
Compilation Error?
├─ Import missing? → See "Required Imports" section
├─ Type mismatch? → Check Step<I,E,S> types align
├─ Cannot find symbol 'createHTML'? → Use kotlinx.html.stream.createHTML
└─ Cannot resolve SafeSQL method? → Use factory methods (.select(), .insert(), etc.)

Runtime Error?
├─ NullPointerException? → Check Result.getOrNull() usage
├─ SQL injection detected? → Using SafeSQL constructor (use factory methods)
├─ Database error? → Verify SafeSQL factory method used correctly
├─ Auth error (403)? → Check plugin installation on route
└─ HTMX not updating? → Verify hxTarget ID matches response element ID

Tests Failing?
├─ TestDataFactory issue? → Check factory methods exist for your entity
├─ Database state? → Run migrations? Clean test DB?
├─ Mock not working? → Using correct test patterns from existing tests?
└─ Assertion failing? → Verify expected vs actual carefully
```

### Code Smell Detector

| If you see this...               | 🚩 Red flag because...         | ✅ Do this instead...                    |
|----------------------------------|--------------------------------|-----------------------------------------|
| `SafeSQL("SELECT...")`           | Using constructor directly     | `SafeSQL.select("...")`                 |
| `import kotlinx.html.createHTML` | Wrong import                   | `import kotlinx.html.stream.createHTML` |
| Authorization check in Step      | Wrong layer (business logic)   | Use Ktor plugin on route                |
| `style="..."` in HTML            | Inline styles prohibited       | Use CSS component classes               |
| JSON response                    | Not an HTML app                | Return HTML with `respondHtml()`        |
| No `hxTarget` attribute          | HTMX won't know what to update | Add `hxTarget = "#element-id"`          |
| `user.userId`                    | Wrong property name            | Use `user.id` (TokenProfile)            |
| `.pipe(ValidatePermissionsStep)` | Auth in wrong place            | Remove, use plugin instead              |
| Direct SQL string concat         | SQL injection risk             | Use SafeSQL with params                 |
| No tests written                 | Violates TDD protocol          | Write tests first                       |

---

## ✅ Ready to Start?

**Your next steps:**

1. **Choose a task** from [PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)
2. **Read relevant docs** from navigation table above
3. **Find similar code** in the codebase
4. **Write tests first**
5. **Implement following patterns**
6. **Verify compilation and tests pass**

**Remember the mantra:**
> Compile → Test → Pattern → Implement → Verify

---

## 📞 Quick Links

- **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)** - Complete system architecture
- **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Implementation patterns
- **[BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)** - Domain rules
- **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)** - Styling guide
- **[PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)** - Feature status

Good luck! 🚀

