# MC-ORG Development Guide

**Complete implementation patterns, best practices, and step-by-step guides**

---

## 📇 Pattern Quick Reference

Quick links to common patterns and solutions:

| Pattern                | Use Case                   | Jump To                                                                       |
|------------------------|----------------------------|-------------------------------------------------------------------------------|
| Simple transformation  | Converting data types      | [Simple Transformation Step](#simple-transformation-step)                     |
| Multi-field validation | Form validation            | [Validation Step with Multiple Fields](#validation-step-with-multiple-fields) |
| Database transaction   | Multi-table updates        | [Transaction Pattern](#transaction-pattern)                                   |
| Business rule check    | Domain constraints         | [Business Rule Validation Step](#business-rule-validation-step)               |
| External API call      | Third-party integration    | [Using ApiProvider in Steps](#using-apiprovider-in-steps)                     |
| HTMX form submission   | Interactive forms          | [Form Submission](#form-submission)                                           |
| HTMX inline editing    | In-place updates           | [Inline Editing Pattern](#inline-editing-pattern)                             |
| Complex query (joins)  | Multi-table queries        | [Complex Query Patterns](#complex-query-patterns)                             |
| Pagination             | Large result sets          | [Pagination Pattern](#pagination-pattern)                                     |
| Sequential pipeline    | Step-by-step processing    | [Sequential Pipeline Pattern](#sequential-pipeline-pattern)                   |
| Parallel pipeline      | Independent concurrent ops | [Parallel Pipeline Pattern](#parallel-pipeline-pattern)                       |
| Error handling         | Custom error responses     | [Error Handling](#error-handling)                                             |
| File upload            | Handle user uploads        | [File Upload Pattern](#file-upload-pattern)                                   |

---

## 📋 Table of Contents

1. [Pipeline Architecture](#pipeline-architecture)
2. [Step Implementation Patterns](#step-implementation-patterns)
3. [Error Handling](#error-handling)
4. [HTMX Integration](#htmx-integration)
5. [HTML Template Patterns](#html-template-patterns)
6. [Database Operations](#database-operations)
7. [Validation Patterns](#validation-patterns)
8. [Testing Requirements](#testing-requirements)
9. [Build and Deployment](#build-and-deployment)

---

## 🔄 Pipeline Architecture

### Overview

**Every endpoint uses the pipeline pattern** - a composable, type-safe way to process requests.

**Key Concepts:**

- **Step**: Single processing unit with `process(input): Result<Failure, Success>`
- **Pipeline**: Chain of steps executed sequentially or in parallel
- **Result**: Success or Failure wrapper (no exceptions for business logic)
- **Short-circuiting**: First failure stops pipeline execution

### Pipeline Execution Types

There are two ways to execute pipelines:

1. **Sequential Execution**: Steps execute one after another
2. **Parallel Execution**: Independent steps execute concurrently, with merge points for combining results

### Sequential Pipeline Pattern

Sequential pipelines execute steps one after another. The pipeline has built-in error handling via the `.fold()` method,
which logs errors automatically. Explicit `onFailure` handlers are rarely needed.

**Basic Sequential Pattern:**

```kotlin
suspend fun ApplicationCall.handleFeatureAction() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    // Create pipeline
    val pipeline = Pipeline.create<AppFailure, Parameters>()
        .pipe(ValidateInputStep)
        .pipe(ValidatePermissionsStep(user, worldId))
        .pipe(ExecuteBusinessLogicStep)
        .pipe(GetUpdatedDataStep)

    // Execute with default error handling
    pipeline.fold(
        input = parameters,
        onSuccess = { result ->
            respondHtml(createHTML().div {
                // Return HTML fragment for HTMX or full page
                featureSuccessContent(result)
            })
        },
        onFailure = {
            // An explicit error handler is rarely needed, as the default handler does it well.
        }
    )
}
```

**Note**: The `.fold()` method automatically logs errors, so explicit error logging in `onFailure` is usually
unnecessary.

### Parallel Pipeline Pattern

Parallel pipelines allow independent operations to execute concurrently, then merge results. Use when you have multiple
independent data fetches or operations that don't depend on each other.

**Basic Parallel Pattern:**

```kotlin
suspend fun ApplicationCall.handleDashboard() {
    val user = this.getUser()

    val pipeline = parallelPipeline<AppFailure> {
        // Define independent operations
        val projects = singleStep(
            id = "fetch-projects",
            input = user.id,
            step = GetUserProjectsStep
        )

        val notifications = singleStep(
            id = "fetch-notifications",
            input = user.id,
            step = GetUserNotificationsStep
        )

        val invites = singleStep(
            id = "fetch-invites",
            input = user.id,
            step = GetUserInvitesStep
        )

        // Merge results
        merge(
            id = "combine-dashboard",
            depA = projects,
            depB = notifications,
            depC = invites
        ) { projectList, notificationList, inviteList ->
            Result.success(
                DashboardData(
                    projects = projectList,
                    notifications = notificationList,
                    invites = inviteList
                )
            )
        }
    }

    // Execute with default error handling
    pipeline.fold(
        input = Unit,
        onSuccess = { dashboardData ->
            respondHtml(createHTML().div {
                dashboardContent(dashboardData)
            })
        },
        onFailure = { failure ->
            respondBadRequest("Failed to load dashboard")
        }
    )
}
```

**Parallel Pipeline Features:**

- `singleStep()`: Execute a single step with given input
- `pipeline()`: Execute an entire pipeline as a node
- `merge()`: Combine results from 2-3 dependencies
- `pipe()`: Transform one dependency's output through a pipeline

**Example with Sequential Dependencies in Parallel Graph:**

```kotlin
val pipeline = parallelPipeline<AppFailure> {
    // Fetch world independently
    val world = singleStep(
        id = "fetch-world",
        input = worldId,
        step = GetWorldByIdStep
    )

    // Fetch projects independently
    val projects = singleStep(
        id = "fetch-projects",
        input = worldId,
        step = GetProjectsByWorldIdStep
    )

    // Process projects (depends on projects being fetched)
    val processedProjects = pipe(
        id = "process-projects",
        dependency = projects,
        pipeline = Pipeline.create<AppFailure, List<Project>>()
            .pipe(FilterArchivedProjectsStep)
            .pipe(SortProjectsByStageStep)
    )

    // Merge world and processed projects
    merge(
        id = "combine-world-view",
        depA = world,
        depB = processedProjects
    ) { worldData, projectList ->
        Result.success(WorldView(worldData, projectList))
    }
}
```

### When to Use Each Type

**Sequential Pipelines:**

- Default choice for most operations
- When steps depend on previous results
- When order matters
- Simple, linear workflows

**Parallel Pipelines:**

- Multiple independent data fetches
- Dashboard pages loading multiple resources
- Operations that can execute concurrently
- Performance optimization for independent operations

### Pipeline Composition Patterns

**1. Validation Steps**

Validation steps check input format and business rules. **Note**: Authorization and access control are **NOT** handled
in pipeline steps - they are handled by Ktor plugins before the pipeline executes (see RolePlugins.kt).

```kotlin
object ValidateFeatureInputStep : Step<Parameters, AppFailure.ValidationError, ValidatedInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ValidatedInput> {
        val name = ValidationSteps.required("name", { AppFailure.ValidationError(listOf(it)) })
            .process(input)

        return if (name is Result.Failure) {
            Result.failure(name.error)
        } else {
            Result.success(ValidatedInput(name.getOrNull()!!))
        }
    }
}
```

**2. Business Logic Steps**

```kotlin
object CreateProjectStep : Step<CreateProjectInput, AppFailure, Project> {
    override suspend fun process(input: CreateProjectInput): Result<AppFailure, Project> {
        return DatabaseSteps.transaction(
            step = object : Step<CreateProjectInput, AppFailure.DatabaseError, Project> {
                override suspend fun process(input: CreateProjectInput): Result<AppFailure.DatabaseError, Project> {
                    val sql = SafeSQL.insert(
                        """
                        INSERT INTO projects (world_id, name, description, type, stage, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                        RETURNING id, world_id, name, description, type, stage, created_by, created_at, updated_at
                        """,
                        listOf(
                            input.worldId,
                            input.name,
                            input.description,
                            input.type.name,
                            ProjectStage.PLANNING.name,
                            input.userId
                        )
                    )

                    val project = executeQuerySingle(sql) { rs ->
                        Project(
                            id = rs.getInt("id"),
                            worldId = rs.getInt("world_id"),
                            name = rs.getString("name"),
                            description = rs.getString("description"),
                            type = ProjectType.valueOf(rs.getString("type")),
                            stage = ProjectStage.valueOf(rs.getString("stage")),
                            ideaId = null,
                            createdBy = rs.getInt("created_by"),
                            createdAt = rs.getTimestamp("created_at").toZonedDateTime(),
                            updatedAt = rs.getTimestamp("updated_at").toZonedDateTime()
                        )
                    }

                    return if (project != null) {
                        Result.success(project)
                    } else {
                        Result.failure(AppFailure.DatabaseError.NoIdReturned)
                    }
                }
            }
        ).process(input)
    }
}
```

**4. Data Retrieval Steps**

```kotlin
class GetProjectByIdStep(private val projectId: Int) : Step<Unit, AppFailure.DatabaseError, Project> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Project> {
        return DatabaseSteps.query<Unit, AppFailure.DatabaseError, Project?>(
            step = object : Step<Unit, AppFailure.DatabaseError, Project?> {
                override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Project?> {
                    val sql = SafeSQL.select(
                        "SELECT * FROM projects WHERE id = ?",
                        listOf(projectId)
                    )

                    val project = executeQuerySingleOrNull(sql) { rs ->
                        mapToProject(rs)
                    }

                    return Result.success(project)
                }
            }
        ).process(Unit).flatMap { project ->
            if (project != null) {
                Result.success(project)
            } else {
                Result.failure(AppFailure.DatabaseError.NotFound)
            }
        }
    }
}
```

### Pipeline Composition Patterns

**Sequential Steps:**

```kotlin
executePipeline(onSuccess, onFailure) {
    step(Step.value(input))
        .step(Step1)
        .step(Step2)
        .step(Step3)
}
```

**Conditional Steps:**

```kotlin
executePipeline(onSuccess, onFailure) {
    step(Step.value(input))
        .step(ValidateInputStep)
        .step { validated ->
            if (validated.needsSpecialHandling) {
                SpecialHandlingStep.process(validated)
            } else {
                StandardHandlingStep.process(validated)
            }
        }
}
```

**Parallel Steps (when independent):**

```kotlin
// Note: Use with caution, ensure no shared mutable state
val result1 = Step1.process(input)
val result2 = Step2.process(input)

if (result1 is Result.Success && result2 is Result.Success) {
    Result.success(Combined(result1.value, result2.value))
} else {
    // Handle failures
}
```

### Required Imports

```kotlin
// Pipeline core
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.executePipeline

// Error types
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure

// Database
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.DatabaseSteps

// Validation
import app.mcorg.pipeline.ValidationSteps

// HTTP
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

// HTML
import kotlinx.html.stream.createHTML  // NOT kotlinx.html.createHTML
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest

// Auth
import app.mcorg.presentation.utils.getUser
import app.mcorg.domain.model.user.TokenProfile
```

---

## 🔧 Step Implementation Patterns

### Step Interface

```kotlin
interface Step<I, E, S> {
    suspend fun process(input: I): Result<E, S>
}
```

**Type Parameters:**

- `I`: Input type
- `E`: Error/Failure type (must extend AppFailure)
- `S`: Success type

### Creating a Step

**Template:**

```kotlin
object YourStepName : Step<InputType, FailureType, OutputType> {
    override suspend fun process(input: InputType): Result<FailureType, OutputType> {
        // 1. Perform operation
        // 2. Return Result.success() or Result.failure()
    }
}
```

**With Parameters:**

```kotlin
class YourParameterizedStep(
    private val param1: String,
    private val param2: Int
) : Step<InputType, FailureType, OutputType> {
    override suspend fun process(input: InputType): Result<FailureType, OutputType> {
        // Use param1 and param2
        return Result.success(output)
    }
}
```

### Result Type Operations

**Creating Results:**

```kotlin
Result.success(value)           // Success case
Result.failure(error)           // Failure case
Step.value(value)              // Wrap value as successful step
```

**Checking Results:**

```kotlin
when (result) {
    is Result.Success -> result.value
    is Result.Failure -> result.error
}

result.isSuccess  // Boolean
result.isFailure  // Boolean
```

**Transforming Results:**

```kotlin
result.map { value -> transformedValue }
result.mapError { error -> transformedError }
result.flatMap { value -> Step.process(value) }
```

**Extracting Values:**

```kotlin
result.getOrNull()              // Returns value or null
result.getOrElse { defaultValue }
result.getOrThrow()             // Throws if failure
```

### Common Step Patterns

**1. Simple Transformation Step**

```kotlin
object TransformStep : Step<Input, Never, Output> {
    override suspend fun process(input: Input): Result<Never, Output> {
        val output = transform(input)
        return Result.success(output)
    }
}
```

**2. Validation Step with Multiple Fields**

```kotlin
object ValidateMultipleFieldsStep : Step<Parameters, AppFailure.ValidationError, ValidatedData> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ValidatedData> {
        val errors = mutableListOf<ValidationFailure>()

        val name = ValidationSteps.required("name", { AppFailure.ValidationError(listOf(it)) })
            .process(input)
        if (name is Result.Failure) errors.addAll(name.error.errors)

        val age = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "age",
            "Must be a positive integer",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { it?.toIntOrNull()?.let { age -> age > 0 } == true }
        ).process(input["age"])
        if (age is Result.Failure) errors.addAll(age.error.errors)

        return if (errors.isEmpty()) {
            Result.success(ValidatedData(name.getOrNull()!!, age.getOrNull()!!.toInt()))
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}
```

**3. Database Query Step**

```kotlin
object QueryDataStep : Step<QueryInput, AppFailure.DatabaseError, List<Data>> {
    override suspend fun process(input: QueryInput): Result<AppFailure.DatabaseError, List<Data>> {
        return DatabaseSteps.query<QueryInput, AppFailure.DatabaseError, List<Data>>(
            step = object : Step<QueryInput, AppFailure.DatabaseError, List<Data>> {
                override suspend fun process(input: QueryInput): Result<AppFailure.DatabaseError, List<Data>> {
                    val sql = SafeSQL.select(
                        "SELECT * FROM table WHERE condition = ?",
                        listOf(input.value)
                    )

                    val results = executeQuery(sql) { rs ->
                        Data(rs.getInt("id"), rs.getString("name"))
                    }

                    return Result.success(results)
                }
            }
        ).process(input)
    }
}
```

**4. Business Rule Validation Step**

```kotlin
object ValidateNoCyclesStep : Step<DependencyInput, AppFailure, DependencyInput> {
    override suspend fun process(input: DependencyInput): Result<AppFailure, DependencyInput> {
        val hasCycle = checkForCycle(input.dependentId, input.dependencyId)

        return if (!hasCycle) {
            Result.success(input)
        } else {
            Result.failure(
                AppFailure.customValidationError(
                    "dependencies",
                    "Adding this dependency would create a cycle"
                )
            )
        }
    }
}
```

---

## ⚠️ Error Handling

### AppFailure Hierarchy

**Complete error type system:**

```kotlin
sealed interface AppFailure {

    // Authentication errors
    sealed interface AuthError : AppFailure {
        data object NotAuthorized : AuthError
        data object MissingToken : AuthError
        data object CouldNotCreateToken : AuthError
        data class ConvertTokenError(
            val errorCode: String,
            val arguments: List<Pair<String, String>> = emptyList()
        ) : AuthError
    }

    // Database errors
    sealed interface DatabaseError : AppFailure {
        data object ConnectionError : DatabaseError
        data object StatementError : DatabaseError
        data object IntegrityConstraintError : DatabaseError
        data object ResultMappingError : DatabaseError
        data object UnknownError : DatabaseError
        data object NoIdReturned : DatabaseError
        data object NotFound : DatabaseError
    }

    // External API errors
    sealed interface ApiError : AppFailure {
        data object NetworkError : ApiError
        data object TimeoutError : ApiError
        data object RateLimitExceeded : ApiError
        data class HttpError(val statusCode: Int, val body: String? = null) : ApiError
        data object SerializationError : ApiError
        data object UnknownError : ApiError
    }

    // Validation errors
    data class ValidationError(val errors: List<ValidationFailure>) : AppFailure

    // Redirect (special case for auth flows)
    data class Redirect(
        val path: String,
        val queryParameters: Map<String, String> = emptyMap()
    ) : AppFailure {
        fun toUrl(): String
    }

    // Configuration errors
    data class IllegalConfigurationError(val reason: String) : AppFailure

    // File handling errors
    data class FileError(val source: Class<Step<*, *, *>>) : AppFailure
}
```

### ValidationFailure Types

```kotlin
sealed interface ValidationFailure {
    data class MissingParameter(val parameterName: String) : ValidationFailure
    data class InvalidFormat(val parameterName: String, val message: String?) : ValidationFailure
    data class InvalidValue(val parameterName: String, val message: String) : ValidationFailure
    data class CustomValidation(val field: String, val message: String) : ValidationFailure
}
```

### Error Handling Patterns

**In Pipeline onFailure:**

```kotlin
executePipeline(
    onSuccess = { result -> /* ... */ },
    onFailure = { failure ->
        when (failure) {
            is AppFailure.AuthError.NotAuthorized ->
                respondBadRequest("You don't have permission to perform this action")

            is AppFailure.DatabaseError.NotFound ->
                respondBadRequest("Resource not found")

            is AppFailure.DatabaseError.IntegrityConstraintError ->
                respondBadRequest("This operation violates database constraints")

            is AppFailure.ValidationError ->
                respondBadRequest(failure.errors.joinToString(", ") { formatValidationError(it) })

            is AppFailure.Redirect ->
                call.respondRedirect(failure.toUrl())

            else ->
                respondBadRequest("An error occurred. Please try again.")
        }
    }
)
```

**Formatting Validation Errors:**

```kotlin
fun formatValidationError(error: ValidationFailure): String = when (error) {
    is ValidationFailure.MissingParameter ->
        "${error.parameterName} is required"
    is ValidationFailure.InvalidFormat ->
        "${error.parameterName}: ${error.message ?: "Invalid format"}"
    is ValidationFailure.InvalidValue ->
        "${error.parameterName}: ${error.message}"
    is ValidationFailure.CustomValidation ->
        "${error.field}: ${error.message}"
}
```

**Creating Custom Errors:**

```kotlin
// Quick validation error
AppFailure.customValidationError("fieldName", "Error message")

// Multiple validation errors
AppFailure.ValidationError(
    listOf(
        ValidationFailure.MissingParameter("name"),
        ValidationFailure.InvalidFormat("email", "Must be valid email")
    )
)

// Auth error
AppFailure.AuthError.NotAuthorized

// Database error
AppFailure.DatabaseError.NotFound
```

### Error Mapping

**Converting between error types:**

```kotlin
result.mapError { dbError ->
    when (dbError) {
        is AppFailure.DatabaseError.NotFound -> AppFailure.ValidationError(
            listOf(ValidationFailure.CustomValidation("resource", "Not found"))
        )
        else -> dbError
    }
}
```

---

## 🎨 HTMX Integration

### HTMX Overview

**Key Concept**: HTMX allows dynamic page updates without full page reloads or client-side JavaScript frameworks.

**Response Types:**

- **GET endpoints**: Return full pages using `createPage()`
- **PUT/PATCH/POST/DELETE endpoints**: Return HTML fragments that replace specific elements

### HTMX Helper Functions

**Available in `hx.kt`:**

```kotlin
// Basic HTTP methods
fun HTMLTag.hxGet(value: String)
fun HTMLTag.hxPost(value: String)
fun HTMLTag.hxPut(value: String)
fun HTMLTag.hxPatch(value: String)
fun HTMLTag.hxDelete(value: String)

// Advanced delete with confirmation modal
fun HTMLTag.hxDeleteWithConfirm(
    url: String,
    title: String,
    description: String,
    warning: String,
    confirmText: String? = null  // Type-to-confirm mode
)

// Targeting and swapping
fun HTMLTag.hxTarget(value: String)
fun HTMLTag.hxSwap(value: String)

// Additional attributes
fun HTMLTag.hxConfirm(value: String)
fun HTMLTag.hxTrigger(value: String)
fun HTMLTag.hxPushUrl(value: String)
```

### HTMX Usage Patterns

**1. Form Submission**

```kotlin
form {
    id = "create-project-form"
    hxPost("/app/worlds/\${worldId}/projects")
    hxTarget("#create-project-form")  // Replace self

    input(classes = "form-control") {
        name = "name"
        placeholder = "Project name"
    }

    button(classes = "btn btn--action") {
        type = ButtonType.submit
        +"Create Project"
    }
}
```

**Server Response:**

```kotlin
respondHtml(createHTML().div {
    id = "create-project-form"  // Matches hxTarget
    div("notice notice--success") {
        +"Project created successfully!"
    }
    // Optionally show updated form or redirect
})
```

**2. Update Actions**

```kotlin
button(classes = "btn btn--action") {
    hxPut("/app/worlds/\${worldId}/projects/\${projectId}/stage")
    hxTarget("#project-stage-indicator")
    +"Move to Building"
}
```

**Server Response:**

```kotlin
respondHtml(createHTML().div {
    id = "project-stage-indicator"
    div("chip chip--success") {
        +"Building"
    }
})
```

**3. Delete with Confirmation**

```kotlin
button(classes = "btn btn--danger") {
    hxDeleteWithConfirm(
        url = "/app/worlds/\${worldId}/projects/\${projectId}",
        title = "Delete Project",
        description = "Are you sure you want to delete this project?",
        warning = "This will also delete all tasks and cannot be undone.",
        confirmText = "DELETE"  // User must type this
    )
    +"Delete Project"
}
```

**Server Response (after confirmation):**

```kotlin
respondHtml(createHTML().div {
    div("notice notice--success") {
        +"Project deleted successfully"
    }
    // Optionally trigger redirect via HTMX
})
```

**4. Dynamic Content Loading**

```kotlin
div {
    id = "task-list"
    hxGet("/app/worlds/\${worldId}/projects/\${projectId}/tasks")
    hxTrigger("load")  // Load on page load
    +"Loading tasks..."
}
```

**5. Inline Editing**

```kotlin
div {
    id = "project-description"
    span { +project.description }
    button {
        hxGet("/app/worlds/\${worldId}/projects/\${projectId}/edit/description")
        hxTarget("#project-description")
        +"Edit"
    }
}
```

**Server Response (edit form):**

```kotlin
respondHtml(createHTML().div {
    id = "project-description"
    form {
        hxPut("/app/worlds/\${worldId}/projects/\${projectId}/description")
        hxTarget("#project-description")

        textarea(classes = "form-control") {
            name = "description"
            +project.description
        }
        button(classes = "btn btn--action") { +"Save" }
        button(classes = "btn btn--neutral") {
            hxGet("/app/worlds/\${worldId}/projects/\${projectId}")
            hxTarget("#project-description")
            +"Cancel"
        }
    }
})
```

### HTMX Best Practices

1. **Always specify hxTarget** - Know what element will be replaced
2. **Match IDs** - Response element ID should match target
3. **Return fragments** - PUT/PATCH/POST/DELETE return HTML fragments, not full pages
4. **Use semantic HTTP** - GET for retrieval, POST for creation, PUT for full updates, PATCH for partial, DELETE for
   removal
5. **Include success messages** - Users need feedback
6. **Handle errors gracefully** - Return error HTML fragments

### HTMX Troubleshooting

**Problem**: Nothing updates after form submission

- Check that hxTarget element ID exists
- Verify response HTML contains matching ID
- Check browser network tab for response

**Problem**: Full page reloads instead of fragment update

- Ensure hxPost/hxPut/etc. are set on form/button
- Verify HTMX library loaded in page
- Check for JavaScript errors in console

**Problem**: Confirmation modal doesn't appear

- Verify hxDeleteWithConfirm syntax
- Check that modal template is included in base page
- Ensure modal JavaScript is loaded

---

## 📄 HTML Template Patterns

### Template Structure

**Base Page Template:**

```kotlin
fun createPage(
    user: TokenProfile,
    pageTitle: String,
    pageScripts: Set<PageScript> = emptySet(),
    pageStyles: Set<PageStyle> = PageStyle.entries.toSet(),
    content: MAIN.() -> Unit
): String = createHTML().html {
    head {
        title { +pageTitle }
        // CSS links
        pageStyles.forEach { style ->
            link(rel = "stylesheet", href = "/static/styles/${style.fileName}")
        }
        // Scripts (HTMX, etc.)
        pageScripts.forEach { script ->
            script(src = script.url) {}
        }
    }
    body {
        header { /* Navigation */ }
        main {
            classes = setOf("container")
            content()
        }
        footer { /* Footer */ }
    }
}
```

### Component Patterns

**1. Feature Page Template**

```kotlin
fun createFeaturePage(
    user: TokenProfile,
    data: FeatureData,
    options: FeatureOptions = FeatureOptions()
) = createPage(
    user = user,
    pageTitle = "Feature Title",
    pageScripts = setOf(PageScript.HTMX)
) {
    classes += "feature-page"

    featureHeader(data, options)
    featureContent(data, options)
    featureActions(data, options)
}
```

**2. Component Subdivision**

```kotlin
private fun MAIN.featureHeader(data: FeatureData, options: FeatureOptions) {
    div("feature-header") {
        h1("feature-header__title") { +data.title }
        featureBreadcrumb(data.breadcrumbPath)
    }
}

private fun DIV.featureBreadcrumb(path: List<BreadcrumbItem>) {
    nav("breadcrumb") {
        ul("breadcrumb__list") {
            path.forEach { item ->
                li("breadcrumb__item") {
                    a(href = item.url) { +item.label }
                }
            }
        }
    }
}
```

**3. Form Component**

```kotlin
fun DIV.featureForm(
    data: FeatureData,
    action: String,
    method: FormMethod = FormMethod.post
) {
    form {
        id = "feature-form"
        this.action = action
        this.method = method

        // HTMX attributes
        hxPost(action)
        hxTarget("#feature-form")

        // Form fields
        div("form-group") {
            label {
                htmlFor = "name-input"
                +"Name"
            }
            input(classes = "form-control") {
                id = "name-input"
                name = "name"
                value = data.name
                required = true
            }
        }

        // Submit button
        button(classes = "btn btn--action") {
            type = ButtonType.submit
            +"Save"
        }
    }
}
```

**4. List Component**

```kotlin
fun DIV.featureList(items: List<FeatureItem>) {
    div("list") {
        items.forEach { item ->
            featureListItem(item)
        }
    }
}

private fun DIV.featureListItem(item: FeatureItem) {
    div("list__item") {
        div("list__item-content") {
            h4("list__item-title") { +item.name }
            p("list__item-meta") { +item.description }
        }
        div("list__item-actions") {
            button(classes = "btn btn--sm btn--action") {
                hxGet("/feature/${item.id}/edit")
                +"Edit"
            }
        }
    }
}
```

**5. Notice/Alert Component**

```kotlin
fun DIV.notice(type: NoticeType, message: String) {
    div("notice notice--${type.name.lowercase()}") {
        div("notice__header notice__header--${type.name.lowercase()}") {
            h3("notice__title") { +type.title }
        }
        div("notice__body") {
            p { +message }
        }
    }
}

enum class NoticeType(val title: String) {
    INFO("Information"),
    SUCCESS("Success"),
    WARNING("Warning"),
    DANGER("Error")
}
```

### CSS Integration

**Using Component Classes:**

```kotlin
// Buttons
button(classes = "btn btn--action") { +"Primary Action" }
button(classes = "btn btn--neutral") { +"Secondary" }
button(classes = "btn btn--danger") { +"Delete" }

// Form controls
input(classes = "form-control") { /* ... */ }
select(classes = "form-control") { /* ... */ }
textarea(classes = "form-control") { /* ... */ }

// Layout
div("container") {
    div("grid grid--cols-2") {
        div { +"Column 1" }
        div { +"Column 2" }
    }
}

// Utilities
div("u-flex u-flex-between u-flex-align-center") {
    span { +"Left" }
    span { +"Right" }
}
```

**Using Design Tokens (inline styles when necessary):**

```kotlin
div {
    style = "color: var(--clr-text-subtle); margin-bottom: var(--spacing-md);"
    +"Secondary text"
}
```

### Template Best Practices

1. **Subdivide large templates** - Break into smaller components
2. **Use CSS classes** - Avoid inline styles except for dynamic values
3. **Match HTMX targets** - Response element IDs must match hxTarget
4. **Include IDs for targets** - Any element that might be updated needs an ID
5. **Use semantic HTML** - `<nav>`, `<article>`, `<section>`, etc.
6. **Accessibility** - Include `aria-*` attributes, labels for inputs

---

## 🗄️ Database Operations

### SafeSQL Pattern

**Factory Methods (NEVER use constructor):**

SafeSQL is a wrapper for SQL query strings that provides safety against SQL injection attacks by validating queries
before execution. It enforces that queries start with the correct SQL keyword and checks for dangerous patterns like SQL
comments, stored procedures, and DDL operations.

```kotlin
// SELECT
val sql = SafeSQL.select(
    "SELECT id, name FROM items WHERE world_id = ?"
)

// INSERT
val sql = SafeSQL.insert(
    "INSERT INTO items (name, world_id, created_by) VALUES (?, ?, ?)"
)

// UPDATE
val sql = SafeSQL.update(
    "UPDATE items SET name = ?, updated_at = NOW() WHERE id = ?"
)

// DELETE
val sql = SafeSQL.delete(
    "DELETE FROM items WHERE id = ?"
)

// WITH (for CTEs)
val sql = SafeSQL.with(
    "WITH ranked_items AS (SELECT * FROM items) SELECT * FROM ranked_items"
)
```

**Why Factory Methods?**

- Constructor is private (compile-time safety)
- Factory methods enforce correct SQL type (select, insert, update, delete, with)
- Validates against SQL injection patterns
- Easier to add validation

### DatabaseSteps Patterns

**1. Query (SELECT)**

DatabaseSteps.query returns a Step that executes a SELECT query. It takes:

- `sql`: SafeSQL query
- `parameterSetter`: Function to set prepared statement parameters
- `resultMapper`: Function to map ResultSet to your type
- `transactionConnection`: Optional transaction connection (for use within transactions)

```kotlin
val queryStep = DatabaseSteps.query<InputType, OutputType>(
    sql = SafeSQL.select("SELECT * FROM table WHERE condition = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.value)
    },
    resultMapper = { rs ->
        val results = mutableListOf<YourModel>()
        while (rs.next()) {
            results.add(
                YourModel(
                    id = rs.getInt("id"),
                    name = rs.getString("name"),
                    createdAt = rs.getTimestamp("created_at").toZonedDateTime()
                )
            )
        }
        results
    }
)

// Use the step in a pipeline
val result = queryStep.process(input)
```

**2. Single Row Query**

```kotlin
val queryStep = DatabaseSteps.query<InputType, OutputType?>(
    sql = SafeSQL.select("SELECT * FROM table WHERE id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.id)
    },
    resultMapper = { rs ->
        if (rs.next()) {
            mapToModel(rs)
        } else {
            null
        }
    }
)

val result = queryStep.process(input)
if (result is Result.Success && result.value != null) {
    // Found
} else {
    // Not found - handle appropriately
}
```

**3. Update (INSERT/UPDATE/DELETE)**

DatabaseSteps.update returns a Step for INSERT/UPDATE/DELETE operations. For INSERT with RETURNING, it returns the ID.
For other operations, it returns the number of affected rows.

```kotlin
val updateStep = DatabaseSteps.update<InputType>(
    sql = SafeSQL.update("UPDATE table SET column = ? WHERE id = ?"),
    parameterSetter = { statement, input ->
        statement.setString(1, input.newValue)
        statement.setInt(2, input.id)
    }
)

val result = updateStep.process(input)
// result contains number of affected rows
```

**INSERT with RETURNING:**

```kotlin
val insertStep = DatabaseSteps.update<InputType>(
    sql = SafeSQL.insert(
        """
        INSERT INTO table (name, value) 
        VALUES (?, ?) 
        RETURNING id
        """
    ),
    parameterSetter = { statement, input ->
        statement.setString(1, input.name)
        statement.setString(2, input.value)
    }
)

val result = insertStep.process(input)
// result contains the new ID
```

**4. Transaction (Multi-Step)**

DatabaseSteps.transaction takes a function that receives a TransactionConnection and returns a Step. All database
operations within the transaction must use the provided connection.

```kotlin
val transactionStep = DatabaseSteps.transaction<InputType, OutputType> { txConn ->
    object : Step<InputType, AppFailure.DatabaseError, OutputType> {
        override suspend fun process(input: InputType): Result<AppFailure.DatabaseError, OutputType> {
            // Step 1: Insert main record
            val insertStep = DatabaseSteps.update<InputType>(
                sql = SafeSQL.insert(
                    "INSERT INTO table (name) VALUES (?) RETURNING id"
                ),
                parameterSetter = { statement, inp ->
                    statement.setString(1, inp.name)
                },
                transactionConnection = txConn
            )
            val insertResult = insertStep.process(input)
            val id = when (insertResult) {
                is Result.Success -> insertResult.value
                is Result.Failure -> return insertResult
            }

            // Step 2: Insert related records
            val relatedStep = DatabaseSteps.update<Int>(
                sql = SafeSQL.insert(
                    "INSERT INTO related_table (parent_id, value) VALUES (?, ?)"
                ),
                parameterSetter = { statement, parentId ->
                    statement.setInt(1, parentId)
                    statement.setString(2, input.relatedValue)
                },
                transactionConnection = txConn
            )
            val relatedResult = relatedStep.process(id)
            if (relatedResult is Result.Failure) return relatedResult

            // Step 3: Get complete result
            val selectStep = DatabaseSteps.query<Int, OutputType>(
                sql = SafeSQL.select("SELECT * FROM table WHERE id = ?"),
                parameterSetter = { statement, parentId ->
                    statement.setInt(1, parentId)
                },
                resultMapper = { rs ->
                    if (rs.next()) mapToModel(rs) else null
                },
                transactionConnection = txConn
            )
            val selectResult = selectStep.process(id)
            when (selectResult) {
                is Result.Success -> {
                    if (selectResult.value != null) {
                        return Result.success(selectResult.value)
                    } else {
                        return Result.failure(AppFailure.DatabaseError.NotFound)
                    }
                }
                is Result.Failure -> return selectResult
            }
        }
    }
}

// Use in pipeline
val result = transactionStep.process(input)
```

**Transaction Behavior:**

- Sets `connection.autoCommit = false`
- Commits on `Result.Success`
- Rolls back on `Result.Failure`
- Rolls back on exceptions
- Proper connection cleanup

### ResultSet Mapping

**Standard Pattern:**

```kotlin
fun ResultSet.mapToProject(): Project {
    return Project(
        id = getInt("id"),
        worldId = getInt("world_id"),
        name = getString("name"),
        description = getString("description"),
        type = ProjectType.valueOf(getString("type")),
        stage = ProjectStage.valueOf(getString("stage")),
        ideaId = getInt("idea_id").takeIf { !wasNull() },
        createdBy = getInt("created_by"),
        createdAt = getTimestamp("created_at").toZonedDateTime(),
        updatedAt = getTimestamp("updated_at").toZonedDateTime()
    )
}
```

**Handling Nulls:**

```kotlin
val optionalValue = rs.getInt("optional_column").takeIf { !rs.wasNull() }
val nullableString = rs.getString("nullable_column")  // Already null if SQL NULL
```

**Joins:**

```kotlin
fun ResultSet.mapToProjectWithWorld(): ProjectWithWorld {
    return ProjectWithWorld(
        project = Project(
            id = getInt("project_id"),
            worldId = getInt("world_id"),
            // ...
        ),
        world = World(
            id = getInt("world_id"),
            name = getString("world_name"),
            // ...
        )
    )
}
```

### Database Best Practices

1. **Always use SafeSQL** - Never string concatenation
2. **Use transactions for multi-step** - Ensures atomicity
3. **Handle NULL values** - Use `takeIf { !wasNull() }`
4. **Use RETURNING clause** - Get inserted ID in one query
5. **Index foreign keys** - Performance for joins
6. **Include audit fields** - created_by, created_at, updated_at
7. **Use prepared statements** - SafeSQL does this automatically

### Common SQL Patterns

**Insert with RETURNING:**

```sql
INSERT INTO projects (name, world_id, created_by, created_at, updated_at)
VALUES (?, ?, ?, NOW(), NOW())
RETURNING id, name, world_id, created_by, created_at, updated_at
```

**Update with timestamp:**

```sql
UPDATE projects
SET name       = ?,
    updated_at = NOW(),
    updated_by = ?
WHERE id = ?
```

**Conditional query:**

```sql
SELECT *
FROM projects
WHERE world_id = ?
  AND ($1::text IS NULL OR stage = $1::text)
  AND ($2::text IS NULL OR type = $2::text)
ORDER BY created_at DESC
```

**Join pattern:**

```sql
SELECT p.*, w.name as world_name
FROM projects p
         JOIN worlds w ON p.world_id = w.id
WHERE p.id = ?
```

### Complex Query Patterns

**With CTEs (Common Table Expressions):**

```kotlin
val sql = SafeSQL.select(
    """
    WITH project_stats AS (
        SELECT 
            project_id,
            COUNT(*) as task_count,
            SUM(CASE WHEN completed = true THEN 1 ELSE 0 END) as completed_count
        FROM action_tasks
        GROUP BY project_id
    ),
    item_stats AS (
        SELECT
            project_id,
            COUNT(*) as item_task_count,
            SUM(quantity_required) as total_items_needed
        FROM item_tasks it
        JOIN item_task_requirements itr ON it.id = itr.item_task_id
        GROUP BY project_id
    )
    SELECT 
        p.*,
        ps.task_count,
        ps.completed_count,
        is.item_task_count,
        is.total_items_needed
    FROM projects p
    LEFT JOIN project_stats ps ON p.id = ps.project_id
    LEFT JOIN item_stats is ON p.id = is.project_id
    WHERE p.world_id = ?
    ORDER BY p.created_at DESC
    """
)
```

**With Aggregations:**

```kotlin
val sql = SafeSQL.select(
    """
    SELECT 
        w.id,
        w.name,
        COUNT(DISTINCT p.id) as project_count,
        COUNT(DISTINCT wm.user_id) as member_count,
        MAX(p.updated_at) as last_activity
    FROM worlds w
    LEFT JOIN projects p ON w.id = p.world_id
    LEFT JOIN world_members wm ON w.id = wm.world_id
    WHERE wm.user_id = ?
    GROUP BY w.id, w.name
    HAVING COUNT(DISTINCT p.id) > 0
    ORDER BY last_activity DESC NULLS LAST
    """
)
```

**With Window Functions:**

```kotlin
val sql = SafeSQL.select(
    """
    SELECT 
        p.*,
        ROW_NUMBER() OVER (PARTITION BY p.type ORDER BY p.created_at DESC) as type_rank,
        LAG(p.name) OVER (ORDER BY p.created_at) as previous_project_name
    FROM projects p
    WHERE p.world_id = ?
    """
)
```

**With Recursive CTEs (for dependencies):**

```kotlin
val sql = SafeSQL.select(
    """
    WITH RECURSIVE dependency_tree AS (
        -- Base case: starting project
        SELECT 
            id, 
            name, 
            0 as depth,
            ARRAY[id] as path
        FROM projects 
        WHERE id = ?
        
        UNION ALL
        
        -- Recursive case: dependencies
        SELECT 
            p.id,
            p.name,
            dt.depth + 1,
            dt.path || p.id
        FROM projects p
        JOIN project_dependencies pd ON p.id = pd.dependency_project_id
        JOIN dependency_tree dt ON pd.dependent_project_id = dt.id
        WHERE NOT p.id = ANY(dt.path)  -- Prevent cycles
    )
    SELECT * FROM dependency_tree
    ORDER BY depth, name
    """
)
```

### Pagination Pattern

```kotlin
data class PaginationInput(
    val page: Int = 1,
    val pageSize: Int = 20
) {
    val offset: Int get() = (page - 1) * pageSize
    val limit: Int get() = pageSize
}

data class PaginatedResult<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int
)

object GetPaginatedProjectsStep : Step<PaginationInput, AppFailure.DatabaseError, PaginatedResult<Project>> {
    override suspend fun process(input: PaginationInput): Result<AppFailure.DatabaseError, PaginatedResult<Project>> {
        // Get total count
        val countSql = SafeSQL.select("SELECT COUNT(*) FROM projects WHERE world_id = ?")
        val totalCount = DatabaseSteps.query(
            countSql,
            parameterSetter = { stmt -> stmt.setInt(1, input.worldId) },
            mapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).getOrElse { return Result.failure(it) }

        // Get paginated data
        val dataSql = SafeSQL.select(
            """
            SELECT * FROM projects 
            WHERE world_id = ? 
            ORDER BY created_at DESC 
            LIMIT ? OFFSET ?
            """
        )
        val projects = DatabaseSteps.queryList(
            dataSql,
            parameterSetter = { stmt ->
                stmt.setInt(1, input.worldId)
                stmt.setInt(2, input.limit)
                stmt.setInt(3, input.offset)
            },
            mapper = { rs -> rs.mapToProject() }
        ).getOrElse { return Result.failure(it) }

        return Result.success(
            PaginatedResult(
                items = projects,
                page = input.page,
                pageSize = input.pageSize,
                totalCount = totalCount,
                totalPages = (totalCount + input.pageSize - 1) / input.pageSize
            )
        )
    }
}
```

### File Upload Pattern

```kotlin
data class FileUploadInput(
    val file: PartData.FileItem,
    val maxSizeBytes: Long = 10_485_760, // 10 MB
    val allowedExtensions: Set<String> = setOf("schematic", "litematic", "nbt")
)

object ValidateFileUploadStep : Step<FileUploadInput, AppFailure.ValidationError, FileUploadInput> {
    override suspend fun process(input: FileUploadInput): Result<AppFailure.ValidationError, FileUploadInput> {
        val file = input.file
        val fileName = file.originalFileName ?: return Result.failure(
            AppFailure.ValidationError(listOf(ValidationFailure("file", "No filename provided")))
        )

        // Check extension
        val extension = fileName.substringAfterLast('.', "")
        if (extension !in input.allowedExtensions) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(
                        ValidationFailure(
                            "file",
                            "Invalid file type. Allowed: ${input.allowedExtensions.joinToString()}"
                        )
                    )
                )
            )
        }

        // Check file size
        val bytes = file.streamProvider().readBytes()
        if (bytes.size > input.maxSizeBytes) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(
                        ValidationFailure("file", "File too large. Max size: ${input.maxSizeBytes / 1_048_576} MB")
                    )
                )
            )
        }

        return Result.success(input)
    }
}

suspend fun ApplicationCall.handleFileUpload() {
    val multipart = receiveMultipart()
    val user = getUser()
    val worldId = getWorldId()

    var fileItem: PartData.FileItem? = null
    var projectName: String? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> fileItem = part
            is PartData.FormItem -> {
                if (part.name == "projectName") projectName = part.value
            }
            else -> {}
        }
        part.dispose()
    }

    if (fileItem == null) {
        respondBadRequest("No file uploaded")
        return
    }

    val pipeline = Pipeline.create<AppFailure, FileUploadInput>()
        .pipe(ValidateFileUploadStep)
        .pipe(ProcessSchematicFileStep)
        .pipe(SaveFileMetadataStep)

    pipeline.fold(
        input = FileUploadInput(fileItem!!),
        onSuccess = { result ->
            respondHtml(createHTML().div {
                div("notice notice--success") { +"File uploaded successfully" }
            })
        },
        onFailure = { failure ->
            respondBadRequest("Upload failed: ${failure}")
        }
    )
}
```

---

## ✅ Validation Patterns

### ValidationSteps Usage

**Required Field:**

```kotlin
val name = ValidationSteps.required(
    "name",
    { AppFailure.ValidationError(listOf(it)) }
).process(parameters)
```

**Optional Field:**

```kotlin
val description = ValidationSteps.optional("description")
    .process(parameters)
```

**Custom Validation:**

```kotlin
val email = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
    "email",
    "Must be valid email address",
    errorMapper = { AppFailure.ValidationError(listOf(it)) },
    predicate = { it?.contains("@") == true && it.contains(".") }
).process(parameters["email"])
```

**Enum Validation:**

```kotlin
val type = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
    "type",
    "Invalid project type",
    errorMapper = { AppFailure.ValidationError(listOf(it)) },
    predicate = { !it.isNullOrBlank() && runCatching { ProjectType.valueOf(it.uppercase()) }.isSuccess }
).process(parameters["type"]).map { ProjectType.valueOf(it!!.uppercase()) }
```

**Number Range Validation:**

```kotlin
val priority = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
    "priority",
    "Priority must be between 1 and 10",
    errorMapper = { AppFailure.ValidationError(listOf(it)) },
    predicate = { it?.toIntOrNull()?.let { p -> p in 1..10 } == true }
).process(parameters["priority"]).map { it!!.toInt() }
```

### Complete Validation Step Example

```kotlin
object ValidateCreateProjectInputStep : Step<Parameters, AppFailure.ValidationError, CreateProjectInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateProjectInput> {
        val errors = mutableListOf<ValidationFailure>()

        // Required field
        val name = ValidationSteps.required("name", { AppFailure.ValidationError(listOf(it)) })
            .process(input)
        if (name is Result.Failure) errors.addAll(name.error.errors)

        // Optional field
        val description = ValidationSteps.optional("description")
            .process(input)

        // Enum validation
        val type = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "type",
            "Invalid project type",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { !it.isNullOrBlank() && runCatching { ProjectType.valueOf(it.uppercase()) }.isSuccess }
        ).process(input["type"])
        if (type is Result.Failure) errors.addAll(type.error.errors)

        // Return result
        return if (errors.isEmpty()) {
            Result.success(
                CreateProjectInput(
                    name = name.getOrNull()!!,
                    description = description.getOrNull() ?: "",
                    type = ProjectType.valueOf(type.getOrNull()!!.uppercase())
                )
            )
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}
```

### Business Rule Validation

**Example: No Circular Dependencies**

```kotlin
object ValidateNoCyclesStep : Step<AddDependencyInput, AppFailure, AddDependencyInput> {
    override suspend fun process(input: AddDependencyInput): Result<AppFailure, AddDependencyInput> {
        // Build dependency graph
        val dependencies = getAllDependencies(input.worldId)

        // Check if adding this dependency creates a cycle
        val wouldCreateCycle = wouldCreateCycle(
            dependencies,
            from = input.dependentProjectId,
            to = input.dependencyProjectId
        )

        return if (!wouldCreateCycle) {
            Result.success(input)
        } else {
            Result.failure(
                AppFailure.customValidationError(
                    "dependency",
                    "Adding this dependency would create a circular dependency"
                )
            )
        }
    }

    private fun wouldCreateCycle(
        existingDependencies: List<ProjectDependency>,
        from: Int,
        to: Int
    ): Boolean {
        // DFS to detect cycles
        val visited = mutableSetOf<Int>()
        val stack = mutableSetOf<Int>()

        fun dfs(node: Int): Boolean {
            if (stack.contains(node)) return true
            if (visited.contains(node)) return false

            visited.add(node)
            stack.add(node)

            val dependencies = existingDependencies
                .filter { it.dependentProjectId == node }
                .map { it.dependencyProjectId }

            for (dep in dependencies) {
                if (dfs(dep)) return true
            }

            stack.remove(node)
            return false
        }

        // Simulate adding the new dependency
        val simulatedDependencies = existingDependencies + ProjectDependency(
            id = 0,
            dependentProjectId = from,
            dependencyProjectId = to,
            createdBy = 0,
            createdAt = ZonedDateTime.now()
        )

        return dfs(from)
    }
}
```

---

---

## 🔐 Authentication & Authorization

### Overview

Authentication and authorization in MC-ORG are handled **outside** of pipelines using Ktor plugins. This ensures
security checks happen before any business logic executes.

### Plugin-Based Authorization

Authorization is enforced through Ktor plugins installed on route groups. These plugins check permissions before the
handler is called.

**Key Plugins** (defined in `RolePlugins.kt`):

1. **AdminPlugin** - Requires super admin role
2. **WorldAdminPlugin** - Requires admin role in specific world
3. **BannedPlugin** - Blocks banned users
4. **DemoUserPlugin** - Restricts demo users in production

### Plugin Usage Pattern

```kotlin
fun Route.worldRoutes() {
    route("/worlds/{worldId}") {
        // Install plugin to check world access
        install(WorldParamPlugin)  // Extracts worldId from path
        install(WorldAdminPlugin)  // Checks admin role

        // Handler executes only if plugins pass
        put {
            call.handleUpdateWorld()
        }
    }
}
```

### Authorization Flow

```
HTTP Request
    ↓
EnvPlugin (environment validation)
    ↓
AuthPlugin (JWT validation, user extraction)
    ↓
BannedPlugin (check if user is banned)
    ↓
WorldParamPlugin (extract and validate worldId)
    ↓
WorldAdminPlugin (check world admin role)
    ↓
Handler (business logic in pipeline)
```

### When Authorization Fails

Plugins respond with appropriate HTTP status codes:

- `403 Forbidden` - User lacks required permission
- `404 Not Found` - User doesn't have access (for admin endpoints)
- `401 Unauthorized` - Missing or invalid JWT token

### Role Checking in Plugins

**Example: WorldAdminPlugin**

```kotlin
val WorldAdminPlugin = createRouteScopedPlugin("WorldAdminPlugin") {
    onCall {
        val user = it.getUser()
        val worldId = it.getWorldId()

        val result = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit)
        if (result is Result.Failure && result.error is AppFailure.AuthError.NotAuthorized) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to access this world.")
        }
    }
}
```

### Accessing User in Handlers

After plugins validate authentication, handlers can safely access the authenticated user:

```kotlin
suspend fun ApplicationCall.handleFeature() {
    val user = this.getUser()  // TokenProfile - guaranteed to exist after AuthPlugin
    val worldId = this.getWorldId()  // Int - guaranteed to exist after WorldParamPlugin

    // User and worldId are already validated by plugins
    // Proceed with business logic
}
```

### Best Practices

1. **Never check authorization inside pipelines** - Use plugins
2. **Install plugins on route groups** - Not individual routes
3. **Plugin order matters** - Auth before role checks
4. **Use specific error messages** - Help users understand why access was denied
5. **Fail closed** - Deny by default, allow explicitly

---

## 🌍 Environment Variables

### Overview

MC-ORG uses environment variables for configuration, managed through the `AppConfig` object. Configuration is loaded
once at application startup and validated before the server starts.

### Configuration Loading

The `AppConfig` object (in `config/AppConfig.kt`) reads environment variables using `System.getenv()` during
initialization:

```kotlin
object AppConfig {
    var dbUrl: String = "jdbc:postgresql://localhost:5432/postgres"
    var dbUsername: String = "postgres"
    var dbPassword: String = "supersecret"
    var env: Env = Local

    init {
        val errors = mutableListOf<String>()

        System.getenv("DB_URL")?.let { dbUrl = it }
            ?: errors.add("DB_URL is not set")

        System.getenv("DB_USER")?.let { dbUsername = it }
            ?: errors.add("DB_USER is not set")

        // ... more environment variables

        if (errors.isNotEmpty()) {
            logger.error("Invalid configuration:\n${errors.joinToString("\n")}")
        }
    }
}
```

### Required Environment Variables

**Database:**

- `DB_URL` - PostgreSQL connection URL (required)
- `DB_USER` - Database username (required)
- `DB_PASSWORD` - Database password (required)

**Environment:**

- `ENV` - Environment type: `LOCAL`, `TEST`, or `PRODUCTION` (optional, defaults to `LOCAL`)

**Authentication:**

- `RSA_PRIVATE_KEY` - JWT signing key (required except LOCAL)
- `RSA_PUBLIC_KEY` - JWT verification key (required except LOCAL)
- `MICROSOFT_CLIENT_ID` - Microsoft OAuth client ID (required unless skipped)
- `MICROSOFT_CLIENT_SECRET` - Microsoft OAuth secret (required unless skipped)
- `SKIP_MICROSOFT_SIGN_IN` - Set to `true` to skip Microsoft auth (optional)

**Application:**

- `APP_HOST` - Application host URL (required except TEST)
- `DEMO_USER` - Demo user Minecraft username (optional, default: `evegul`)

**External APIs** (optional overrides):

- `MODRINTH_BASE_URL` - Modrinth API base URL
- `MICROSOFT_LOGIN_BASE_URL` - Microsoft login API
- `XBOX_AUTH_BASE_URL` - Xbox auth API
- `XSTS_AUTH_BASE_URL` - XSTS auth API
- `MINECRAFT_BASE_URL` - Minecraft services API
- `FABRIC_MC_BASE_URL` - Fabric metadata API
- `GITHUB_GISTS_BASE_URL` - GitHub Gists API

### Accessing Configuration

Configuration is accessed through the `AppConfig` object:

```kotlin
import app.mcorg.config.AppConfig

// Check environment
if (AppConfig.env == Production) {
    // Production-specific logic
}

// Use configuration
val dbConnection = DriverManager.getConnection(
    AppConfig.dbUrl,
    AppConfig.dbUsername,
    AppConfig.dbPassword
)
```

### Environment-Specific Behavior

The application behaves differently based on `ENV`:

**LOCAL:**

- Uses local RSA keys (generated on startup)
- More lenient validation
- Demo mode enabled by default

**TEST:**

- `APP_HOST` should not be set
- Uses test database
- Skips external API calls in tests

**PRODUCTION:**

- All required variables must be set
- Demo users have write restrictions
- Stricter security policies

### Validation & Error Handling

Configuration errors are collected during initialization and logged before the application starts. If critical
configuration is missing, the application will log errors but may still attempt to start (allowing for debugging).

**Example validation:**

```kotlin
init {
    val errors = mutableListOf<String>()

    System.getenv("DB_URL")?.let { dbUrl = it }
        ?: errors.add("DB_URL is not set")

    System.getenv("ENV")?.let {
        when (it) {
            "LOCAL" -> Local
            "TEST" -> Test
            "PRODUCTION" -> Production
            else -> {
                errors.add("ENV must be one of LOCAL, TEST, PRODUCTION")
                null
            }
        }
    }?.let { env = it }

    if (errors.isNotEmpty()) {
        logger.error("Invalid configuration:\n${errors.joinToString("\n")}")
    }
}
```

### Local Development Setup

For local development, create a `local.env` file (not tracked in git):

```bash
DB_URL=jdbc:postgresql://localhost:5432/mcorg_dev
DB_USER=postgres
DB_PASSWORD=supersecret
ENV=LOCAL
SKIP_MICROSOFT_SIGN_IN=true
```

Load environment variables before running:

```bash
export $(cat local.env | xargs)
mvn exec:java
```

### Docker/Production Setup

In Docker or production environments, pass environment variables through:

**Docker Compose:**

```yaml
environment:
  - DB_URL=postgresql://db:5432/mcorg
  - DB_USER=${DB_USER}
  - DB_PASSWORD=${DB_PASSWORD}
  - ENV=PRODUCTION
  - RSA_PRIVATE_KEY=${RSA_PRIVATE_KEY}
  - RSA_PUBLIC_KEY=${RSA_PUBLIC_KEY}
```

**Fly.io:**

```bash
flyctl secrets set DB_URL=... DB_USER=... DB_PASSWORD=...
flyctl secrets set RSA_PRIVATE_KEY=... RSA_PUBLIC_KEY=...
```

### Best Practices

1. **Never commit secrets** - Use environment variables
2. **Validate on startup** - Fail fast if configuration is invalid
3. **Provide defaults** - For non-sensitive, optional values
4. **Document requirements** - Clear error messages for missing config
5. **Use appropriate types** - Convert strings to proper types (Boolean, Enum, etc.)

---

## 🌐 HTTP Clients & External APIs

### Overview

MC-ORG uses `ApiProvider` (defined in `config/ApiProvider.kt`) for making HTTP requests to external APIs. ApiProvider is
a sealed class that provides type-safe, Step-based HTTP operations with built-in error handling, rate limiting, and JSON
serialization.

### ApiProvider Pattern

Each external API has its own ApiProvider implementation that extends the base class:

```kotlin
sealed class ApiProvider(
    protected val config: ApiConfig
) {
    protected val logger: Logger
    val json: Json  // Configured for lenient parsing

    // HTTP methods
    fun <I, S> get(url: String, headerBuilder, ...): Step<I, AppFailure.ApiError, S>
    fun <I, S> post(url: String, headerBuilder, bodyBuilder, ...): Step<I, AppFailure.ApiError, S>
    fun <I> getRaw(url: String, headerBuilder, ...): Step<I, AppFailure.ApiError, InputStream>
}
```

### Using ApiProvider in Steps

**Example: Fetching from external API**

```kotlin
object FetchMinecraftVersionsStep : Step<Unit, AppFailure.ApiError, List<MinecraftVersion>> {
    override suspend fun process(input: Unit): Result<AppFailure.ApiError, List<MinecraftVersion>> {
        val apiStep = FabricMcApiProvider.get<Unit, VersionsResponse>(
            url = "/versions",
            headerBuilder = { builder, _ ->
                builder.header("User-Agent", "MC-ORG/1.0")
            }
        )

        return apiStep.process(Unit).map { response ->
            response.versions.map { MinecraftVersion.fromString(it) }
        }
    }
}
```

### HTTP Methods

**GET Request:**

```kotlin
val step = apiProvider.get<InputType, ResponseType>(
    url = "/endpoint",
    headerBuilder = { builder, input ->
        builder.header("Authorization", "Bearer ${input.token}")
        builder.parameter("page", input.page.toString())
    }
)
```

**POST Request:**

```kotlin
val step = apiProvider.post<InputType, ResponseType>(
    url = "/endpoint",
    headerBuilder = { builder, input ->
        builder.header("Content-Type", "application/json")
    },
    bodyBuilder = { builder, input ->
        builder.setBody(input.requestData)
    }
)
```

**Raw Response (for files):**

```kotlin
val step = apiProvider.getRaw<InputType>(
    url = "/download",
    headerBuilder = { builder, input ->
        builder.header("Accept", "application/octet-stream")
    }
)
// Returns InputStream
```

### Built-in Features

**1. Automatic JSON Deserialization**

```kotlin
// Response automatically deserialized to type
val step = apiProvider.get<Unit, MyDataClass>(url = "/data")
```

**2. Error Handling**

ApiProvider automatically maps HTTP errors to `AppFailure.ApiError`:

- `NetworkError` - Connection failures
- `TimeoutError` - Request timeouts
- `RateLimitExceeded` - 429 responses
- `HttpError(statusCode, body)` - HTTP error responses
- `SerializationError` - JSON parsing failures

**3. Rate Limiting**

Built-in rate limit detection from response headers:

```kotlin
// Automatically respects:
// - X-RateLimit-Limit
// - X-RateLimit-Remaining  
// - X-RateLimit-Reset
```

### Configuration

API base URLs are configured in `AppConfig` and can be overridden with environment variables:

```kotlin
object AppConfig {
    var modrinthBaseUrl: String = "https://api.modrinth.com/v2"
    var minecraftBaseUrl: String = "https://api.minecraftservices.com"
    var fabricMcBaseUrl: String = "https://meta.fabricmc.net/v2"

    init {
        System.getenv("MODRINTH_BASE_URL")?.let { modrinthBaseUrl = it }
        System.getenv("MINECRAFT_BASE_URL")?.let { minecraftBaseUrl = it }
        // ...
    }
}
```

### Creating a Custom ApiProvider

**1. Define the provider:**

```kotlin
object MyApiProvider : ApiProvider(
    config = object : ApiConfig {
        override val baseUrl: String = AppConfig.myApiBaseUrl
        override val timeout: Long = 30_000
    }
)
```

**2. Use in steps:**

```kotlin
object FetchDataStep : Step<String, AppFailure.ApiError, MyData> {
    override suspend fun process(input: String): Result<AppFailure.ApiError, MyData> {
        return MyApiProvider.get<String, MyData>(
            url = "/data/$input",
            headerBuilder = { builder, _ ->
                builder.header("API-Key", AppConfig.myApiKey)
            }
        ).process(input)
    }
}
```

### Error Handling Pattern

```kotlin
val pipeline = Pipeline.create<AppFailure, Unit>()
    .pipe(FetchExternalDataStep)

pipeline.fold(
    input = Unit,
    onSuccess = { data ->
        // Use data
    },
    onFailure = { failure ->
        when (failure) {
            is AppFailure.ApiError.NetworkError ->
                logger.error("Network error connecting to API")
            is AppFailure.ApiError.TimeoutError ->
                logger.error("API request timed out")
            is AppFailure.ApiError.RateLimitExceeded ->
                logger.warn("Rate limit exceeded, retry later")
            is AppFailure.ApiError.HttpError ->
                logger.error("HTTP ${failure.statusCode}: ${failure.body}")
            is AppFailure.ApiError.SerializationError ->
                logger.error("Failed to parse API response")
            else -> logger.error("Unknown API error")
        }
    }
)
```

### Best Practices

1. **Use typed responses** - Define data classes for API responses
2. **Handle all error types** - Network, timeout, rate limit, HTTP errors
3. **Set appropriate timeouts** - Based on API characteristics
4. **Include User-Agent** - Identify your application
5. **Respect rate limits** - ApiProvider tracks automatically
6. **Log failures** - Help with debugging API issues
7. **Use raw responses for files** - Don't try to deserialize binary data

### Common API Providers

**Existing providers in the codebase:**

- `ModrinthApiProvider` - Modrinth mod repository API
- `MicrosoftAuthProvider` - Microsoft authentication
- `XboxAuthProvider` - Xbox Live authentication
- `MinecraftServicesProvider` - Minecraft profile services
- `FabricMcApiProvider` - Fabric metadata

---

## 🧪 Testing Requirements

### Test-First Development Protocol

**MANDATORY Before Implementation:**

1. **Create test file structure**
    - Mirror `src/main` structure in `src/test`
    - Example: `HandleCreateProject.kt` → `HandleCreateProjectTest.kt`

2. **Write failing tests**
    - Test happy path
    - Test validation failures
    - Test edge cases

3. **Implement to make tests pass**
    - Follow patterns from failing tests
    - Run tests frequently

4. **Verify all tests pass**
    - `mvn test` must pass before marking complete

### Unit Test Patterns

**Step Testing:**

```kotlin
class ValidateProjectInputStepTest {

    @Test
    fun `should succeed with valid input`() = runBlocking {
        val input = Parameters.build {
            append("name", "Test Project")
            append("description", "Test Description")
            append("type", "BUILDING")
        }

        val result = ValidateProjectInputStep.process(input)

        assertTrue(result.isSuccess)
        assertEquals("Test Project", result.getOrNull()?.name)
    }

    @Test
    fun `should fail with missing name`() = runBlocking {
        val input = Parameters.build {
            append("description", "Test Description")
        }

        val result = ValidateProjectInputStep.process(input)

        assertTrue(result.isFailure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppFailure.ValidationError)
    }

    @Test
    fun `should fail with invalid type`() = runBlocking {
        val input = Parameters.build {
            append("name", "Test")
            append("type", "INVALID_TYPE")
        }

        val result = ValidateProjectInputStep.process(input)

        assertTrue(result.isFailure)
    }
}
```

**Data Class Testing:**

```kotlin
class ProjectTest {

    @Test
    fun `should create project with all fields`() {
        val project = Project(
            id = 1,
            worldId = 1,
            name = "Test",
            description = "Description",
            type = ProjectType.BUILDING,
            stage = ProjectStage.PLANNING,
            ideaId = null,
            createdBy = 1,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )

        assertEquals(1, project.id)
        assertEquals("Test", project.name)
    }
}
```

### Integration Test Patterns

**Handler Testing:**

```kotlin
class CreateProjectIntegrationTest {

    @Test
    fun `should create project and return HTML`() = testApplication {
        application {
            configureRouting()
        }

        val response = client.post("/app/worlds/1/projects") {
            header("Cookie", "jwt=test_token")
            setBody(FormDataContent(Parameters.build {
                append("name", "New Project")
                append("type", "BUILDING")
            }))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("notice--success"))
    }
}
```

### Test Data Factories

**TestDataFactory Usage:**

```kotlin
object TestDataFactory {
    fun createUser(
        id: Int = 1,
        username: String = "testuser"
    ): User = User(
        id = id,
        username = username,
        minecraftUuid = "test-uuid",
        displayName = "Test User",
        globalRole = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now()
    )

    fun createWorld(
        id: Int = 1,
        createdBy: Int = 1
    ): World = World(
        id = id,
        name = "Test World",
        description = "Test Description",
        minecraftVersion = MinecraftVersion.Release("1.20.1"),
        createdBy = createdBy,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now()
    )
}
```

### Compilation Safety Protocol

**Before Committing:**

```bash
# 1. Clean compilation
mvn clean compile

# 2. Run all tests
mvn test

# 3. Check for warnings
mvn compile 2>&1 | grep -i warning
```

**Required Checklist:**

- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all tests
- [ ] No new warnings introduced
- [ ] Test coverage for new code
- [ ] Integration tests for new endpoints

---

## 🚀 Build and Deployment

### Development Workflow

```bash
# Start fresh
mvn clean

# Compile code
mvn compile

# Run tests
mvn test

# Run specific test
mvn test -Dtest=YourTestClass

# Apply database migrations
mvn flyway:migrate

# Rollback last migration
mvn flyway:undo

# Start development server
mvn exec:java
```

### Database Migrations

**Creating a Migration:**

1. **Create SQL file** in `src/main/resources/db/migration/`
2. **Naming**: `V{major}_{minor}_{patch}__{description}.sql`
    - Example: `V2_22_0__add_user_preferences.sql`
3. **Write forward migration**:

```sql
-- V2_22_0__add_user_preferences.sql

CREATE TABLE user_preferences
(
    user_id  INT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    theme    VARCHAR(50) NOT NULL DEFAULT 'light',
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences (user_id);
```

4. **Test migration**:

```bash
mvn flyway:migrate
```

5. **Test rollback** (if needed):

```bash
mvn flyway:undo
```

### Deployment Process

**Build Artifact:**

```bash
# Create deployment JAR
mvn clean package

# Artifact location
target/webapp-0.0.1-jar-with-dependencies.jar
```

**Docker Build:**

```bash
# Build container
docker build -t mcorg-webapp .

# Run locally
docker-compose up
```

**Fly.io Deployment:**

```bash
# Deploy to production
flyctl deploy

# Check status
flyctl status

# View logs
flyctl logs
```

### Environment Configuration

**Required Environment Variables:**

```env
# Database
DATABASE_URL=postgresql://user:pass@host:5432/dbname
FLYWAY_URL=postgresql://user:pass@host:5432/dbname
FLYWAY_USER=user
FLYWAY_PASSWORD=pass

# JWT
JWT_SECRET=your-secret-key
JWT_ISSUER=mc-org
JWT_AUDIENCE=mc-org-users

# Microsoft OAuth (if using)
MICROSOFT_CLIENT_ID=your-client-id
MICROSOFT_CLIENT_SECRET=your-client-secret
MICROSOFT_REDIRECT_URI=https://your-domain.com/auth/sign-in/microsoft/callback

# App Configuration
PORT=8080
ENVIRONMENT=production
```

**Local Development (local.env):**

```env
DATABASE_URL=postgresql://localhost:5432/mcorg_dev
JWT_SECRET=dev-secret-key
ENVIRONMENT=development
```

### Performance Optimization

**Database Indexes:**

- Always index foreign keys
- Index columns used in WHERE clauses
- Composite indexes for common query patterns

**Connection Pooling:**

- Configure HikariCP in application
- Set appropriate pool size

**Caching Strategy:**

- Cache static assets (CSS, JS, images)
- Consider caching frequently accessed data

---

## 📚 Additional Resources

- **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** - Quick orientation
- **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)** - System architecture
- **[BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)** - Domain rules
- **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)** - Styling guide
- **[PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)** - Feature status

---

**Document Version**: 1.0  
**Last Updated**: January 12, 2026  
**Maintained By**: Development Team

