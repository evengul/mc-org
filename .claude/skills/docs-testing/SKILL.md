---
name: docs-testing
description: Test patterns reference for MC-ORG. Covers testApplication setup, auth mocking via WithUser, DatabaseTestExtension, request/assertion patterns, and test file organization.
disable-model-invocation: true
---

# Testing Reference

## File Organization

```
src/test/kotlin/app/mcorg/
├── test/                          # Shared utilities
│   ├── WithUser.kt                # Base class for auth — extend this
│   ├── postgres/
│   │   └── DatabaseTestExtension.kt  # JUnit 5 extension (TestContainers)
│   ├── fixtures/
│   │   └── TestDataFactory.kt     # Object factories
│   └── utils/
│       └── TestUtils.kt           # Pipeline/Result assertion helpers
├── domain/                        # Unit tests (no DB, no HTTP)
├── presentation/handler/          # Integration tests (*IT.kt)
└── nbt/                           # Minecraft file format tests
```

**Naming:** `*IT.kt` = integration test (uses `testApplication` + DB). `*Test.kt` = unit test.

---

## Integration Test Setup

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class MyFeatureIT : WithUser() {

    @Test
    fun `should do something`() = testApplication {
        val client = setup()
        val response = client.get("/app/some-path") {
            addAuthCookie(this)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            install(BannedPlugin)
            // install(WorldParamPlugin) etc. as needed
            route("/app") {
                get { call.respondText("content") }
            }
        }
        return createClient { followRedirects = false }
    }
}
```

---

## WithUser — Auth Helper Base Class

Extend `WithUser` to get:

```kotlin
// Default test user (lazy, created once per class)
val user: TokenProfile  // id, uuid, minecraftUsername, roles

// Create additional users with optional roles
val admin = createExtraUser("superadmin")
val banned = createExtraUser("banned")

// Add auth cookie to any request builder
client.get("/path") {
    addAuthCookie(this)            // uses default user
    addAuthCookie(this, admin)     // uses specific user
}
```

---

## HTTP Requests

```kotlin
// GET
val response = client.get("/app/worlds/1") {
    addAuthCookie(this)
}

// POST with form data
val response = client.post("/app/worlds/1/projects") {
    addAuthCookie(this)
    contentType(ContentType.Application.FormUrlEncoded)
    setBody(Parameters.build {
        append("name", "My Project")
        append("description", "Description text")
    }.formUrlEncode())
}

// DELETE
val response = client.delete("/app/worlds/1/projects/42") {
    addAuthCookie(this)
}
```

---

## Assertions

```kotlin
// Status codes
assertEquals(HttpStatusCode.OK, response.status)
assertEquals(HttpStatusCode.Found, response.status)       // redirect
assertEquals(HttpStatusCode.Forbidden, response.status)   // 403
assertEquals(HttpStatusCode.NotFound, response.status)    // 404

// Body
val body = response.bodyAsText()
assertContains(body, "notice--success")
assertContains(body, "Project created")

// Redirect location
assertEquals("/app/worlds/1", response.headers["Location"])
```

---

## Result / Pipeline Assertions

```kotlin
// From TestUtils
val value = TestUtils.executeAndAssertSuccess(MyStep, input)
val error = TestUtils.executeAndAssertFailure<ValidationError>(MyStep, badInput)

// Manual
when (val result = MyStep.process(input)) {
    is Result.Success -> assertEquals(expected, result.value)
    is Result.Failure -> fail("Expected success, got: ${result.error}")
}
```

---

## Database in Tests

Tests use **TestContainers** (PostgreSQL). Flyway migrations run automatically before tests.

```kotlin
// Clean between tests if needed
DatabaseTestExtension.cleanDatabase()

// Direct SQL (use sparingly — prefer pipeline steps)
DatabaseTestExtension.executeSQL("""
    INSERT INTO world (name, version, created_at) VALUES ('Test', '1.20.4', NOW())
""")

// Via DatabaseSteps (preferred)
DatabaseSteps.update<Unit>(
    sql = SafeSQL.insert("INSERT INTO world (name, version, created_at) VALUES (?, ?, NOW())"),
    parameterSetter = { stmt, _ ->
        stmt.setString(1, "Test World")
        stmt.setString(2, "1.20.4")
    }
).process(Unit)
```

---

## Common Pitfalls

- **`followRedirects = false`** — always set this so you can assert on 302 responses
- **`@TestInstance(PER_CLASS)`** — required when using `WithUser` to share the lazy `user` instance
- **`@ExtendWith(DatabaseTestExtension::class)`** — required for any test that touches the DB or auth (auth requires the DB)
- **Install only the plugins your test needs** — don't blindly install everything or tests become opaque
- **`addAuthCookie(this)`** takes the `HttpRequestBuilder` as receiver — pass `this` inside the request block
