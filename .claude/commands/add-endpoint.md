# Add New HTTP Endpoint

Template for creating a new HTTP endpoint in MC-ORG.

## Step 1: Create the Handler Function

Location: `src/main/kotlin/app/mcorg/presentation/handler/{Feature}Handler.kt`

```kotlin
suspend fun ApplicationCall.handle{Action}{Feature}() {
    // 1. Extract inputs
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()  // if world-scoped

    // 2. Build pipeline
    val pipeline = Pipeline.create<AppFailure, Parameters>()
        .pipe(Validate{Feature}InputStep)
        .pipe({Action}{Feature}Step(user.id, worldId))
        .pipe(Get{Feature}ByIdStep)

    // 3. Execute with fold
    pipeline.fold(
        input = parameters,
        onSuccess = { result ->
            respondHtml(createHTML().div {
                id = "{feature}-result"
                // Success HTML fragment
            })
        },
        onFailure = { failure ->
            respondBadRequest("Operation failed")
        }
    )
}
```

## Step 2: Create Validation Step

```kotlin
object Validate{Feature}InputStep : Step<Parameters, AppFailure.ValidationError, Validated{Feature}Input> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Validated{Feature}Input> {
        val errors = mutableListOf<ValidationFailure>()

        val name = input["name"]
        if (name.isNullOrBlank()) {
            errors.add(ValidationFailure.MissingParameter("name"))
        }

        return if (errors.isEmpty()) {
            Result.success(Validated{Feature}Input(name!!))
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}

data class Validated{Feature}Input(val name: String)
```

## Step 3: Create Business Logic Step

```kotlin
class {Action}{Feature}Step(
    private val userId: Int,
    private val worldId: Int
) : Step<Validated{Feature}Input, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Validated{Feature}Input): Result<AppFailure.DatabaseError, Int> {
        val insertStep = DatabaseSteps.update<Validated{Feature}Input>(
            sql = SafeSQL.insert("""
                INSERT INTO {features} (name, world_id, created_by, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { stmt, inp ->
                stmt.setString(1, inp.name)
                stmt.setInt(2, worldId)
                stmt.setInt(3, userId)
            }
        )
        return insertStep.process(input)
    }
}
```

## Step 4: Add Route

Location: `src/main/kotlin/app/mcorg/presentation/router/AppRouterV2.kt` (or appropriate router)

```kotlin
route("/{features}") {
    // Create
    post {
        call.handleCreate{Feature}()
    }

    route("/{featureId}") {
        install({Feature}ParamPlugin)  // if needed

        // Read
        get {
            call.handleGet{Feature}()
        }

        // Update
        put {
            call.handleUpdate{Feature}()
        }

        // Delete
        delete {
            call.handleDelete{Feature}()
        }
    }
}
```

## Step 5: Add Authorization Plugin (if needed)

For routes requiring specific roles, install the appropriate plugin:

```kotlin
route("/{features}") {
    install(WorldParamPlugin)
    install(WorldAdminPlugin)  // Requires Admin role

    post {
        call.handleCreate{Feature}()
    }
}
```

## Step 6: Write Tests

Location: `src/test/kotlin/app/mcorg/presentation/handler/{Feature}HandlerTest.kt`

```kotlin
class {Feature}HandlerTest {
    @Test
    fun `should create {feature} with valid input`() = testApplication {
        // Arrange
        val input = Parameters.build {
            append("name", "Test {Feature}")
        }

        // Act
        val response = client.post("/app/worlds/1/{features}") {
            header("Cookie", "jwt=test_token")
            setBody(FormDataContent(input))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("notice--success"))
    }

    @Test
    fun `should reject {feature} with missing name`() = testApplication {
        // Test validation failure
    }
}
```

## Step 7: Add HTMX Integration (in templates)

```kotlin
// Form for creating
form {
    id = "create-{feature}-form"
    hxPost("/app/worlds/${worldId}/{features}")
    hxTarget("#create-{feature}-form")

    input(classes = "form-control") { name = "name" }
    button(classes = "btn btn--action") { +"Create" }
}

// Delete button
button(classes = "btn btn--danger") {
    hxDeleteWithConfirm(
        url = "/app/worlds/${worldId}/{features}/${featureId}",
        title = "Delete {Feature}",
        description = "Are you sure?",
        warning = "This cannot be undone."
    )
    +"Delete"
}
```

## Checklist

- [ ] Handler function created
- [ ] Validation step created
- [ ] Business logic step(s) created
- [ ] Route registered
- [ ] Authorization plugins installed (if needed)
- [ ] Tests written and passing
- [ ] HTMX attributes added to templates
- [ ] `mvn clean compile` passes
- [ ] `mvn test` passes
