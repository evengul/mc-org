# AI Integration Specifications & Behavioral Guidelines

## AI Agent Operational Framework

### Safe Modification Boundaries

#### Database Schema Evolution
```yaml
Migration Creation Rules:
  Pattern: V{major}_{minor}_{patch}__{description}.sql
  Location: src/main/resources/db/migration/
  
  Safe Operations:
    ✅ ADD COLUMN with DEFAULT values
    ✅ CREATE TABLE with proper constraints
    ✅ CREATE INDEX for performance
    ✅ ADD FOREIGN KEY constraints
    ✅ INSERT reference data
    
  Dangerous Operations:
    ⚠️ DROP COLUMN (requires consultation)
    ⚠️ ALTER COLUMN TYPE (data loss risk)
    ⚠️ DROP TABLE (requires explicit approval)
    ⚠️ RENAME operations (breaks existing code)

  Rollback Requirements:
    - All migrations must be reversible
    - Test rollback before applying
    - Document rollback procedures
```

#### Code Generation Templates
```kotlin
// Template: New Domain Entity
data class NewEntity(
    val id: Int,
    val name: String,
    val description: String,
    val worldId: Int, // Foreign key pattern
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
) {
    // Validation rules
    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(name.length <= 100) { "Name too long" }
    }
}

// Template: New Handler
class NewFeatureHandler {
    fun Route.newFeatureRoutes() {
        route("/new-feature") {
            get {
                call.handleGetNewFeature()
            }
            post {
                call.handleCreateNewFeature()
            }
            route("/{id}") {
                install(NewFeatureParamPlugin)
                get {
                    call.handleGetNewFeatureDetail()
                }
                put {
                    call.handleUpdateNewFeature()
                }
                delete {
                    call.handleDeleteNewFeature()
                }
            }
        }
    }
}

// Template: New Template Function
fun createNewFeatureTemplate(
    user: TokenProfile,
    data: NewFeatureData,
    options: NewFeatureOptions = NewFeatureOptions()
) = createPage(
    user = user,
    pageTitle = "New Feature"
) {
    classes += "new-feature-page"
    
    newFeatureHeader(data, options)
    newFeatureContent(data)
    newFeatureActions(data, options)
}
```

### Input/Output Format Specifications

#### Request Processing Pipeline
```kotlin
// Actual pattern: Pipeline-based architecture using Step interface
suspend fun ApplicationCall.handleFeatureAction() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = { result: FeatureResult ->
            respondHtml(createHTML().div {
                featureSuccessContent(result)
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        // Pipeline composition using Step interface
        step(Step.value(parameters))
            .step(ValidateInputStep)
            .step(ValidatePermissionsStep(user, worldId))
            .step(ExecuteBusinessLogicStep(worldId))
            .step(GetUpdatedDataStep)
    }
}

// Step implementation pattern
object ValidateInputStep : Step<Parameters, FeatureFailures, ValidatedInput> {
    override suspend fun process(input: Parameters): Result<FeatureFailures, ValidatedInput> {
        // Use ValidationSteps for input validation
        val name = ValidationSteps.required("name", { FeatureFailures.ValidationError(listOf(it)) })
            .process(input)
        
        return if (name is Result.Failure) {
            Result.failure(FeatureFailures.ValidationError(name.error.errors))
        } else {
            Result.success(ValidatedInput(name.getOrNull()!!))
        }
    }
}
```

#### Data Transformation Patterns
```kotlin
// Current Pattern: Work directly with domain models
// Note: Display models may be added in the future, but currently we use domain entities directly

// Current approach - Domain entities used directly in templates
fun createProjectTemplate(
    user: TokenProfile,
    project: Project // Direct use of domain model
): String = createPage(
    user = user,
    pageTitle = project.name
) {
    div("project-details") {
        h1 { +project.name }
        p { +project.description }
        span("project-type") { +project.type.name }
    }
}

// Form validation pattern - Using ValidationSteps, not toCreateRequest
object ValidateProjectInputStep : Step<Parameters, CreateProjectFailures, CreateProjectInput> {
    override suspend fun process(input: Parameters): Result<CreateProjectFailures, CreateProjectInput> {
        val name = ValidationSteps.required("name", { CreateProjectFailures.ValidationError(listOf(it)) })
            .process(input)
        val description = ValidationSteps.optional("description")
            .process(input)
        val type = ValidationSteps.validateCustom<CreateProjectFailures.ValidationError, String?>(
            "type",
            "Invalid project type",
            errorMapper = { CreateProjectFailures.ValidationError(listOf(it)) },
            predicate = { !it.isNullOrBlank() && runCatching { ProjectType.valueOf(it.uppercase()) }.isSuccess }
        ).process(input["type"]).map { ProjectType.valueOf(it!!.uppercase()) }

        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) errors.addAll(name.error.errors)
        if (type is Result.Failure) errors.addAll(type.error.errors)
        
        return if (errors.isNotEmpty()) {
            Result.failure(CreateProjectFailures.ValidationError(errors))
        } else {
            Result.success(CreateProjectInput(name.getOrNull()!!, description.getOrNull() ?: "", type.getOrNull()!!))
        }
    }
}
```

### State Management & Side Effects

#### Transaction Boundaries
```kotlin
// Actual pattern: DatabaseSteps.transaction with Step-based architecture
suspend fun performComplexOperation(request: OperationRequest): OperationResult {
    return DatabaseSteps.transaction(
        step = object : Step<OperationRequest, OperationFailures, OperationResult> {
            override suspend fun process(input: OperationRequest): Result<OperationFailures, OperationResult> {
                // 1. Validate preconditions using pipeline steps
                val existingEntity = FindEntityStep.process(input.entityId)
                if (existingEntity is Result.Failure) {
                    return Result.failure(OperationFailures.EntityNotFound)
                }
                
                // 2. Check business rules
                val validationResult = ValidateBusinessRulesStep.process(input)
                if (validationResult is Result.Failure) {
                    return validationResult
                }
                
                // 3. Perform atomic updates within transaction
                val updateResult = UpdateEntityStep.process(input)
                if (updateResult is Result.Failure) {
                    return updateResult
                }
                
                // 4. Create audit log
                val auditResult = CreateAuditLogStep.process(input)
                if (auditResult is Result.Failure) {
                    return auditResult
                }
                
                // 5. Return successful result
                return Result.success(OperationResult(updateResult.getOrNull()!!, auditResult.getOrNull()!!))
            }
        }
    ).process(request)
}

// DatabaseSteps.transaction automatically handles:
// - Setting connection.autoCommit = false
// - Committing on Result.Success
// - Rolling back on Result.Failure
// - Rolling back on exceptions
// - Proper connection cleanup
```

#### Side Effect Documentation

```
World Creation Side Effects:
  1. Creates world record in database (INSERT INTO world)
  2. Assigns creator as OWNER role in world_members table
  3. Sets createdAt and createdBy fields (audit logging)
  4. No notifications sent (world creator has immediate access)
  5. No background jobs triggered

Project Dependency Addition Side Effects:
  1. Validates no circular dependencies (pre-check only)
  2. Creates dependency record in database
  3. Sets createdAt and createdBy fields (audit logging)
  4. No post-creation side effects or notifications
  5. No background jobs triggered

User Invitation Process Side Effects:
  1. Validates inviter has Admin+ role in world
  2. Validates invitee username exists in database (must have logged in before)
  3. Creates invitation record with specified role
  4. Sets createdAt and createdBy fields (audit logging)
  5. User receives invitation notification (in-app only, no email yet)
  6. If accepted: Creates world_members record with specified role
  7. If accepted: User gains access to world immediately
  8. No background jobs triggered

Background Jobs & Scheduled Tasks:
  - Current: None implemented
  - Planned: Cleanup job to delete worlds with no members (when all members leave)

Audit Logging Pattern:
  Creation:
    - createdAt: Set once at record creation, never updated
    - createdBy: Set once at record creation, never updated
  
  Updates:
    - updatedAt: Updated on every record modification
    - updatedBy: Updated on every record modification
  
  Authentication:
    - minecraft_profiles.last_login: Updated on successful sign-in
```

### Error Recovery & Rollback Procedures

#### Error Handling Hierarchy
```kotlin
// WANTED FUNCTIONALITY - Error type hierarchy does not yet exist but should be implemented
// Currently the application uses Result<E, S> pattern with specific failure types per feature
// This unified error hierarchy would provide better error handling across the application

// There is now an AppFailure sealed interface used everywhere, but it is yet to be documented, and it is not finished.

// Proposed Error Hierarchy (to be implemented):
sealed class ApplicationError(message: String) : Exception(message)

class ValidationError(field: String, message: String) : 
    ApplicationError("Validation failed for $field: $message")

class AuthorizationError(requiredRole: Role) : 
    ApplicationError("Requires $requiredRole role")

class BusinessRuleError(rule: String) : 
    ApplicationError("Business rule violation: $rule")

class SystemError(cause: Throwable) : 
    ApplicationError("System error: ${cause.message}")

// Migration strategy: Gradually unify error types while maintaining pipeline compatibility
```

#### Rollback Strategies
```yaml
Database Migration Rollback:
  1. Stop application
  2. Run flyway:undo for last migration
  3. Verify database state
  4. Restart application
  5. Test critical functionality

Failed Transaction Rollback:
  1. Database transaction auto-rollback
  2. Clear any cached data
  3. Log error for investigation
  4. Return user-friendly error message
  5. Suggest corrective actions

Feature Deployment Rollback:
  1. Revert to previous Git commit
  2. Rebuild and redeploy application
  3. Run database rollback if needed
  4. Verify system functionality
  5. Notify users of temporary issues
```

### Build Tool Integration

#### Maven Build Process
```xml
<!-- Critical build phases for AI agents -->
<build>
    <plugins>
        <!-- Kotlin compilation -->
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <configuration>
                <args>
                    <arg>-Xjsr305=strict</arg>
                </args>
            </configuration>
        </plugin>
        
        <!-- Flyway database migrations -->
        <plugin>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-maven-plugin</artifactId>
            <configuration>
                <url>${flyway.url}</url>
                <user>${flyway.user}</user>
                <password>${flyway.password}</password>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Build Commands for AI Agents
```bash
# Development workflow
mvn clean compile                    # Compile Kotlin code
mvn flyway:migrate                  # Apply database migrations
mvn test                           # Run unit tests
mvn exec:java                      # Start development server

# Testing workflow  
mvn test                           # Kotlin unit tests
cd src/test/javascript && npm test # Playwright E2E tests

# Deployment workflow
mvn clean package                  # Build deployment artifact
docker build -t mcorg-webapp .    # Build container
flyctl deploy                      # Deploy to Fly.io
```

### AI Agent Safety Protocols

#### Pre-Change Validation
```yaml
Before Making Changes:
  1. Read and understand affected code modules
  2. Identify all dependencies and side effects
  3. Check for existing similar patterns
  4. Validate against business rules
  5. Plan rollback strategy

Required Checks:
  - Compilation success
  - Unit test passage
  - Database migration validation
  - No circular dependency creation
  - Role-based access preservation
```

#### Post-Change Verification
```yaml
After Making Changes:
  1. Verify compilation without errors
  2. Run affected unit tests
  3. Test database migration forward/backward
  4. Validate HTML template rendering
  5. Check for security vulnerabilities

Integration Testing:
  - Run Playwright test suite
  - Verify authentication flows
  - Test permission boundaries
  - Validate data integrity
  - Check error handling paths
```

### Success/Failure Criteria

#### Deployment Success Criteria
```yaml
✅ Successful Deployment:
  - All tests pass (unit + integration)
  - Database migrations apply cleanly
  - Application starts without errors
  - Critical user flows functional
  - No security vulnerabilities introduced
  - Performance within acceptable limits

❌ Deployment Failure Signals:
  - Compilation errors
  - Test failures
  - Migration rollback required
  - Authentication/authorization bypass
  - Data corruption detected
  - Significant performance degradation
```

#### Code Quality Gates
```yaml
Mandatory Requirements:
  - No hardcoded secrets or credentials
  - Proper error handling and logging
  - Input validation for all user data
  - Role-based access control maintained
  - Database transactions for multi-step operations
  - Consistent naming conventions followed

Performance Requirements:
  - Page load times under 2 seconds
  - Database queries optimized (no N+1)
  - Proper indexing for new tables
  - Memory usage within limits
  - No resource leaks
```

This comprehensive documentation enables AI agents to safely understand, modify, and extend the MC-ORG application while maintaining system integrity, security, and performance standards across all technology layers.
