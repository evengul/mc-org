# Add Pipeline Step

Template for creating a new pipeline Step in MC-ORG.

## Step Interface

```kotlin
interface Step<I, E, S> {
    suspend fun process(input: I): Result<E, S>
}
```

- `I` = Input type
- `E` = Error/Failure type (extends AppFailure)
- `S` = Success type

---

## Pattern 1: Simple Transformation Step

For transforming data without potential failure:

```kotlin
object Transform{Feature}Step : Step<{Input}, Nothing, {Output}> {
    override suspend fun process(input: {Input}): Result<Nothing, {Output}> {
        val output = {Output}(
            // transform input to output
        )
        return Result.success(output)
    }
}
```

---

## Pattern 2: Validation Step

For validating input with potential validation errors:

```kotlin
object Validate{Feature}Step : Step<Parameters, AppFailure.ValidationError, Validated{Input}> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Validated{Input}> {
        val errors = mutableListOf<ValidationFailure>()

        // Required field
        val name = input["name"]
        if (name.isNullOrBlank()) {
            errors.add(ValidationFailure.MissingParameter("name"))
        } else if (name.length < 3 || name.length > 100) {
            errors.add(ValidationFailure.InvalidValue("name", "Must be 3-100 characters"))
        }

        // Optional field with format validation
        val email = input["email"]
        if (!email.isNullOrBlank() && !email.contains("@")) {
            errors.add(ValidationFailure.InvalidFormat("email", "Must be valid email"))
        }

        // Enum validation
        val type = input["type"]
        val validType = type?.let {
            runCatching { {FeatureType}.valueOf(it.uppercase()) }.getOrNull()
        }
        if (type != null && validType == null) {
            errors.add(ValidationFailure.InvalidValue("type", "Must be one of: ${FeatureType.entries.joinToString()}"))
        }

        return if (errors.isEmpty()) {
            Result.success(Validated{Input}(
                name = name!!,
                email = email,
                type = validType ?: {FeatureType}.DEFAULT
            ))
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}

data class Validated{Input}(
    val name: String,
    val email: String?,
    val type: {FeatureType}
)
```

---

## Pattern 3: Database Query Step

For fetching data from the database:

```kotlin
class Get{Feature}ByIdStep(
    private val featureId: Int
) : Step<Unit, AppFailure.DatabaseError, {Feature}> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, {Feature}> {
        val queryStep = DatabaseSteps.query<Unit, {Feature}?>(
            sql = SafeSQL.select("SELECT * FROM {features} WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, featureId)
            },
            resultMapper = { rs ->
                if (rs.next()) rs.mapTo{Feature}() else null
            }
        )

        return queryStep.process(Unit).flatMap { feature ->
            if (feature != null) {
                Result.success(feature)
            } else {
                Result.failure(AppFailure.DatabaseError.NotFound)
            }
        }
    }
}
```

---

## Pattern 4: Database Insert Step

For inserting data and returning the new ID:

```kotlin
class Create{Feature}Step(
    private val userId: Int,
    private val worldId: Int
) : Step<Validated{Input}, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Validated{Input}): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Validated{Input}>(
            sql = SafeSQL.insert("""
                INSERT INTO {features} (name, type, world_id, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { stmt, inp ->
                stmt.setString(1, inp.name)
                stmt.setString(2, inp.type.name)
                stmt.setInt(3, worldId)
                stmt.setInt(4, userId)
            }
        ).process(input)
    }
}
```

---

## Pattern 5: Database Update Step

For updating existing data:

```kotlin
class Update{Feature}Step(
    private val featureId: Int
) : Step<Validated{Input}, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Validated{Input}): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Validated{Input}>(
            sql = SafeSQL.update("""
                UPDATE {features}
                SET name = ?, type = ?, updated_at = NOW()
                WHERE id = ?
            """),
            parameterSetter = { stmt, inp ->
                stmt.setString(1, inp.name)
                stmt.setString(2, inp.type.name)
                stmt.setInt(3, featureId)
            }
        ).process(input)
    }
}
```

---

## Pattern 6: Database Delete Step

For deleting data:

```kotlin
class Delete{Feature}Step(
    private val featureId: Int
) : Step<Unit, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete("DELETE FROM {features} WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, featureId)
            }
        ).process(Unit)
    }
}
```

---

## Pattern 7: Business Rule Validation Step

For checking domain rules:

```kotlin
class ValidateNoCycles{Feature}Step(
    private val parentId: Int,
    private val childId: Int
) : Step<Unit, AppFailure, Unit> {
    override suspend fun process(input: Unit): Result<AppFailure, Unit> {
        val hasCycle = checkForCycle(parentId, childId)

        return if (!hasCycle) {
            Result.success(Unit)
        } else {
            Result.failure(
                AppFailure.ValidationError(listOf(
                    ValidationFailure.CustomValidation(
                        "dependency",
                        "This would create a circular dependency"
                    )
                ))
            )
        }
    }

    private suspend fun checkForCycle(from: Int, to: Int): Boolean {
        // Implement cycle detection logic
        return false
    }
}
```

---

## Pattern 8: Transaction Step

For operations requiring multiple database changes:

```kotlin
class Create{Feature}WithRelatedStep(
    private val userId: Int,
    private val worldId: Int
) : Step<Validated{Input}, AppFailure.DatabaseError, {Feature}> {
    override suspend fun process(input: Validated{Input}): Result<AppFailure.DatabaseError, {Feature}> {
        return DatabaseSteps.transaction<Validated{Input}, {Feature}> { txConn ->
            object : Step<Validated{Input}, AppFailure.DatabaseError, {Feature}> {
                override suspend fun process(inp: Validated{Input}): Result<AppFailure.DatabaseError, {Feature}> {
                    // Step 1: Insert main record
                    val insertStep = DatabaseSteps.update<Validated{Input}>(
                        sql = SafeSQL.insert("""
                            INSERT INTO {features} (name, world_id, created_by, created_at, updated_at)
                            VALUES (?, ?, ?, NOW(), NOW())
                            RETURNING id
                        """),
                        parameterSetter = { stmt, i ->
                            stmt.setString(1, i.name)
                            stmt.setInt(2, worldId)
                            stmt.setInt(3, userId)
                        },
                        transactionConnection = txConn
                    )
                    val id = insertStep.process(inp).getOrElse { return Result.failure(it) }

                    // Step 2: Insert related records
                    inp.relatedItems.forEach { item ->
                        val relatedStep = DatabaseSteps.update<String>(
                            sql = SafeSQL.insert("""
                                INSERT INTO {feature}_items (feature_id, item_name)
                                VALUES (?, ?)
                            """),
                            parameterSetter = { stmt, itemName ->
                                stmt.setInt(1, id)
                                stmt.setString(2, itemName)
                            },
                            transactionConnection = txConn
                        )
                        relatedStep.process(item).getOrElse { return Result.failure(it) }
                    }

                    // Step 3: Fetch complete result
                    val selectStep = DatabaseSteps.query<Int, {Feature}?>(
                        sql = SafeSQL.select("SELECT * FROM {features} WHERE id = ?"),
                        parameterSetter = { stmt, i -> stmt.setInt(1, i) },
                        resultMapper = { rs -> if (rs.next()) rs.mapTo{Feature}() else null },
                        transactionConnection = txConn
                    )
                    val feature = selectStep.process(id).getOrElse { return Result.failure(it) }

                    return if (feature != null) {
                        Result.success(feature)
                    } else {
                        Result.failure(AppFailure.DatabaseError.NotFound)
                    }
                }
            }
        }.process(input)
    }
}
```

---

## Pattern 9: Parameterized Step (Object with Parameters)

For reusable steps with configuration:

```kotlin
class {Feature}QueryStep(
    private val worldId: Int,
    private val filter: {Feature}Filter? = null,
    private val limit: Int = 50
) : Step<Unit, AppFailure.DatabaseError, List<{Feature}>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<{Feature}>> {
        val sql = SafeSQL.select("""
            SELECT * FROM {features}
            WHERE world_id = ?
            ${if (filter?.status != null) "AND status = ?" else ""}
            ORDER BY created_at DESC
            LIMIT ?
        """)

        return DatabaseSteps.query<Unit, List<{Feature}>>(
            sql = sql,
            parameterSetter = { stmt, _ ->
                var idx = 1
                stmt.setInt(idx++, worldId)
                filter?.status?.let { stmt.setString(idx++, it.name) }
                stmt.setInt(idx, limit)
            },
            resultMapper = { rs ->
                val results = mutableListOf<{Feature}>()
                while (rs.next()) {
                    results.add(rs.mapTo{Feature}())
                }
                results
            }
        ).process(Unit)
    }
}

data class {Feature}Filter(
    val status: {Feature}Status? = null
)
```

---

## Using Steps in Pipeline

**Sequential Pipeline (using executePipeline):**

```kotlin
suspend fun ApplicationCall.handle{Action}{Feature}() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = { feature ->
            respondHtml(createHTML().div {
                // success HTML
            })
        }
        // onFailure is optional - default handler responds appropriately
    ) {
        value(parameters)
            .step(Validate{Feature}Step)
            .step(Create{Feature}Step(user.id, worldId))
            .step(Get{Feature}ByIdStep)
    }
}
```

**Parallel Pipeline (for independent steps):**

```kotlin
suspend fun ApplicationCall.handleGet{Feature}Dashboard() {
    val featureId = this.getFeatureId()

    executeParallelPipeline(
        onSuccess = { (feature, relatedItems, stats) ->
            respondHtml(dashboardPage(feature, relatedItems, stats))
        }
    ) {
        val featureRef = singleStep("feature", featureId, Get{Feature}ByIdStep(featureId))
        val relatedRef = singleStep("related", featureId, GetRelatedItemsStep(featureId))
        val statsRef = singleStep("stats", featureId, Get{Feature}StatsStep(featureId))
        merge("dashboard", featureRef, relatedRef, statsRef) { feature, related, stats ->
            Result.success(Triple(feature, related, stats))
        }
    }
}
```

**Note:** Steps are the building blocks used in both patterns. Use `singleStep()` in parallel pipelines when operations are independent and can execute concurrently.

---

## Checklist

- [ ] Step implements `Step<I, E, S>` interface
- [ ] Error type extends `AppFailure`
- [ ] Uses `Result.success()` or `Result.failure()` (not exceptions)
- [ ] Database operations use `SafeSQL` factory methods
- [ ] Transaction used for multi-table operations
- [ ] Input validation collects all errors before returning
- [ ] Unit tests written for step
- [ ] `mvn clean compile` passes
