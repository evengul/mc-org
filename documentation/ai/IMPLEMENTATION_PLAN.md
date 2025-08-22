# MC-ORG Implementation Plan for AI Agents

## 📚 Documentation References

**Essential Reading for AI Agents**: Before beginning implementation work, review these documentation files for complete context:

- **[AI_AGENT_DOCUMENTATION.md](./AI_AGENT_DOCUMENTATION.md)** - Core architecture overview, technology stack, domain model, and operational guidelines
- **[AI_INTEGRATION_SPECS.md](./AI_INTEGRATION_SPECS.md)** - Behavioral specifications, pipeline patterns, error handling, and safety protocols  
- **[API_SPECIFICATIONS.md](./API_SPECIFICATIONS.md)** - HTMX-based HTML endpoint contracts, authentication flows, and request/response patterns
- **[BUSINESS_REQUIREMENTS.md](./BUSINESS_REQUIREMENTS.md)** - Domain requirements, user roles, permission model, and business rule constraints
- **[CSS_ARCHITECTURE.md](./CSS_ARCHITECTURE.md)** - CSS component system, utility classes, design tokens, and HTML refactoring guidelines

**Critical Architecture Notes**:
- This is a **Ktor-based Kotlin web application** using **server-side HTML generation** with Kotlin HTML DSL
- **Uses HTMX for dynamic interactions** - PUT/PATCH/POST/DELETE endpoints return HTML fragments, not full pages
- **Pipeline-based architecture** using Step interface with Result<E, S> pattern for error handling
- **Role-based permission system** with world-level access control via invitations

## Project Status Overview

**Last Updated**: 2025-01-21  
**Current Phase**: Core Feature Implementation  
**Progress**: ~30% Complete (foundation and some core features implemented)

## Implementation Priority Matrix

### ✅ COMPLETED FEATURES
- [x] **Authentication System** - JWT-based auth with proper pipeline
- [x] **World Creation** - `handleCreateWorld()` implemented
- [x] **Project Creation** - `handleCreateProject()` implemented  
- [x] **Home Dashboard** - `HomeHandler.handleGetHome()` fully implemented
- [x] **Profile Display** - `handleGetProfile()` implemented
- [x] **World Display** - `handleGetWorld()` implemented
- [x] **World Settings Display** - `handleGetWorldSettings()` implemented
- [x] **Admin Dashboard** - `handleGetAdminPage()` implemented
- [x] **Project Display** - `handleGetProject()` implemented
- [x] **World Updates** - `handleUpdateWorld()` implemented
- [x] **World Deletion** - `handleDeleteWorld()` implemented
- [x] **🎉 Invitation System (Sprint 1, Section 1)** - Complete invitation workflow implemented
- [x] **POST /app/worlds/{worldId}/settings/invitations** - Create world invitation ✅
- [x] **PATCH /app/invites/{inviteId}/accept** - Accept invitation ✅
- [x] **PATCH /app/invites/{inviteId}/decline** - Decline invitation ✅

### 🚧 HIGH PRIORITY - Core User Flows (Sprint 1)

#### ✅ Invitation System (Critical Path) - COMPLETED 🎉
- [x] **POST /app/worlds/{worldId}/settings/invitations** - Create world invitation
  - ✅ Pipeline: ValidateInvitationInputStep → ValidateInviterPermissionsStep → CreateInvitationStep
  - ✅ Authorization: Admin+ role required to send invitations
  - ✅ Validation: Target username must exist, role must be Member/Admin
  - ✅ Side Effects: Creates invitation record, sends notification to invitee
  - ✅ Template: HTML fragment replacing invitations list with new invitation included
  - **Actual Effort**: ~6 hours (vs estimated 4-5 hours)

- [x] **PATCH /app/invites/{inviteId}/accept** - Accept world invitation
  - ✅ Pipeline: ValidateInvitationStep → AcceptInvitationStep → CreateWorldMemberStep
  - ✅ Side Effects: Creates world_members record, updates invitation status
  - ✅ Template: HTML fragment with success message and redirect to world dashboard
  - **Actual Effort**: ~4 hours (within estimated 4-6 hours)

- [x] **PATCH /app/invites/{inviteId}/decline** - Decline world invitation  
  - ✅ Pipeline: ValidateInvitationStep → DeclineInvitationStep
  - ✅ Side Effects: Updates invitation status to declined
  - ✅ Template: HTML fragment with success message replacing invitation card
  - **Actual Effort**: ~2 hours (within estimated 2-3 hours)

#### Notification System (User Experience)
- [ ] **GET /app/notifications** - List user notifications
  - Pipeline: GetUserNotificationsStep → FormatNotificationsStep
  - Template: Full page with notifications list including read/unread states
  - **Estimated Effort**: 3-4 hours

- [ ] **PATCH /app/notifications/{notificationId}/read** - Mark notification as read
  - Pipeline: ValidateNotificationStep → MarkNotificationReadStep
  - Side Effects: Updates notification read status and timestamp
  - Template: HTML fragment replacing notification item with updated read state
  - **Estimated Effort**: 2-3 hours

- [ ] **PATCH /app/notifications/read** - Mark all notifications as read
  - Pipeline: GetUserNotificationsStep → BulkMarkReadStep
  - Side Effects: Updates all unread notifications for user
  - Template: HTML fragment replacing notification list with all items marked as read
  - **Estimated Effort**: 2-3 hours

#### Profile Management (User Settings)
- [ ] **PATCH /app/profile/display-name** - Update user display name
  - Pipeline: ValidateDisplayNameStep → UpdateProfileStep
  - Validation: Non-empty, max 50 characters, unique check
  - Template: HTML fragment replacing profile form with updated name and success state
  - **Estimated Effort**: 3-4 hours

- [ ] **PATCH /app/profile/avatar** - Update user avatar
  - Pipeline: ValidateAvatarStep → UpdateAvatarStep
  - Validation: File size, format (future: image upload)
  - Template: HTML fragment replacing avatar section with updated image
  - **Estimated Effort**: 4-5 hours (complex due to file handling)

### 🔧 MEDIUM PRIORITY - Project Management (Sprint 2)

#### Task Management
- [ ] **POST /app/worlds/{worldId}/projects/{projectId}/tasks** - Create new task
  - Pipeline: ValidateTaskInputStep → CreateTaskStep → GetProjectTasksStep
  - Validation: Name required, description optional, priority enum
  - Template: HTML fragment replacing tasks list with new task included
  - **Estimated Effort**: 4-5 hours

- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/tasks/{taskId}/complete** - Mark task complete
  - Pipeline: ValidateTaskAccessStep → CompleteTaskStep → CheckProjectProgressStep
  - Side Effects: Updates task completion, may trigger project stage change
  - Template: HTML fragment replacing task item with completed state
  - **Estimated Effort**: 3-4 hours

- [ ] **DELETE /app/worlds/{worldId}/projects/{projectId}/tasks/{taskId}** - Delete task
  - Pipeline: ValidateTaskOwnershipStep → DeleteTaskStep → GetUpdatedTasksStep
  - Authorization: Task creator or world admin+
  - Template: HTML fragment replacing tasks list with task removed
  - **Estimated Effort**: 3-4 hours

#### Project Stage Management
- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/stage** - Update project stage
  - Pipeline: ValidateStageTransitionStep → UpdateProjectStageStep
  - Validation: Valid stage enum, logical progression rules
  - Template: HTML fragment replacing project header with updated stage indicator
  - **Estimated Effort**: 4-5 hours

#### Task Requirements System
- [ ] **PUT /app/worlds/{worldId}/projects/{projectId}/tasks/{taskId}/requirements** - Update task requirements
  - Pipeline: ValidateRequirementsStep → UpdateRequirementsStep
  - Data: Countable vs Action tasks, quantities, completion criteria
  - Template: HTML fragment replacing requirements form with updated fields
  - **Estimated Effort**: 5-6 hours

- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/tasks/{taskId}/requirements/done-more** - Update task progress
  - Pipeline: ValidateProgressStep → UpdateTaskProgressStep
  - Validation: Cannot exceed total requirements
  - Template: HTML fragment replacing progress bar with updated percentage
  - **Estimated Effort**: 3-4 hours

### ⚙️ MEDIUM PRIORITY - World Management (Sprint 3)

#### World Settings Management
- [ ] **PATCH /app/worlds/{worldId}/settings/name** - Update world name
  - Pipeline: ValidateWorldNameStep → UpdateWorldStep → GetUpdatedWorldStep
  - Validation: Non-empty, max 100 characters, unique per user
  - Authorization: Admin+ role required
  - Template: HTML fragment replacing settings form with updated name
  - **Estimated Effort**: 3-4 hours

- [ ] **PATCH /app/worlds/{worldId}/settings/description** - Update world description
  - Pipeline: ValidateDescriptionStep → UpdateWorldStep
  - Validation: Max 1000 characters, HTML escaping
  - Template: HTML fragment replacing description editor with updated content
  - **Estimated Effort**: 3-4 hours

- [ ] **PATCH /app/worlds/{worldId}/settings/version** - Update Minecraft version
  - Pipeline: ValidateMinecraftVersionStep → UpdateWorldStep
  - Validation: Valid MinecraftVersion.fromString() format
  - Template: HTML fragment replacing version selector with updated selection
  - **Estimated Effort**: 4-5 hours

#### Member Management
- [ ] **PATCH /app/worlds/{worldId}/settings/members/role** - Update member role
  - Pipeline: ValidateRoleChangeStep → UpdateMemberRoleStep
  - Authorization: Owner can change any role, Admin can change Member roles
  - Validation: Cannot change Owner role, role enum validation
  - Template: HTML fragment replacing member list with updated role dropdowns
  - **Estimated Effort**: 5-6 hours

- [ ] **DELETE /app/worlds/{worldId}/settings/members** - Remove member from world
  - Pipeline: ValidateMemberRemovalStep → RemoveMemberStep → CheckWorldEmptyStep
  - Authorization: Owner/Admin can remove Members, Owner can remove Admins
  - Side Effects: May trigger world cleanup if no members remain
  - Template: HTML fragment replacing member list with member removed
  - **Estimated Effort**: 4-5 hours

- [ ] **DELETE /app/worlds/{worldId}/settings/members/invitations/{inviteId}** - Cancel invitation
  - Pipeline: ValidateInvitationOwnershipStep → CancelInvitationStep
  - Authorization: Invitation creator or world admin+
  - Template: HTML fragment replacing invitations list with invitation removed
  - **Estimated Effort**: 3-4 hours

### 🔗 MEDIUM PRIORITY - Project Dependencies (Sprint 4)

#### Dependency Management
- [ ] **POST /app/worlds/{worldId}/projects/{projectId}/dependencies/{projectId}** - Add project dependency
  - Pipeline: ValidateDependencyStep → CheckCyclesStep → CreateDependencyStep
  - Validation: No circular dependencies, same world only
  - Template: HTML fragment replacing dependency section with updated graph visualization
  - **Estimated Effort**: 6-8 hours (complex cycle detection)

- [ ] **DELETE /app/worlds/{worldId}/projects/{projectId}/dependencies/{projectId}** - Remove project dependency
  - Pipeline: ValidateDependencyExistsStep → RemoveDependencyStep
  - Template: HTML fragment replacing dependency list with dependency removed
  - **Estimated Effort**: 3-4 hours

- [ ] **POST /app/worlds/{worldId}/projects/{projectId}/dependencies/{projectId}/task/{taskId}** - Add task dependency
  - Pipeline: ValidateTaskDependencyStep → CheckTaskCyclesStep → CreateTaskDependencyStep
  - Validation: Cross-project task dependencies, no cycles
  - Template: HTML fragment replacing task list with dependency indicators added
  - **Estimated Effort**: 6-7 hours

- [ ] **DELETE /app/worlds/{worldId}/projects/{projectId}/dependencies/{projectId}/task/{taskId}** - Remove task dependency
  - Pipeline: ValidateTaskDependencyStep → RemoveTaskDependencyStep
  - Template: HTML fragment replacing task list with dependency markers removed
  - **Estimated Effort**: 3-4 hours

### 📍 LOW PRIORITY - Resource Management (Sprint 5)

#### Project Location & Resources
- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/location** - Update project location
  - Pipeline: ValidateLocationStep → UpdateProjectLocationStep
  - Data: Coordinates, world region, location description
  - Template: HTML fragment replacing location editor with updated coordinates
  - **Estimated Effort**: 4-5 hours

- [ ] **POST /app/worlds/{worldId}/projects/{projectId}/resources** - Add project resource
  - Pipeline: ValidateResourceStep → CreateResourceStep
  - Data: Resource type, quantity needed, current stock
  - Template: HTML fragment replacing resource list with new resource included
  - **Estimated Effort**: 5-6 hours

- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/resources/{resourceId}/active** - Update resource status
  - Pipeline: ValidateResourceAccessStep → UpdateResourceStep
  - Template: HTML fragment replacing resource item with updated status
  - **Estimated Effort**: 3-4 hours

- [ ] **PATCH /app/worlds/{worldId}/projects/{projectId}/resources/{resourceId}/rate** - Update resource rate
  - Pipeline: ValidateRateStep → UpdateResourceRateStep
  - Data: Collection/consumption rate tracking
  - Template: HTML fragment replacing resource rate calculator with updated values
  - **Estimated Effort**: 4-5 hours

- [ ] **DELETE /app/worlds/{worldId}/projects/{projectId}/resources/{resourceId}** - Delete resource
  - Pipeline: ValidateResourceOwnershipStep → DeleteResourceStep
  - Template: HTML fragment replacing resource list with resource removed
  - **Estimated Effort**: 3-4 hours

#### World Resource Management
- [ ] **GET /app/worlds/{worldId}/resources** - List world resources
  - Pipeline: GetWorldResourcesStep → FormatResourcesStep
  - Template: Full page with resource overview across all projects
  - **Estimated Effort**: 4-5 hours

- [ ] **POST /app/worlds/{worldId}/resources/map** - Create resource map
  - Pipeline: ValidateMapDataStep → CreateResourceMapStep
  - Data: Resource locations, quantities, accessibility
  - Template: HTML fragment replacing map section with new resource map interface
  - **Estimated Effort**: 8-10 hours (complex feature)

- [ ] **DELETE /app/worlds/{worldId}/resources/map** - Delete resource map
  - Pipeline: ValidateMapOwnershipStep → DeleteResourceMapStep
  - Template: HTML fragment with confirmation message and redirect
  - **Estimated Effort**: 2-3 hours

- [ ] **POST /app/worlds/{worldId}/resources/map/{mapId}** - Create map resource
  - Pipeline: ValidateMapResourceStep → CreateMapResourceStep
  - Template: HTML fragment replacing map with new resource marker added
  - **Estimated Effort**: 5-6 hours

- [ ] **DELETE /app/worlds/{worldId}/resources/map/{mapId}/{resourceId}** - Delete map resource
  - Pipeline: ValidateMapResourceStep → DeleteMapResourceStep
  - Template: HTML fragment replacing map with resource marker removed
  - **Estimated Effort**: 3-4 hours

### 🛡️ LOW PRIORITY - Admin Functions (Sprint 6)

#### User Administration
- [ ] **PATCH /app/admin/users/{userId}/role** - Update user global role
  - Pipeline: ValidateGlobalRoleStep → UpdateUserRoleStep
  - Authorization: Admin GlobalUserRole required
  - Validation: Valid GlobalUserRole enum
  - Template: HTML fragment replacing admin user list with updated role indicators
  - **Estimated Effort**: 4-5 hours

- [ ] **PATCH /app/admin/users/{userId}/ban** - Ban/unban user
  - Pipeline: ValidateBanActionStep → UpdateUserBanStatusStep
  - Side Effects: Removes user from all worlds, prevents new logins
  - Template: HTML fragment replacing admin user list with updated ban status
  - **Estimated Effort**: 5-6 hours

- [ ] **DELETE /app/admin/users/{userId}** - Delete user account
  - Pipeline: ValidateUserDeletionStep → TransferOwnershipStep → DeleteUserStep
  - Complex: Handle world ownership transfer, data cleanup
  - Template: HTML fragment replacing confirmation dialog with deletion success message
  - **Estimated Effort**: 8-10 hours (complex cascading deletes)

#### World Administration
- [ ] **DELETE /app/admin/worlds/{worldId}** - Admin delete world
  - Pipeline: ValidateAdminWorldAccessStep → DeleteWorldAdminStep
  - Authorization: Admin GlobalUserRole, emergency deletion
  - Side Effects: Cascading delete of all projects, tasks, dependencies
  - Template: HTML fragment replacing admin world list with world removed
  - **Estimated Effort**: 6-7 hours

- [ ] **DELETE /app/worlds/{worldId}/settings** - Owner delete world
  - Pipeline: ValidateWorldOwnershipStep → DeleteWorldStep
  - Authorization: Owner role only
  - Template: HTML fragment with success message and redirect to home
  - **Estimated Effort**: 4-5 hours

## Implementation Guidelines for AI Agents

### Critical: Handling Documentation-Code Inconsistencies

**⚠️ IMPORTANT**: When you encounter inconsistencies between documentation and existing code, follow this protocol:

1. **DO NOT** immediately rewrite code to match documentation
2. **DO NOT** immediately update documentation to match code
3. **ASK** which approach is correct - documentation or existing implementation
4. **IF** documentation is wrong → Update documentation to reflect correct implementation
5. **IF** code should follow documentation → Create a refactoring task in this plan under appropriate sprint
6. **DOCUMENT** the inconsistency clearly when asking for clarification

**Example Response Pattern**:
```
"I found an inconsistency between the documentation and existing code:
- Documentation states: [specific documentation claim]
- Existing code does: [specific code behavior]
- Location: [file/line references]

Should I:
A) Update documentation to match existing code, or
B) Add a refactoring task to update code to match documentation?"
```

This prevents unnecessary code churn and ensures architectural decisions are intentional, not accidental.

### Required Patterns for All Endpoints

#### 1. Pipeline Architecture Pattern
```kotlin
suspend fun ApplicationCall.handleFeatureAction() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val contextId = this.getContextId() // worldId, projectId, etc.

    executePipeline(
        onSuccess = { result: FeatureResult ->
            respondHtml(createHTML().div {
                featureSuccessTemplate(result)
            })
        },
        onFailure = { failure: FeatureFailures ->
            when (failure) {
                is FeatureFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString()}")
                is FeatureFailures.DatabaseError ->
                    respondBadRequest("Database operation failed")
                is FeatureFailures.InsufficientPermissions ->
                    respondBadRequest("Permission denied")
            }
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateInputStep)
            .step(ValidatePermissionsStep(user, contextId))
            .step(ExecuteBusinessLogicStep)
            .step(GetUpdatedDataStep)
    }
}
```

#### 2. Step Implementation Pattern
```kotlin
object ValidateFeatureInputStep : Step<Parameters, FeatureFailures, FeatureInput> {
    override suspend fun process(input: Parameters): Result<FeatureFailures, FeatureInput> {
        val field1 = ValidationSteps.required("field1", { FeatureFailures.ValidationError(listOf(it)) })
            .process(input)
        val field2 = ValidationSteps.optional("field2")
            .process(input)
            
        val errors = mutableListOf<ValidationFailure>()
        if (field1 is Result.Failure) errors.addAll(field1.error.errors)
        
        return if (errors.isNotEmpty()) {
            Result.failure(FeatureFailures.ValidationError(errors))
        } else {
            Result.success(FeatureInput(field1.getOrNull()!!, field2.getOrNull() ?: ""))
        }
    }
}
```

#### 3. Template Response Pattern
```kotlin
fun createFeatureSuccessTemplate(data: FeatureData): String = createHTML().div {
    // Return HTML fragment for HTMX updates or full page
    div("feature-response") {
        // Use CSS architecture from CSS_ARCHITECTURE.md
        div("notice notice--success") {
            +"Feature updated successfully"
        }
        // Include updated content
        featureContentComponent(data)
    }
}
```

### Validation Requirements

#### Standard Validation Rules
- **Authentication**: All endpoints require valid JWT token
- **Authorization**: Role-based access control per endpoint
- **Input Validation**: Use ValidationSteps for all form data
- **Business Rules**: Validate domain constraints (cycles, dependencies, etc.)

#### Common Validation Steps to Implement
1. `ValidateWorldAccessStep(user: TokenProfile, worldId: Int)`
2. `ValidateProjectAccessStep(user: TokenProfile, projectId: Int)`
3. `ValidateTaskOwnershipStep(user: TokenProfile, taskId: Int)`
4. `ValidateAdminRoleStep(user: TokenProfile, worldId: Int)`
5. `ValidateOwnerRoleStep(user: TokenProfile, worldId: Int)`

### Database Transaction Guidelines

#### Use DatabaseSteps.transaction for Multi-Step Operations
```kotlin
DatabaseSteps.transaction(
    step = object : Step<Input, Failures, Output> {
        override suspend fun process(input: Input): Result<Failures, Output> {
            // 1. Validate preconditions
            // 2. Execute business logic
            // 3. Update related records
            // 4. Return result
        }
    },
    errorMapper = { dbFailure -> FeatureFailures.DatabaseError }
).process(input)
```

### Testing Requirements

#### Required Tests for Each Endpoint

**Before Marking Any Task Complete:**
- [ ] Code compiles without errors (`mvn clean compile`)
- [ ] All existing tests pass (`mvn test`)
- [ ] New functionality has basic unit tests
- [ ] Manual testing of happy path completed
- [ ] Error cases properly handled

**For Database Changes:**
- [ ] Migration applies cleanly (`mvn flyway:migrate`)
- [ ] Migration can be rolled back (`mvn flyway:undo`)
- [ ] No data loss in migration
- [ ] Proper indexes created for new tables/columns

**For HTML/Template Changes:**
- [ ] Template renders without errors
- [ ] Mobile responsive design maintained
- [ ] Accessibility considerations preserved
- [ ] CSS classes follow architecture guidelines

## 🎓 Lessons Learned from Sprint 1 Implementation

### Critical Implementation Issues Identified

During the Sprint 1, Section 1 - Invitation System implementation, several critical issues were encountered that must be addressed in all future development work. This section serves as mandatory guidance for AI agents to prevent similar issues.

#### 1. Compilation Error Prevention Protocol

**Issue**: Multiple compilation errors occurred due to missing imports, incorrect type inference, and wrong property access patterns.

**Root Causes**:
- Missing imports for `kotlinx.html.stream.createHTML` (not `kotlinx.html.createHTML`)
- Incorrect property access on domain models (`userId` vs `id` on `TokenProfile`)
- Missing generic type parameters in `DatabaseSteps` calls
- Incorrect pipeline pattern usage

**Mandatory Prevention Steps**:
```kotlin
// ✅ REQUIRED: Always use these specific imports
import kotlinx.html.stream.createHTML  // NOT kotlinx.html.createHTML
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest

// ✅ REQUIRED: Always specify generic types for DatabaseSteps
DatabaseSteps.query<InputType, FailureType, OutputType>(...)
DatabaseSteps.update<InputType, FailureType>(...)

// ✅ REQUIRED: Use correct property names
user.id  // NOT user.userId (TokenProfile uses 'id')

// ✅ REQUIRED: Follow exact pipeline pattern
executePipeline(
    onSuccess = { result: ResultType ->
        respondHtml(createHTML().div { /* content */ })
    },
    onFailure = { failure: FailureType ->
        respondBadRequest("Error message")
    }
) {
    step(Step.value(initialInput))
        .step(ValidationStep)
        .step(BusinessLogicStep)
}
```

#### 2. Testing Requirements - MANDATORY

**Issue**: No tests were created during implementation, violating the established protocol.

**Mandatory Testing Protocol**:
```yaml
Before ANY implementation session:
  1. Create test file structure first
  2. Write failing tests for the functionality
  3. Implement the functionality to make tests pass
  4. Verify all tests pass before marking complete

Required Test Coverage:
  - Unit tests for all Step implementations
  - Unit tests for data classes and validation logic
  - Integration tests for complete pipelines (when possible)
  - Error case testing for all failure paths
  - Edge case testing (empty inputs, boundary values)

Test File Naming Convention:
  - Implementation: HandleCreateInvitation.kt
  - Test: HandleCreateInvitationTest.kt
  - Location: Mirror the src/main structure in src/test
```

**Test Implementation Status for Sprint 1**:
- ✅ Created: `HandleCreateInvitationTest.kt` - Tests validation logic and data structures
- ✅ Created: `InviteHandlerTest.kt` - Tests accept/decline data structures and failure types
- ⚠️ Missing: Database integration tests (requires test database setup)
- ⚠️ Missing: End-to-end pipeline tests (complex setup required)

#### 3. Domain Model Property Access Patterns

**Issue**: Confusion between different user model properties across the codebase.

**Standardized Property Access**:
```kotlin
// ✅ TokenProfile (authenticated user)
user.id                    // User ID (Int)
user.uuid                  // Minecraft UUID (String)
user.minecraftUsername     // Minecraft username (String)
user.displayName           // Display name (String)

// ✅ User domain model (database entity)
user.id                    // Primary key (Int)
user.username              // Username (String)
user.minecraftUuid         // Minecraft UUID (String)

// ❌ NEVER use
user.userId                // Does not exist on TokenProfile
```

#### 4. HTML Response Pattern Standardization

**Issue**: Inconsistent HTML response patterns causing HTMX integration problems.

**Mandatory HTML Response Pattern**:
```kotlin
// ✅ For successful operations (HTMX fragments)
respondHtml(createHTML().div {
    div("notice notice--success") {
        +"Operation completed successfully"
    }
    // Updated content here
})

// ✅ For error responses
respondBadRequest("Clear, user-friendly error message")

// ❌ NEVER use
respondText(..., contentType = io.ktor.http.ContentType.Text.Html)  // Too verbose
respondHtml(createHTML().html { ... })  // Creates full page, not fragment
```

#### 5. SafeSQL Usage Patterns

**Issue**: Incorrect SafeSQL constructor usage - the constructor is private.

**Correct SafeSQL Usage**:
```kotlin
// ✅ REQUIRED: Use factory methods
SafeSQL.select("SELECT ...")
SafeSQL.insert("INSERT ...")
SafeSQL.update("UPDATE ...")
SafeSQL.delete("DELETE ...")

// ❌ NEVER use
SafeSQL("SELECT ...")  // Constructor is private
```

#### 6. Pipeline Error Handling Patterns

**Issue**: Inconsistent error message handling across validation failures.

**Standardized Error Handling**:
```kotlin
// ✅ REQUIRED: Handle ValidationFailure properties correctly
when (it) {
    is ValidationFailure.MissingParameter -> "Missing ${it.parameterName}"
    is ValidationFailure.InvalidFormat -> "${it.parameterName}: ${it.message ?: "Invalid format"}"
    else -> it.toString()
}

// ✅ REQUIRED: Use proper ValidationSteps error mapping
ValidationSteps.required("fieldName", { FailureType.ValidationError(listOf(it)) })
```

### Mandatory Pre-Implementation Checklist

**Before starting ANY new feature implementation:**

1. **Architecture Review** (5 minutes)
   - [ ] Read existing similar implementations
   - [ ] Identify required imports and patterns
   - [ ] Plan database operations and transactions
   - [ ] Design error handling strategy

2. **Test-First Development** (15-30 minutes)
   - [ ] Create test file structure
   - [ ] Write failing unit tests for core logic
   - [ ] Write failing integration tests if applicable
   - [ ] Implement to make tests pass

3. **Implementation Validation** (10 minutes)
   - [ ] Run `mvn clean compile` - MUST pass
   - [ ] Run `mvn test` - MUST pass with new tests
   - [ ] Manual verification of happy path
   - [ ] Test error cases manually

4. **Code Quality Check** (5 minutes)
   - [ ] Follow established naming conventions
   - [ ] Use proper generic type parameters
   - [ ] Include meaningful error messages
   - [ ] Follow CSS architecture for HTML templates

### Compilation Safety Protocol

**NEVER commit or mark complete without:**

1. **Clean Compilation**: `mvn clean compile` must pass with zero errors
2. **Test Passage**: `mvn test` must pass with all existing + new tests
3. **Type Safety**: All generic type parameters explicitly specified
4. **Import Verification**: All imports verified and working

### Error Recovery Protocol

**When compilation errors occur:**

1. **Immediate Stop**: Do not continue implementing until errors are fixed
2. **Systematic Debugging**: 
   - Check imports first (most common issue)
   - Verify property access patterns
   - Confirm generic type parameters
   - Validate pipeline patterns
3. **Pattern Verification**: Compare with existing working implementations
4. **Test Creation**: Write tests to prevent regression

### Success Metrics for Future Sprints

**Sprint completion requires:**
- ✅ Zero compilation errors
- ✅ All new tests passing
- ✅ Existing tests continue to pass
- ✅ Manual verification completed
- ✅ Error cases properly handled
- ✅ Documentation updated

### Time Investment Allocation

**Recommended time distribution for future features:**
- 20% Planning and pattern analysis
- 25% Test creation (test-first development)
- 40% Implementation
- 10% Compilation and error fixing
- 5% Documentation updates

**Actual vs Estimated Time Analysis**:
- Sprint 1 took ~12 hours vs estimated 10-13 hours
- 40% of time spent on compilation fixes (should be 10%)
- 0% of time spent on tests (should be 25%)
- Future sprints should reallocate time to prevent similar issues

This lessons learned section must be consulted before starting any new feature work to prevent similar issues and maintain code quality standards.
