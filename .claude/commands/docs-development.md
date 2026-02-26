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
                div("notice notice--success") { +"Project created!" }
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
        respondHtml(createPage(user, "Dashboard") {
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
interface Step<I, E, S> {
    suspend fun process(input: I): Result<E, S>
}
```

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
result.isSuccess / result.isFailure
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

---

## DatabaseSteps Patterns

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
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.handler.pipelineResult
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

| Value | Effect |
|-------|--------|
| `innerHTML` | Replace inner content (default) |
| `outerHTML` | Replace entire element |
| `beforeend` | Append to end |
| `afterbegin` | Prepend to beginning |
| `delete` | Remove element |
