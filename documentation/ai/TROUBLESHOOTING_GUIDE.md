# MC-ORG Troubleshooting Guide

**Solutions to common errors and issues**

---

## 🚨 Compilation Errors

### "Cannot resolve symbol 'createHTML'"

**Cause**: Wrong import or missing import

**Symptoms**:

```kotlin
// This causes the error:
import kotlinx.html.createHTML
// or missing import entirely
```

**Fix**:

```kotlin
// Use the correct import:
import kotlinx.html.stream.createHTML
```

**Why**: Kotlin HTML DSL has two implementations - `stream` (for server-side) and regular (for browser). We use `stream`
for server-side HTML generation.

---

### "Type mismatch: inferred type is Result<Never, T> but Result<SomeError, T> was expected"

**Cause**: Step's error type is `Never` but the pipeline expects a specific error type

**Symptoms**:

```kotlin
object SimpleStep : Step<Input, Never, Output> {
    override suspend fun process(input: Input): Result<Never, Output> {
        // This can't fail, but pipeline expects ValidationError
    }
}

// Pipeline:
val pipeline = Pipeline.create<AppFailure.ValidationError, Input>()
    .pipe(SimpleStep)  // Type mismatch!
```

**Fix Option 1** - Change Step to use proper error type:

```kotlin
object SimpleStep : Step<Input, AppFailure.ValidationError, Output> {
    override suspend fun process(input: Input): Result<AppFailure.ValidationError, Output> {
        // Handle potential errors properly
        return Result.success(output)
    }
}
```

**Fix Option 2** - Use a common error supertype in pipeline:

```kotlin
val pipeline = Pipeline.create<AppFailure, Input>()  // More general error type
    .pipe(SimpleStep)
```

---

### "Cannot find method 'select' in SafeSQL"

**Cause**: Trying to use SafeSQL constructor instead of factory method

**Symptoms**:

```kotlin
val sql = SafeSQL("SELECT * FROM users")  // Constructor is private!
```

**Fix**:

```kotlin
val sql = SafeSQL.select("SELECT * FROM users")
```

**Factory Methods**:

- `SafeSQL.select("SELECT ...")` - for queries
- `SafeSQL.insert("INSERT ...")` - for inserts
- `SafeSQL.update("UPDATE ...")` - for updates
- `SafeSQL.delete("DELETE ...")` - for deletes
- `SafeSQL.with("WITH ...")` - for CTEs

---

### "Unresolved reference: respondHtml"

**Cause**: Missing import or wrong import

**Fix**:

```kotlin
import app.mcorg.presentation.utils.respondHtml
```

**Related Imports**:

```kotlin
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondNotFound
import app.mcorg.presentation.utils.respondForbidden
```

---

### "Cannot access 'getUser': it is private in 'ApplicationCall'"

**Cause**: Missing import for extension function

**Fix**:

```kotlin
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getProjectId
```

---

### "Smart cast to 'X' is impossible, because 'property' is a mutable property that could have been changed"

**Cause**: Kotlin can't guarantee the value hasn't changed between check and usage

**Symptoms**:

```kotlin
if (value != null) {
    useValue(value)  // Smart cast fails
}
```

**Fix**:

```kotlin
// Option 1: Use safe call
value?.let { useValue(it) }

// Option 2: Assign to local variable
val safeValue = value
if (safeValue != null) {
    useValue(safeValue)
}
```

---

## 💥 Runtime Errors

### "SQL injection pattern detected"

**Cause**: Using SafeSQL constructor or unsafe query patterns

**Symptoms**:

```kotlin
val sql = SafeSQL("SELECT * FROM users WHERE id = $userId")  // String interpolation!
```

**Fix**:

```kotlin
val sql = SafeSQL.select("SELECT * FROM users WHERE id = ?")
// Then use prepared statement:
stmt.setInt(1, userId)
```

**Never**:

- Don't use string interpolation/concatenation
- Don't use SafeSQL constructor
- Don't build dynamic queries without parameterization

---

### "NullPointerException in handler"

**Common Causes**:

**1. Using `.getOrNull()` without null check**:

```kotlin
// Wrong:
val result = someStep.process(input).getOrNull()
result.someProperty  // NPE if result is null!

// Right:
val result = someStep.process(input).getOrElse {
    return Result.failure(it)
}
```

**2. Database query returns null**:

```kotlin
// Wrong:
val user = DatabaseSteps.query(sql, ...).getOrNull()
user.id  // NPE if query returned no results!

// Right:
val user = DatabaseSteps.query(sql, ...)
.getOrElse { return Result.failure(AppFailure.DatabaseError.NotFound) }
```

**3. Missing call attribute**:

```kotlin
// Wrong:
val worldId = call.attributes[WorldParamPlugin.worldIdKey]  // Can be null!

// Right:
val worldId = call.getWorldId()  // Extension function handles nulls
```

---

### "403 Forbidden" on valid request

**Cause**: Authorization plugin rejecting request

**Debug Steps**:

1. **Check JWT token**:

```kotlin
// In handler:
val user = call.getUser()
println("User: ${user.id}, Role: ${user.role}")
```

2. **Check world membership**:

```sql
SELECT *
FROM world_members
WHERE user_id = ?
  AND world_id = ?
```

3. **Check plugin installation**:

```kotlin
// Ensure plugin is installed on route:
authenticate(optional = false) {
    install(WorldAdminPlugin)
    post("/worlds/{worldId}/projects") {
        handleCreateProject()
    }
}
```

4. **Check role hierarchy**:

```kotlin
// OWNER = 0, ADMIN = 10, MEMBER = 100, BANNED = 1000
// Lower number = higher privilege
```

---

### "HTMX not updating the page"

**Cause**: Target ID mismatch or incorrect swap strategy

**Debug Checklist**:

1. **Check hxTarget matches response ID**:

```kotlin
// Button:
button {
    hxPost = "/worlds/123/projects"
    hxTarget = "#projects-list"  // Must match response ID!
}

// Response:
div {
    id = "projects-list"  // IDs must match exactly!
    // content
}
```

2. **Check hx-swap strategy**:

```kotlin
// Default is innerHTML (replaces content)
hxSwap = "outerHTML"  // Replaces entire element
hxSwap = "beforeend"  // Appends to end
hxSwap = "afterbegin"  // Prepends to beginning
```

3. **Check response status code**:

- HTMX only processes 2xx responses by default
- 4xx/5xx require special handling

4. **Check browser console**:

- Look for HTMX error messages
- Verify request/response in Network tab

---

### "Database connection timeout"

**Cause**: Too many open connections or long-running queries

**Symptoms**:

- "Connection pool exhausted"
- Slow response times
- Application hangs

**Fixes**:

1. **Ensure connections are closed**:

```kotlin
// DatabaseSteps handles this automatically
// Don't manually manage connections
```

2. **Check for N+1 query problems**:

```kotlin
// Bad: Query in loop
projects.forEach { project ->
    val tasks = queryTasks(project.id)  // N queries!
}

// Good: Single query with JOIN
val projectsWithTasks = queryProjectsWithTasks(worldId)
```

3. **Add query timeouts**:

```kotlin
val sql = SafeSQL.select("SELECT * FROM large_table")
// Set statement timeout
stmt.queryTimeout = 30  // seconds
```

---

## 🧪 Test Failures

### "TestDataFactory: No such factory method"

**Cause**: Factory method doesn't exist for that entity

**Symptoms**:

```kotlin
val idea = TestDataFactory.createTestIdea()  // Doesn't exist!
```

**Fix**: Check existing factory methods or create new one:

```kotlin
// In TestDataFactory.kt:
fun createTestIdea(
    name: String = "Test Idea",
    category: IdeaCategory = IdeaCategory.FARM,
    // ...
): Idea {
    // Factory implementation
}
```

---

### "Database state contamination between tests"

**Cause**: Tests not cleaning up or running in wrong order

**Symptoms**:

- Tests pass individually but fail when run together
- Duplicate key violations
- Unexpected foreign key errors

**Fixes**:

1. **Use transactions and rollback**:

```kotlin
@Test
fun testSomething() = runTest {
        DatabaseSteps.transaction {
            // Test code
            // Automatically rolled back after test
        }
    }
```

2. **Clean test database before each test**:

```kotlin
@BeforeEach
fun setup() {
    cleanTestDatabase()
}
```

3. **Use unique test data**:

```kotlin
val uniqueName = "test-world-${UUID.randomUUID()}"
```

---

### "Assertion failed: expected X but got Y"

**Debug Steps**:

1. **Add debug output**:

```kotlin
println("Expected: $expected")
println("Actual: $actual")
assertEquals(expected, actual)
```

2. **Check data types**:

```kotlin
// Int vs Long mismatch
val id: Int = 1
val expectedId: Long = 1L
// These won't match!
```

3. **Check object equality**:

```kotlin
// Data classes use structural equality (correct)
data class Project(val id: Int, val name: String)

// Regular classes use reference equality (might fail)
class Project(val id: Int, val name: String)
```

---

## 🔧 Build Issues

### "mvn clean compile" fails

**Common Causes**:

**1. Kotlin version mismatch**:

```xml
<!-- Check pom.xml -->
<kotlin.version>2.1.10</kotlin.version>
```

**2. Missing dependency**:

```bash
# Clear local Maven cache
rm -rf ~/.m2/repository/org/jetbrains/kotlin
mvn clean install
```

**3. Out of memory**:

```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2048m"
mvn clean compile
```

---

### "Tests fail with database migration errors"

**Cause**: Test database out of sync with migrations

**Fix**:

```bash
# Drop and recreate test database
dropdb mc_org_test
createdb mc_org_test

# Migrations will auto-apply on next test run
mvn test
```

---

## 🌐 Deployment Issues

### "Application starts but returns 502"

**Cause**: Application crash during request processing

**Debug**:

```bash
# Check logs
fly logs

# Look for stack traces
# Common issues:
# - Database connection failed
# - Missing environment variable
# - Unhandled exception in handler
```

---

### "Database migrations fail on startup"

**Cause**: Migration version conflict or syntax error

**Debug**:

```sql
-- Check migration history
SELECT *
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

-- Look for failed migrations
SELECT *
FROM flyway_schema_history
WHERE success = false;
```

**Fix**:

```bash
# If migrations are out of order:
# 1. Backup database
# 2. Fix migration version numbers
# 3. Restart application
```

---

## 📚 Additional Resources

- **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** - Development protocols
- **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Implementation patterns
- **[GLOSSARY.md](GLOSSARY.md)** - Term definitions
- **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)** - System architecture

---

**Document Version**: 1.0  
**Last Updated**: January 13, 2026  
**Maintained By**: Development Team

