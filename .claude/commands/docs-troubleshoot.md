# Troubleshooting Guide

Solutions to common errors in MC-ORG.

---

## Compilation Errors

### "Cannot resolve symbol 'createHTML'" or wrong HTML output

```kotlin
// WRONG
import kotlinx.html.createHTML

// RIGHT
import kotlinx.html.stream.createHTML
```

### Type mismatch: Result<Never, T> vs Result<AppFailure, T>

```kotlin
// Fix: change Step error type to Nothing (subtype of all types)
object SimpleStep : Step<Input, Nothing, Output> {
    override suspend fun process(input: Input): Result<Nothing, Output> =
        Result.success(output)
}

// Or use AppFailure to match pipeline's error type
object SimpleStep : Step<Input, AppFailure, Output> {
    override suspend fun process(input: Input): Result<AppFailure, Output> =
        Result.success(output)
}
```

### "Cannot find constructor" or SafeSQL error

```kotlin
// WRONG — constructor is private
val sql = SafeSQL("SELECT * FROM users")

// RIGHT — use factory methods
val sql = SafeSQL.select("SELECT * FROM users WHERE id = ?")
// Other: SafeSQL.insert(), .update(), .delete(), .with()
```

### "Unresolved reference: respondHtml / getUser / getWorldId"

```kotlin
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondNotFound
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.clientRedirect
```

### "Smart cast is impossible, mutable property"

```kotlin
// WRONG
if (value != null) { useValue(value) }  // smart cast fails

// RIGHT — assign to local val first
val safeValue = value
if (safeValue != null) { useValue(safeValue) }

// OR — use safe call
value?.let { useValue(it) }
```

---

## Runtime Errors

### NullPointerException in handler

```kotlin
// WRONG — getOrNull() without null check
val result = someStep.process(input).getOrNull()
result.someProperty  // NPE!

// RIGHT — early return on failure
val result = someStep.process(input).getOrElse { return Result.failure(it) }

// OR for nullable DB result
val user = queryStep.process(id).getOrElse { return Result.failure(it) }
    ?: return Result.failure(AppFailure.DatabaseError.NotFound)
```

### SQL injection detected at runtime

```kotlin
// WRONG — string interpolation in SQL
val sql = SafeSQL.select("SELECT * FROM users WHERE id = $userId")

// RIGHT — parameterized query
val sql = SafeSQL.select("SELECT * FROM users WHERE id = ?")
stmt.setInt(1, userId)
```

### 403 Forbidden on what should be valid request

Debug steps:
1. Check JWT cookie is present and valid
2. Check `world_members` table: `SELECT * FROM world_members WHERE user_id = ? AND world_id = ?`
3. Check plugin installation order on route — `WorldParamPlugin` must come before `WorldAdminPlugin`
4. Check role hierarchy: OWNER=0, ADMIN=10, MEMBER=100 (lower = higher privilege)
5. Verify user isn't globally banned

### HTMX not updating the page

Checklist:
1. `hxTarget` selector matches the `id` on the response element exactly
2. Response uses correct swap strategy (`outerHTML` vs `innerHTML`)
3. Server returns 2xx status (HTMX ignores 4xx/5xx by default)
4. HTMX library is loaded in the base page template
5. Check browser Network tab for request/response details
6. Check browser console for HTMX errors

```kotlin
// Template
button { hxPost("/..."); hxTarget("#my-element") }

// Response MUST have matching id
respondHtml(createHTML().div {
    id = "my-element"  // must match hxTarget exactly
    // ...
})
```

### Database connection timeout / pool exhausted

1. Don't manually manage connections — use `DatabaseSteps.*` which handles cleanup
2. Look for N+1 queries (querying in a loop) — replace with a JOIN
3. Check for long-running transactions that block connections

---

## Test Failures

### Database state contamination between tests

```kotlin
// Use unique data per test
val uniqueName = "test-world-${UUID.randomUUID()}"

// Or clean before each test
@BeforeEach fun setup() { cleanTestDatabase() }

// Or wrap in transaction that rolls back
DatabaseSteps.transaction {
    // test code — rolled back after test
}
```

### Tests fail with migration errors

```bash
# Recreate test database to sync migrations
dropdb mc_org_test && createdb mc_org_test
mvn test
```

---

## Build Issues

### `mvn clean compile` fails

```bash
# Clear Maven cache (fixes stale/corrupt artifacts)
rm -rf ~/.m2/repository/org/jetbrains/kotlin
mvn clean install

# Increase memory if OOM
export MAVEN_OPTS="-Xmx2048m"
mvn clean compile
```

Check `pom.xml` for: `<kotlin.version>2.1.10</kotlin.version>`

### Flyway migration failure

```sql
-- Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;

-- Find failed migrations
SELECT * FROM flyway_schema_history WHERE success = false;
```

Fix: check migration version numbers are sequential and unique. Backup DB before manual fixes.

---

## Deployment Issues

### 502 Bad Gateway (Fly.io)

```bash
fly logs  # look for stack traces, common causes:
          # - Database connection failed (wrong DB_URL/credentials)
          # - Missing required environment variable
          # - Unhandled exception in handler
```

### Application health check

```bash
fly status
curl https://your-app.fly.dev/test/ping
```
