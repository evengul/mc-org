---
name: docs-development
description: Development patterns reference for MC-ORG. Use when writing pipeline steps, using handlePipeline, performing database operations with SafeSQL/DatabaseSteps, writing validation steps, or needing the critical imports list.
user-invocable: false
---

# Development Patterns Reference

Core implementation patterns for MC-ORG — Pipeline, Steps, Database, Validation, Imports.

---

## Pipeline Pattern

### handlePipeline (sequential)

```kotlin
suspend fun ApplicationCall.handleCreateProject() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    handlePipeline(
        onSuccess = { project ->
            respondHtml(createHTML().div {
                id = "project-result"
                // Success feedback: use the dsl alert component (alert-dialog--success),
                // see /docs-frontend — not ad-hoc classes
                div("alert-dialog alert-dialog--success") { +"Project created!" }
            })
        }
        // onFailure optional — default handler logs and responds appropriately
    ) {
        val input = ValidateInputStep.run(parameters)
        val id = CreateProjectStep(user, worldId).run(input)
        GetProjectByIdStep.run(id)
    }
}
```

### handlePipeline (parallel — independent fetches)

```kotlin
handlePipeline(
    onSuccess = { (projects, notifications) ->
        respondHtml(pageShell(pageTitle = "Dashboard", user = user) {
            dashboardContent(projects, notifications)
        })
    }
) {
    val (projects, notifications) = parallel(
        { GetUserProjectsStep.run(user.id) },
        { GetNotificationsStep.run(user.id) }
    )
    Pair(projects, notifications)
}
```

### pipelineResult (returns Result directly)

```kotlin
val result = pipelineResult<AppFailure, Output> {
    val a = StepA.run(input)
    StepB.run(a)
}
// result: Result<AppFailure, Output>
```

### Conditional branching inside pipeline

```kotlin
handlePipeline(onSuccess = { ... }) {
    val validated = ValidateInputStep.run(input)
    if (validated.needsSpecialHandling) {
        SpecialHandlingStep.run(validated)
    } else {
        StandardHandlingStep.run(validated)
    }
}
```

---

## Step Interface

```kotlin
fun interface Step<in I, out E, out S> {
    suspend fun process(input: I): Result<E, S>
}
```

Everything in `mc-pipeline` — `Step`, `Result`, `PipelineScope`, `pipelineResult`, `getOrElse` —
is in the one package `app.mcorg.pipeline` (MCO-553; the old `app.mcorg.domain.pipeline` is gone).

`Step` is the unit of work, and it is a `fun interface`: the class ceremony is optional. Pick the
shape by what the step carries, not by habit:

- **Lambda** — stateless, one expression, not something a test names:
  `val trimmed = Step<String, AppFailure, String> { Result.success(it.trim()) }`.
  `DatabaseSteps.query(...)`, `DatabaseSteps.update(...)` and `ValidationSteps.*` already return
  steps built this way; a `val` holding one is the normal shape for a one-query step.
- **Object** — stateless but named, so tests and pipelines can refer to it.
- **Class** — carries constructor state (a user, a world id, a transaction connection).

Existing steps are not to be rewritten from one shape to another; apply the choice where the next
change lands.

### Object step (no constructor params)

```kotlin
object ValidateNameStep : Step<Parameters, AppFailure.ValidationError, ValidatedInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ValidatedInput> {
        val errors = mutableListOf<ValidationFailure>()
        val name = input["name"]
        if (name.isNullOrBlank()) errors.add(ValidationFailure.MissingParameter("name"))
        return if (errors.isEmpty()) Result.success(ValidatedInput(name!!))
        else Result.failure(AppFailure.ValidationError(errors))
    }
}
```

### Class step (needs constructor params)

```kotlin
class CreateProjectStep(
    private val userId: Int,
    private val worldId: Int
) : Step<ValidatedInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ValidatedInput): Result<AppFailure.DatabaseError, Int> {
        // ...
    }
}
```

### Result operations

```kotlin
Result.success(value)
Result.failure(error)
result.getOrNull()
result.getOrElse { defaultValue }
result.getOrElse { return Result.failure(it) }  // Early return pattern
result.map { v -> transform(v) }
result.mapError { e -> transform(e) }
result.flatMap { v -> anotherStep.process(v) }
result is Result.Success / result is Result.Failure   // no isSuccess/isFailure on this Result
```

---

## SafeSQL Factory Methods

```kotlin
SafeSQL.select("SELECT * FROM projects WHERE world_id = ?")
SafeSQL.insert("INSERT INTO projects (name, world_id) VALUES (?, ?) RETURNING id")
SafeSQL.update("UPDATE projects SET name = ?, updated_at = NOW() WHERE id = ?")
SafeSQL.delete("DELETE FROM projects WHERE id = ?")
SafeSQL.with("WITH ranked AS (SELECT * FROM ...) SELECT * FROM ranked")
```

**NEVER:** `SafeSQL("...")` (constructor is private) or string interpolation in SQL.

The text is safe because it is a constant and every value is a `?` bind — `SafeSQL` itself only
checks the statement kind and that there is one statement. `SafeSqlSourceScanTest` scans
`src/main` and fails on any `SafeSQL.*(...)` argument that is not a single literal or that
interpolates. If a value genuinely cannot be bound (a `const val` column list, a `when` over fixed
`ORDER BY` clauses), add the identifier to that test's allowlist with the reason; a stale entry
fails the test too.

---

## DatabaseSteps Patterns

Every `DatabaseSteps` step runs its JDBC work on `Dispatchers.IO`, so `process()` is safe to call
from a Ktor handler. Never call JDBC yourself from a step: the handler runs on Netty's call
thread, of which production has exactly one (MCO-551).

### query (SELECT — multiple rows)

```kotlin
val step = DatabaseSteps.query<InputType, List<Project>>(
    sql = SafeSQL.select("SELECT * FROM projects WHERE world_id = ?"),
    parameterSetter = { stmt, input -> stmt.setInt(1, input.worldId) },
    resultMapper = { rs ->
        val results = mutableListOf<Project>()
        while (rs.next()) results.add(mapToProject(rs))
        results
    }
)
```

### query (SELECT — single row)

```kotlin
val step = DatabaseSteps.query<Int, Project?>(
    sql = SafeSQL.select("SELECT * FROM projects WHERE id = ?"),
    parameterSetter = { stmt, id -> stmt.setInt(1, id) },
    resultMapper = { rs -> if (rs.next()) mapToProject(rs) else null }
)
```

### update (INSERT/UPDATE/DELETE)

```kotlin
val step = DatabaseSteps.update<InputType>(
    sql = SafeSQL.insert("INSERT INTO projects (name, world_id) VALUES (?, ?) RETURNING id"),
    parameterSetter = { stmt, input ->
        stmt.setString(1, input.name)
        stmt.setInt(2, input.worldId)
    }
)
// Returns the generated id (from RETURNING clause) or affected row count
```

### transaction (multi-step atomically)

```kotlin
val step = DatabaseSteps.transaction<InputType, OutputType> { txConn ->
    object : Step<InputType, AppFailure.DatabaseError, OutputType> {
        override suspend fun process(input: InputType): Result<AppFailure.DatabaseError, OutputType> {
            val insertStep = DatabaseSteps.update<InputType>(
                sql = SafeSQL.insert("INSERT INTO projects (name) VALUES (?) RETURNING id"),
                parameterSetter = { stmt, inp -> stmt.setString(1, inp.name) },
                transactionConnection = txConn
            )
            val id = insertStep.process(input).getOrElse { return Result.failure(it) }

            val relatedStep = DatabaseSteps.update<Int>(
                sql = SafeSQL.insert("INSERT INTO project_members (project_id) VALUES (?)"),
                parameterSetter = { stmt, parentId -> stmt.setInt(1, parentId) },
                transactionConnection = txConn
            )
            relatedStep.process(id).getOrElse { return Result.failure(it) }

            return Result.success(output)
        }
    }
}
```

### ResultSet mapping

A row shape read by more than one query gets a named `ResultSet.toX()` extension in the feature's
`extractors/` file (`pipeline/<feature>/extractors/`). An inline `resultMapper` lambda is fine for
a shape read in exactly one place.

```kotlin
fun ResultSet.mapToProject(): Project = Project(
    id = getInt("id"),
    worldId = getInt("world_id"),
    name = getString("name"),
    type = ProjectType.valueOf(getString("type")),
    stage = ProjectStage.valueOf(getString("stage")),
    ideaId = getInt("idea_id").takeIf { !wasNull() },
    createdBy = getInt("created_by"),
    createdAt = getTimestamp("created_at").toZonedDateTime(),
    updatedAt = getTimestamp("updated_at").toZonedDateTime()
)
```

---

## Validation

```kotlin
object ValidateCreateProjectInputStep : Step<Parameters, AppFailure.ValidationError, CreateProjectInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateProjectInput> {
        val errors = mutableListOf<ValidationFailure>()

        val name = ValidationSteps.required("name", { AppFailure.ValidationError(listOf(it)) }).process(input)
        if (name is Result.Failure) errors.addAll(name.error.errors)

        val type = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "type", "Invalid project type",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { !it.isNullOrBlank() && runCatching { ProjectType.valueOf(it.uppercase()) }.isSuccess }
        ).process(input["type"])
        if (type is Result.Failure) errors.addAll(type.error.errors)

        return if (errors.isEmpty()) {
            Result.success(CreateProjectInput(name.getOrNull()!!, ProjectType.valueOf(type.getOrNull()!!.uppercase())))
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}
```

---

## Critical Imports

```kotlin
import kotlinx.html.stream.createHTML         // NOT kotlinx.html.createHTML
import kotlinx.html.*
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondNotFound
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.getOrElse
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.pipeline.pipelineResult
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.domain.model.user.TokenProfile
```

---

## AppFailure Types

| Type | Use Case |
|------|----------|
| `AppFailure.ValidationError(errors)` | Input validation failures |
| `AppFailure.DatabaseError.NotFound` | Entity not found in DB |
| `AppFailure.DatabaseError.NoIdReturned` | INSERT didn't return ID |
| `AppFailure.DatabaseError.IntegrityConstraintError` | DB constraint violation |
| `AppFailure.AuthError.NotAuthorized` | Permission denied |
| `AppFailure.ApiError.NetworkError` | External API connection failed |
| `AppFailure.Redirect(path)` | Redirect response |
| `AppFailure.IllegalConfigurationError(reason)` | Bad config |

## ValidationFailure Types

| Type | Use Case |
|------|----------|
| `ValidationFailure.MissingParameter("name")` | Required field missing |
| `ValidationFailure.InvalidFormat("email", "msg")` | Wrong format |
| `ValidationFailure.InvalidValue("type", "msg")` | Value not in allowed set |
| `ValidationFailure.CustomValidation("field", "msg")` | Business rule violation |

## HTTP Response Helpers

| Function | Status | Use Case |
|----------|--------|----------|
| `respondHtml(html)` | 200 | Success with HTML |
| `respondBadRequest(msg)` | 400 | Validation/client error |
| `respondNotFound(msg)` | 404 | Resource not found |
| `clientRedirect(path)` | 200 | HTMX redirect (HX-Redirect) |

## HTMX Swap Strategies

Owned by **docs-htmx** — load it for the swap-strategy table, hx* helper signatures,
and OOB/fragment patterns.

## Outbound HTTP (external calls)

Every outbound call runs on one CIO engine owned by `OutboundHttp` (`config/OutboundHttp.kt`),
which is also where the timeouts, the retry policy and the shutdown live. It exposes two clients,
and there are exactly three paths out of the app (MCO-552):

| Path | Client | Policy |
|------|--------|--------|
| JSON services — Microsoft, Xbox, XSTS, Minecraft services, Mojang piston-meta | `OutboundHttp.api` via `ApiProvider` | 30s request / 10s connect / 30s socket; bounded retry (MCO-354): GETs and `retrySafe = true` POSTs on 5xx/429 or a refused connection, never on a timeout; rate limiter keyed by `ApiConfig.baseUrl` |
| Mojang server.jar download (`GetServerFileStep`) | `OutboundHttp.api` **directly** — streams to a temp file with a SHA-1 digest, which the `Step` shape cannot express | Same engine and retry; timeouts overridden per request (`DownloadTimeouts`) |
| Webhook delivery (`WebhookDeliveryPoller`) | `OutboundHttp.webhook` | 5s everything, **no** transport retry (the outbox is the retry), no rate limiter, no content negotiation — see `OutboundHttp`'s class comment for why that is a second client and not four per-request overrides |

`ApiProvider` (`config/ApiProvider.kt`) is the `Step`-shaped face of the first row: `get`/`post`
return `Step<I, ApiError, S>` with the response deserialized and the failure taxonomy every
sign-in and ingestion step branches on. Per-service config objects live in `config/ApiConfig.kt`
(`MicrosoftLoginApiConfig`, `XboxAuthApiConfig`, `XstsAuthorizationApiConfig`, `MinecraftApiConfig`,
`MojangLauncherMetaApiConfig`).

Rules: a new call to a JSON service goes through `ApiProvider`. A new call that is not JSON, or
not a single value in memory, calls `OutboundHttp.api` directly the way `GetServerFileStep` does.
**Never construct an `HttpClient`** — the count in `src/main` is the two in `OutboundHttp`, and
each has a stated owner and shutdown path. A POST is only retried if its call site passes
`retrySafe = true`, and only when replaying the exact body is harmless (the Microsoft token
exchange spends a single-use code and must not).
