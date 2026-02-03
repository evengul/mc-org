# Documentation Verification Findings

**Date:** 2026-02-03
**Purpose:** Verify AI documentation accuracy against actual implementation
**Status:** Complete

---

## Executive Summary

This document compares the AI documentation (located in `/documentation/ai/` and `/.claude/commands/`) against the
actual implementation. The verification identified several discrepancies ranging from missing features to incorrect API
signatures.

### Key Findings

| Category                | Items Verified | Issues Found | Severity |
|-------------------------|----------------|--------------|----------|
| Pipeline Pattern        | 5              | 2            | Medium   |
| Step Interface          | 3              | 0            | None     |
| Result Type             | 8              | 1            | Low      |
| ParallelPipelineBuilder | 1              | 1            | High     |
| SafeSQL                 | 5              | 0            | None     |
| DatabaseSteps           | 4              | 1            | Medium   |
| AppFailure Hierarchy    | 4              | 1            | Low      |
| ValidationFailure       | 6              | 0            | None     |
| ApiConfig/ApiProvider   | 1              | 1            | High     |
| Handler Pattern         | 3              | 1            | Medium   |
| Authorization Plugins   | 4              | 1            | Low      |
| HTMX Helpers            | 8              | 2            | Low      |

**Overall Assessment:** Documentation is largely accurate but missing several important features that have been added to
the codebase. Priority should be given to documenting `ParallelPipelineBuilder`, `executePipeline()` helper, and the API
configuration system.

---

## Detailed Verification Results

### 1. Pipeline Pattern

**Documentation Location:** DEVELOPMENT_GUIDE.md, CLAUDE.md
**Code Location:** `domain/pipeline/Pipeline.kt`, `PipelineBuilder.kt`

#### Verified Correct

- [x] `Pipeline.create<E, I>()` factory method exists
- [x] `.pipe(step)` method chains steps correctly
- [x] `.fold(input, onSuccess, onFailure)` method exists and logs errors automatically
- [x] Short-circuit behavior on first failure

#### Discrepancies Found

| Issue                        | Documented     | Actual                                                                                                                               | Recommendation                                |
|------------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| **PipelineBuilder DSL**      | Not documented | `pipeline<I, E>()` top-level function exists with fluent API: `step()`, `map()`, `value()`, `transform()`, `validate()`, `execute()` | **Add section** on PipelineBuilder fluent DSL |
| **executePipeline() helper** | Not documented | `ApplicationCall.executePipeline()` extension function in `PipelineExecutor.kt`                                                      | **Add to handler patterns**                   |

#### Recommended Documentation Update

Add to DEVELOPMENT_GUIDE.md:

```kotlin
// New: PipelineBuilder Fluent DSL
pipeline<Parameters, AppFailure>()
    .step(ValidateInputStep)
    .map { it.name.uppercase() }
    .validate(AppFailure.customValidationError("name", "Cannot be empty")) { it.isNotEmpty() }
    .step(CreateEntityStep)
    .execute(input, onSuccess = { ... }, onFailure = { ... })
```

---

### 2. Step Interface

**Documentation Location:** DEVELOPMENT_GUIDE.md, add-step.md
**Code Location:** `domain/pipeline/Step.kt`

#### Verified Correct

- [x] `Step<I, E, S>` interface with `process(input: I): Result<E, S>`
- [x] `Step.value(value)` factory method
- [x] `Step.validate(error, predicate)` factory method
- [x] All 9 step patterns in add-step.md are valid

#### No Discrepancies Found

Documentation accurately reflects implementation.

---

### 3. Result Type

**Documentation Location:** DEVELOPMENT_GUIDE.md, GLOSSARY.md
**Code Location:** `domain/pipeline/Result.kt`

#### Verified Correct

- [x] `Result<E, S>` sealed interface with `Success<S>` and `Failure<E>`
- [x] `Result.success(value)` and `Result.failure(error)` factory methods
- [x] `Result.tryCatch(errorMapper, block)` factory method
- [x] `.map()`, `.flatMap()`, `.mapError()` methods (suspend)
- [x] `.getOrNull()`, `.getOrThrow()` methods
- [x] `.fold(onSuccess, onFailure)` method

#### Discrepancies Found

| Issue                  | Documented     | Actual                                                                                | Recommendation           |
|------------------------|----------------|---------------------------------------------------------------------------------------|--------------------------|
| **Additional methods** | Not documented | `.peek()`, `.recover()`, `.flatMapSuccess()`, `.mapSuccess()`, `.errorOrNull()` exist | **Add to API reference** |

#### Recommended Documentation Update

Add to DEVELOPMENT_GUIDE.md Result section:

```kotlin
// Additional Result methods
result.peek { value -> /* side effect */ }  // Execute side effect on success
result.recover { error -> /* new Result */ }  // Attempt recovery from failure
result.errorOrNull()  // Extract error or null
```

---

### 4. ParallelPipelineBuilder

**Documentation Location:** Not documented
**Code Location:** `domain/pipeline/ParallelPipelineBuilder.kt`

#### Critical Finding: Feature Not Documented

The `ParallelPipelineBuilder` is a significant feature that enables DAG-based parallel pipeline execution with
dependency resolution. It is **completely undocumented**.

**Actual API:**

```kotlin
// Top-level builder function
fun <E> parallelPipeline(block: ParallelPipelineBuilder<E>.() -> PipelineRef<*>): Pipeline<Unit, E, Any>

// ParallelPipelineBuilder methods
class ParallelPipelineBuilder<E> {
    fun <I, O> singleStep(id: String, input: I, step: Step<I, E, O>): PipelineRef<O>
    fun <I, O> pipeline(id: String, input: I, pipeline: Pipeline<I, E, O>): PipelineRef<O>
    fun <I, O> pipe(id: String, dependency: PipelineRef<I>, pipeline: Pipeline<I, E, O>): PipelineRef<O>
    fun <O> merge(
        id: String,
        dependencies: List<PipelineRef<*>>,
        merger: suspend (Map<String, Any>) -> Result<E, O>
    ): PipelineRef<O>
    fun <A, B, O> merge(
        id: String,
        depA: PipelineRef<A>,
        depB: PipelineRef<B>,
        merger: suspend (A, B) -> Result<E, O>
    ): PipelineRef<O>
    fun <A, B, C, O> merge(
        id: String,
        depA: PipelineRef<A>,
        depB: PipelineRef<B>,
        depC: PipelineRef<C>,
        merger: suspend (A, B, C) -> Result<E, O>
    ): PipelineRef<O>
}

// PipelineRef - reference to a node
data class PipelineRef<T>(val id: String)
```

**Usage in handlers (e.g., HomeHandler.kt):**

```kotlin
executeParallelPipeline(
    onSuccess = { (invitations, worlds) ->
        respondHtml(homePage(user, invitations, worlds))
    }
) {
    val invitationsRef = singleStep("invitations", user.id, GetUserInvitationsStep)
    val worldsRef = singleStep("worlds", GetPermittedWorldsInput(userId = user.id), GetPermittedWorldsStep)
    merge("homeData", invitationsRef, worldsRef) { invitations, worlds ->
        Result.success(Pair(invitations, worlds))
    }
}
```

#### Recommendation: HIGH PRIORITY

Create new section in DEVELOPMENT_GUIDE.md for Parallel Pipelines covering:

1. When to use parallel vs sequential pipelines
2. `singleStep()` for registering independent operations
3. `merge()` for combining results with type-safe lambdas
4. `pipe()` for chaining dependent operations
5. `executeParallelPipeline()` handler helper

---

### 5. SafeSQL

**Documentation Location:** DEVELOPMENT_GUIDE.md, CLAUDE.md
**Code Location:** `pipeline/SafeSQL.kt`

#### Verified Correct

- [x] `SafeSQL.select(query)` factory method
- [x] `SafeSQL.insert(query)` factory method
- [x] `SafeSQL.update(query)` factory method
- [x] `SafeSQL.delete(query)` factory method
- [x] `SafeSQL.with(query)` factory method for CTEs
- [x] Private constructor prevents direct instantiation
- [x] SQL injection protection via pattern validation

#### Additional Security Features (documented but confirmed)

- Blocks dangerous patterns: `;`, `/*`, `*/`, `xp_`, `sp_`, `exec`, `execute`
- Blocks DDL keywords as whole words: `drop`, `alter`, `create`, `truncate`, `grant`, `revoke`

#### No Discrepancies Found

Documentation accurately reflects implementation.

---

### 6. DatabaseSteps

**Documentation Location:** DEVELOPMENT_GUIDE.md, add-step.md
**Code Location:** `pipeline/DatabaseSteps.kt`

#### Verified Correct

- [x] `DatabaseSteps.query<I, S>(sql, parameterSetter, resultMapper, transactionConnection?)` method
- [x] `DatabaseSteps.update<I>(sql, parameterSetter, transactionConnection?)` method
- [x] `DatabaseSteps.transaction<I, S>(step)` method with auto-rollback
- [x] `TransactionConnection` wrapper class

#### Discrepancies Found

| Issue                    | Documented                          | Actual                                                                                                              | Recommendation               |
|--------------------------|-------------------------------------|---------------------------------------------------------------------------------------------------------------------|------------------------------|
| **batchUpdate()**        | Not documented                      | `DatabaseSteps.batchUpdate<I>(sql, parameterSetter, chunkSize?, transactionConnection?)` exists for bulk operations | **Add to database patterns** |
| **update() return type** | Documented as `Int` (affected rows) | Returns Int but: with RETURNING clause returns first column value; without RETURNING returns affected rows          | **Clarify behavior**         |

#### Recommended Documentation Update

Add to DEVELOPMENT_GUIDE.md:

```kotlin
// Batch operations for bulk inserts/updates
val batchStep = DatabaseSteps.batchUpdate<ItemInput>(
    sql = SafeSQL.insert("INSERT INTO items (name, quantity) VALUES (?, ?)"),
    parameterSetter = { stmt, input ->
        stmt.setString(1, input.name)
        stmt.setInt(2, input.quantity)
    },
    chunkSize = 500  // Default, processes in batches
)
batchStep.process(listOf(item1, item2, item3))

// Note: DatabaseSteps.update() with RETURNING clause returns the first column value (as Int)
// Without RETURNING, returns affected row count
```

---

### 7. AppFailure Hierarchy

**Documentation Location:** DEVELOPMENT_GUIDE.md, GLOSSARY.md, CLAUDE.md
**Code Location:** `pipeline/failure/AppFailure.kt`

#### Verified Correct

- [x] `AppFailure.AuthError` with variants: `NotAuthorized`, `MissingToken`, `CouldNotCreateToken`, `ConvertTokenError`
- [x] `AppFailure.DatabaseError` with variants: `ConnectionError`, `StatementError`, `IntegrityConstraintError`,
  `ResultMappingError`, `UnknownError`, `NotFound`
- [x] `AppFailure.ApiError` with variants: `NetworkError`, `TimeoutError`, `RateLimitExceeded`, `HttpError`,
  `SerializationError`, `UnknownError`
- [x] `AppFailure.ValidationError(errors: List<ValidationFailure>)`
- [x] `AppFailure.Redirect(path, queryParameters)`

#### Discrepancies Found

| Issue                           | Documented                   | Actual                                                                                                             | Recommendation                 |
|---------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------|
| **Additional types**            | Only common types documented | `IllegalConfigurationError(reason)`, `FileError(source)`, `DatabaseError.NoIdReturned` exist                       | **Add to hierarchy table**     |
| **ConvertTokenError factories** | Not documented               | Has factory methods: `invalidToken()`, `expiredToken()`, `missingClaim()`, `incorrectClaim()`, `conversionError()` | **Document for auth handling** |

#### Recommended Documentation Update

Update AppFailure table in CLAUDE.md:

```markdown
| Type                                    | Use Case                        |
|-----------------------------------------|---------------------------------|
| `AppFailure.ValidationError`            | Input validation failures       |
| `AppFailure.DatabaseError.NotFound`     | Entity not found                |
| `AppFailure.DatabaseError.NoIdReturned` | INSERT didn't return ID         |
| `AppFailure.AuthError.NotAuthorized`    | Permission denied               |
| `AppFailure.ApiError`                   | External API failures           |
| `AppFailure.Redirect`                   | Redirect response               |
| `AppFailure.IllegalConfigurationError`  | Bad configuration               |
| `AppFailure.FileError`                  | File operation failures         |
```

---

### 8. ValidationFailure Types

**Documentation Location:** DEVELOPMENT_GUIDE.md, CLAUDE.md
**Code Location:** `pipeline/failure/ValidationFailure.kt`

#### Verified Correct

- [x] `MissingParameter(parameterName)`
- [x] `InvalidFormat(parameterName, message?)`
- [x] `OutOfRange(parameterName, min?, max?)`
- [x] `InvalidLength(parameterName, minLength?, maxLength?)`
- [x] `InvalidValue(parameterName, allowedValues?)`
- [x] `CustomValidation(parameterName, message)`

#### No Discrepancies Found

Documentation accurately reflects implementation. CLAUDE.md only lists 4 types but DEVELOPMENT_GUIDE.md has all 6.

---

### 9. ApiConfig & ApiProvider

**Documentation Location:** Not documented
**Code Location:** `config/ApiConfig.kt`, `config/ApiProvider.kt`, `config/AppConfig.kt`

#### Critical Finding: Feature Not Documented

The entire API configuration and provider system is **completely undocumented**. This is a significant feature for
external API integration.

**Actual API:**

```kotlin
// ApiConfig sealed class - base for all API configurations
sealed class ApiConfig(internal val baseUrl: String) {
    abstract fun getContentType(): ContentType
    open fun acceptContentType(): ContentType = ContentType.Application.Json
    open fun getUserAgent(): String? = null
    fun useFakeProvider(responses: (HttpMethod, String) -> Result<AppFailure.ApiError, String>)
    fun resetProvider()
    fun getProvider(): ApiProvider
}

// Concrete API configurations
object ModrinthApiConfig : ApiConfig(AppConfig.modrinthBaseUrl)
object MicrosoftLoginApiConfig : ApiConfig(AppConfig.microsoftLoginBaseUrl)
object XboxAuthApiConfig : ApiConfig(AppConfig.xboxAuthBaseUrl)
object XstsAuthorizationApiConfig : ApiConfig(AppConfig.xstsAuthBaseUrl)
object MinecraftApiConfig : ApiConfig(AppConfig.minecraftBaseUrl)
object FabricMcApiConfig : ApiConfig(AppConfig.fabricMcBaseUrl)
object GithubGistsApiConfig : ApiConfig(AppConfig.githubGistsBaseUrl)

// ApiProvider sealed class - executes API requests
sealed class ApiProvider(protected val config: ApiConfig) {
    inline fun <I, reified S> get(url: String, headerBuilder: ...): Step<I, AppFailure.ApiError, S>
    fun <I> getRaw(url: String, headerBuilder: ...): Step<I, AppFailure.ApiError, InputStream>
    inline fun <I, reified S> post(url: String, headerBuilder: ..., bodyBuilder: ...): Step<I, AppFailure.ApiError, S>
}

// DefaultApiProvider - real HTTP with rate limiting
class DefaultApiProvider(config: ApiConfig) : ApiProvider(config) {
    // Built-in rate limiting via X-Ratelimit-* headers
    // Timeouts: request=30s, connect=10s, socket=30s
}

// FakeApiProvider - for testing
class FakeApiProvider<S>(config: ApiConfig, getResponseBody: ...) : ApiProvider(config)
```

**External APIs integrated:**

1. Modrinth - Minecraft mod repository
2. Microsoft OAuth - User authentication
3. Xbox Live - Xbox authentication
4. XSTS - Xbox Secure Token Service
5. Minecraft Services - Official Minecraft API
6. Fabric MC - Modding framework
7. GitHub Gists - Server JAR downloads

#### Recommendation: HIGH PRIORITY

Create new section in DEVELOPMENT_GUIDE.md for External API Integration:

1. ApiConfig pattern for new API integrations
2. Using providers in pipelines
3. Rate limiting behavior
4. Testing with FakeApiProvider
5. Error handling for API failures

---

### 10. Handler Pattern

**Documentation Location:** DEVELOPMENT_GUIDE.md, add-endpoint.md
**Code Location:** `presentation/handler/*.kt`, `presentation/handler/PipelineExecutor.kt`

#### Verified Correct

- [x] Extension function pattern: `suspend fun ApplicationCall.handleXxx()`
- [x] `respondHtml(createHTML().div { ... })` for responses
- [x] GET returns full pages via `createPage()`
- [x] PUT/POST/PATCH/DELETE return HTML fragments

#### Discrepancies Found

| Issue                         | Documented     | Actual                                                                                                        | Recommendation                           |
|-------------------------------|----------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------|
| **executePipeline() helper**  | Not documented | `ApplicationCall.executePipeline()` extension function handles pipeline execution with default error handling | **Critical: Add to handler pattern**     |
| **executeParallelPipeline()** | Not documented | `ApplicationCall.executeParallelPipeline()` for parallel operations                                           | **Add alongside parallel pipeline docs** |

**Actual handler pattern in codebase:**

```kotlin
suspend fun ApplicationCall.handleCreateWorld() {
    val parameters = receiveParameters()
    val user = getUser()

    executePipeline(
        onSuccess = { world ->
            respondHtml(createHTML().div {
                worldCard(world)
            })
        }
    ) {
        value(parameters)
            .step(ValidateWorldInputStep)
            .step(CreateWorldStep(user))
    }
}
```

#### Recommended Documentation Update

Update add-endpoint.md and DEVELOPMENT_GUIDE.md handler sections to use `executePipeline()` pattern instead of manual
`Pipeline.create().fold()`.

---

### 11. Authorization Plugins

**Documentation Location:** DEVELOPMENT_GUIDE.md, ARCHITECTURE_REFERENCE.md
**Code Location:** `presentation/plugins/RolePlugins.kt`

#### Verified Correct

- [x] `AdminPlugin` - Checks superadmin role, returns 404 if not
- [x] `WorldAdminPlugin` - Checks ADMIN role in world, returns 403 if not
- [x] `BannedPlugin` - Checks global ban status, returns 403 if banned
- [x] Plugin chain order documented correctly

#### Discrepancies Found

| Issue              | Documented     | Actual                                                                 | Recommendation         |
|--------------------|----------------|------------------------------------------------------------------------|------------------------|
| **DemoUserPlugin** | Not documented | `DemoUserPlugin` blocks non-GET requests from demo users in production | **Add to plugin list** |

#### Recommended Documentation Update

Add to plugin documentation:

```kotlin
// DemoUserPlugin - restricts demo users in production
val DemoUserPlugin = createRouteScopedPlugin("DemoUserPlugin") {
    // In production, demo users can only make GET/OPTIONS requests
    // Prevents demo accounts from modifying data
}
```

---

### 12. HTMX Helpers

**Documentation Location:** DEVELOPMENT_GUIDE.md, CLAUDE.md
**Code Location:** `presentation/hx.kt`, `presentation/utils/htmxResponseUtils.kt`

#### Verified Correct

- [x] `hxGet(value)`, `hxPost(value)`, `hxPut(value)`, `hxPatch(value)`
- [x] `hxTarget(value)`, `hxSwap(value)`
- [x] `hxDeleteWithConfirm(url, title, description?, warning?, confirmText?)`
- [x] `hxTrigger(value)`, `hxIndicator(value)`

#### Discrepancies Found

| Issue                | Documented        | Actual                                                                             | Recommendation           |
|----------------------|-------------------|------------------------------------------------------------------------------------|--------------------------|
| **hxInclude()**      | Not documented    | `hxInclude(selector)` includes additional form data                                | **Add to HTMX helpers**  |
| **hxOutOfBands()**   | Mentioned briefly | `hxOutOfBands(locator)` for out-of-band swaps - critical for error handling        | **Add example usage**    |
| **hxErrorTarget()**  | Not documented    | `hxErrorTarget(target)` and `hxErrorTarget(target, errorCode)` for error targeting | **Add to helpers**       |
| **hxExtension()**    | Not documented    | `hxExtension(value)` for HTMX extensions                                           | **Add to helpers**       |
| **Response helpers** | Not documented    | `ApplicationCall.clientRedirect(path)`, `respondBadRequest()`, `respondNotFound()` | **Add response section** |

#### Recommended Documentation Update

Add to HTMX section:

```kotlin
// Additional HTMX helpers
form {
    hxInclude("[name='extra-field']")  // Include fields from outside the form
}

div {
    hxOutOfBands("true")  // Out-of-band swap (replaces element elsewhere in DOM)
}

button {
    hxErrorTarget("#error-container", "422")  // Target specific element on 422 error
}

// Response helpers in handlers
clientRedirect("/app/worlds")  // HTMX redirect (sets HX-Redirect header)
respondBadRequest("Error message", "#error-target")  // 400 with HX-ReTarget
respondNotFound("Not found", "#error-target")  // 404 with HX-ReTarget
```

---

## Summary of Required Updates

### Priority 1: High (Missing Critical Features)

1. **ParallelPipelineBuilder Documentation**
    - Location: New section in DEVELOPMENT_GUIDE.md
    - Content: Full API reference, usage patterns, when to use
    - Files affected: DEVELOPMENT_GUIDE.md, add-endpoint.md

2. **executePipeline() Handler Helper**
    - Location: Update handler pattern section
    - Content: Replace manual Pipeline.fold() with executePipeline()
    - Files affected: DEVELOPMENT_GUIDE.md, CLAUDE.md, add-endpoint.md

3. **ApiConfig/ApiProvider System**
    - Location: New section in DEVELOPMENT_GUIDE.md
    - Content: Full documentation of API integration patterns
    - Files affected: DEVELOPMENT_GUIDE.md, ARCHITECTURE_REFERENCE.md

### Priority 2: Medium (Missing Features)

4. **PipelineBuilder Fluent DSL**
    - Add section documenting `pipeline<I, E>()` builder function
    - Document `.step()`, `.map()`, `.value()`, `.validate()`, `.transform()`, `.execute()` methods

5. **DatabaseSteps.batchUpdate()**
    - Add documentation for bulk operations
    - Include chunkSize parameter explanation

6. **DemoUserPlugin**
    - Add to authorization plugins list
    - Explain production-only behavior

### Priority 3: Low (Enhancements)

7. **Additional Result Methods**
    - Document `.peek()`, `.recover()`, `.flatMapSuccess()`, `.mapSuccess()`, `.errorOrNull()`

8. **Additional AppFailure Types**
    - Add `IllegalConfigurationError`, `FileError`, `DatabaseError.NoIdReturned`
    - Document `ConvertTokenError` factory methods

9. **Additional HTMX Helpers**
    - Document `hxInclude()`, `hxOutOfBands()`, `hxErrorTarget()`, `hxExtension()`
    - Add response helpers section: `clientRedirect()`, `respondBadRequest()`, `respondNotFound()`

---

## Skill Template Updates Needed

### add-endpoint.md

**Current pattern uses:**

```kotlin
val pipeline = Pipeline.create<AppFailure, Parameters>()
    .pipe(ValidateInputStep)
    .pipe(CreateProjectStep(user, worldId))

pipeline.fold(
    input = parameters,
    onSuccess = { ... },
    onFailure = { ... }
)
```

**Should use:**

```kotlin
executePipeline(
    onSuccess = { result ->
        respondHtml(createHTML().div { ... })
    }
) {
    value(parameters)
        .step(ValidateInputStep)
        .step(CreateProjectStep(user, worldId))
}
```

### add-step.md

- Add note about using steps with `executePipeline()` helper
- Add section on steps for parallel pipeline usage

### add-migration.md

- No changes needed - accurately reflects migration patterns

---

## Verification Checklist Summary

| Section                 | Status       | Notes                                      |
|-------------------------|--------------|--------------------------------------------|
| Pipeline Pattern        | Needs Update | Add PipelineBuilder, executePipeline()     |
| Step Interface          | Verified     | Accurate                                   |
| Result Type             | Minor Update | Add additional methods                     |
| ParallelPipelineBuilder | Missing      | Create new section                         |
| SafeSQL                 | Verified     | Accurate                                   |
| DatabaseSteps           | Needs Update | Add batchUpdate(), clarify update() return |
| AppFailure Hierarchy    | Minor Update | Add missing types                          |
| ValidationFailure       | Verified     | Accurate                                   |
| ApiConfig/ApiProvider   | Missing      | Create new section                         |
| Handler Pattern         | Needs Update | Use executePipeline()                      |
| Authorization Plugins   | Minor Update | Add DemoUserPlugin                         |
| HTMX Helpers            | Needs Update | Add missing helpers and response functions |

---

## Appendix: Files to Modify

### Documentation Files

1. `/documentation/ai/DEVELOPMENT_GUIDE.md`
    - Add ParallelPipelineBuilder section
    - Add PipelineBuilder DSL section
    - Add ApiConfig/ApiProvider section
    - Update handler pattern to use executePipeline()
    - Add batchUpdate() to DatabaseSteps
    - Add additional Result methods
    - Add additional HTMX helpers

2. `/documentation/ai/ARCHITECTURE_REFERENCE.md`
    - Add external API integrations overview
    - Update data flow to show parallel execution

3. `/documentation/ai/GLOSSARY.md`
    - Add: ParallelPipelineBuilder, PipelineBuilder, ApiConfig, ApiProvider, PipelineRef
    - Add: executePipeline, executeParallelPipeline

4. `/.claude/commands/add-endpoint.md`
    - Replace Pipeline.create().fold() with executePipeline() pattern
    - Add parallel pipeline example

5. `/.claude/commands/add-step.md`
    - Add note about executePipeline() usage
    - Add parallel pipeline step example

6. `/CLAUDE.md`
    - Update handler example to use executePipeline()
    - Add missing AppFailure types to table
    - Add missing HTMX helpers

---

*End of verification findings document*
