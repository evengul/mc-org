# MC-ORG Comprehensive Testing Plan

## 📋 Testing Strategy Overview

This testing plan provides comprehensive coverage for the MC-ORG Kotlin web application, focusing on both backend unit/integration tests and frontend end-to-end tests using Playwright. The plan is organized by implementation sprints and feature areas, with clear priorities and dependencies.

**Testing Philosophy**: Test-first development with comprehensive coverage of business logic, user workflows, and edge cases while maintaining fast feedback loops.

## 🎯 Testing Architecture

### Backend Testing Stack
- **Unit Tests**: Kotlin with JUnit 5 for isolated component testing
- **Integration Tests**: Full pipeline testing with database integration
- **Pattern**: Step-based architecture testing with Result<E, S> pattern validation
- **Database**: In-memory H2 for fast test execution
- **Coverage**: Business logic, validation, error handling, data transformation

### Frontend Testing Stack
- **End-to-End Tests**: Playwright with TypeScript for full user workflow testing
- **Pattern**: Page Object Model with reusable utilities and locators
- **Coverage**: User interactions, HTMX fragment updates, responsive design, accessibility
- **Environments**: Local development, test deployment, production verification

## 📊 Current Testing Status

### ✅ Existing Test Coverage
**Backend Unit Tests** (18 test files):
- Pipeline architecture (Step, Result, Pipeline patterns)
- Domain model validation (MinecraftVersion, validation steps)
- Database operations (SafeSQL, DatabaseSteps)
- Authentication pipeline (CreateUserIfNotExistsStep)
- Invitation system (HandleCreateInvitation, InviteHandler)
- World settings (UpdateWorldName, UpdateWorldDescription, UpdateWorldVersion)
- Task creation pipeline

**Frontend E2E Tests** (4 test files):
- Sign-in workflow and authentication
- World creation and navigation
- Project creation and management
- Task creation and basic management
- Basic user interface verification

### 🚧 Testing Gaps Identified
- No database integration tests with real PostgreSQL
- Limited error scenario coverage in E2E tests
- Missing permission boundary testing
- No responsive design validation
- Incomplete task management workflow testing
- No dependency management testing
- Missing notification system testing

## 🏗️ Testing Plan by Sprint

### Sprint 1 - Core Foundation Testing (COMPLETED + GAPS)

#### ✅ Backend Tests - Authentication & World Management
**Status**: Partially complete, needs enhancement

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── presentation/handler/
│   ├── AuthHandlerTest.kt ⚠️ MISSING
│   ├── WorldHandlerTest.kt ⚠️ MISSING
│   └── HomeHandlerTest.kt ⚠️ MISSING
├── pipeline/auth/
│   ├── AuthenticationPipelineTest.kt ⚠️ MISSING
│   └── CreateUserIfNotExistsStepTest.kt ✅ EXISTS
├── pipeline/world/
│   ├── CreateWorldPipelineTest.kt ⚠️ MISSING
│   ├── UpdateWorldPipelineTest.kt ⚠️ MISSING
│   └── DeleteWorldPipelineTest.kt ⚠️ MISSING
└── domain/model/
    ├── UserTest.kt ⚠️ MISSING
    ├── WorldTest.kt ⚠️ MISSING
    └── RoleTest.kt ⚠️ MISSING
```

**Test Priorities**:
1. **Authentication Pipeline Tests** (High Priority)
   - JWT token validation and extraction
   - User creation from Microsoft OAuth response
   - Authentication failure scenarios
   - Token expiration handling

2. **World Management Pipeline Tests** (High Priority)
   - World creation with all validation rules
   - World settings updates (name, description, version)
   - World deletion with cascade effects
   - Permission validation for world operations

3. **Role-based Access Control Tests** (Critical)
   - Owner/Admin/Member permission boundaries
   - Global role validation (Developer, Support, Moderator)
   - Permission inheritance and context switching

#### ✅ Backend Tests - Invitation System (COMPLETED)
**Status**: Well covered with room for enhancement

**Existing Coverage**:
- ✅ HandleCreateInvitationTest.kt - Validation logic and data structures
- ✅ InviteHandlerTest.kt - Accept/decline data structures and failure types

**Enhancement Needed**:
- Database integration tests for invitation workflows
- Notification side effects validation
- Cross-world invitation restriction testing

#### ✅ Frontend Tests - Core User Flows
**Status**: Basic coverage exists, needs comprehensive scenarios

**Required Test Coverage**:
#### ✅ Frontend Tests - Core User Flows (COMPLETE REWRITE REQUIRED)
**Status**: All existing tests outdated - Complete rewrite needed from scratch
// ✅ EXISTS: projects.page.spec.ts
**Critical Status Update**: Due to major refactor, all existing Playwright tests are non-functional and must be completely rewritten. This is not an enhancement task but a ground-up rebuild of the entire E2E test suite.

**Required Test Replacements (Complete Rewrite)**:
```
// ⚠️ MISSING: Complete user workflow tests
tests/
├── auth/
│   ├── authentication-flow.spec.ts ⚠️ REWRITE (was signIn.page.spec.ts)
│   ├── oauth-integration.spec.ts ⚠️ NEW
│   ├── session-management.spec.ts ⚠️ NEW
│   └── auth-error-scenarios.spec.ts ⚠️ NEW
├── world/
│   ├── world-creation.spec.ts ⚠️ REWRITE (was firstWorld.page.spec.ts)
│   ├── world-dashboard.spec.ts ⚠️ NEW
│   ├── world-settings.spec.ts ⚠️ NEW
│   └── world-deletion.spec.ts ⚠️ NEW
├── project/
│   ├── project-creation.spec.ts ⚠️ REWRITE (was projects.page.spec.ts)
│   ├── project-stage-management.spec.ts ⚠️ NEW
│   ├── project-settings.spec.ts ⚠️ NEW
│   └── project-deletion.spec.ts ⚠️ NEW
├── task/
│   ├── task-creation.spec.ts ⚠️ REWRITE (was tasks.page.spec.ts)
│   ├── task-completion.spec.ts ⚠️ NEW
│   ├── task-requirements.spec.ts ⚠️ NEW
│   └── task-assignment.spec.ts ⚠️ NEW
├── shared/
│   ├── page-objects.ts ⚠️ REWRITE (was locators.ts)
│   ├── test-utilities.ts ⚠️ REWRITE (was utils.ts)
│   └── fixtures.ts ⚠️ NEW
└── ui/
    ├── htmx-fragments.spec.ts ⚠️ NEW
    ├── form-validation.spec.ts ⚠️ NEW
    ├── responsive-design.spec.ts ⚠️ NEW
    └── accessibility.spec.ts ⚠️ NEW
   - Token persistence and validation
   - Sign-out and session cleanup
```
**E2E Test Rewrite Priorities**:
1. **Authentication Flow Testing** (Critical - Foundation for all other tests)
2. **World Management Workflows** (High Priority)
   - JWT token persistence and validation
   - World settings management via HTMX
   - World deletion confirmation flow
   - **Estimated Effort**: 12-15 hours (complete rewrite)
   - Permission-based UI element visibility

   - World creation with validation and error handling
   - World settings management via HTMX fragments
   - World deletion confirmation flow with cascading effects
   - Permission-based UI element visibility testing
   - **Estimated Effort**: 15-18 hours (complete rewrite)

3. **Project & Task Management** (High Priority)
   - Project creation with all form fields and validation
   - Task creation (countable vs action tasks)
   - Project stage transitions through complete lifecycle
   - Task completion and progress tracking
   - **Estimated Effort**: 18-22 hours (complete rewrite)

4. **HTMX Fragment Testing** (Critical for UI Validation)
   - Verify PUT/PATCH/POST responses replace correct DOM elements
   - Test error handling and validation message display
   - Validate loading states and user feedback
   - Test concurrent user interactions and race conditions
   - **Estimated Effort**: 10-12 hours (new functionality)

5. **Cross-browser and Device Testing** (Medium Priority)

#### 🔧 Backend Tests - Project Data Pipeline (Critical Path)
   - Accessibility compliance verification (WCAG 2.1 basics)
   - **Estimated Effort**: 8-10 hours (new functionality)

**Total E2E Test Rewrite Effort**: 63-77 hours

**Rewrite Strategy**:
1. **Start Fresh**: Do not attempt to modify existing test files
2. **Modern Patterns**: Use current Playwright best practices and Page Object Model
3. **Current Locators**: Build locator strategies based on actual current HTML structure
4. **HTMX Integration**: Design tests specifically for HTMX fragment updates
5. **Mobile-First**: Test mobile viewports as primary target
6. **Accessibility**: Include basic accessibility checks in all user workflow tests

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── pipeline/project/
│   ├── CreateProjectPipelineTest.kt ✅ EXISTS (basic)
│   ├── GetProjectDataPipelineTest.kt ⚠️ MISSING
│   ├── UpdateProjectPipelineTest.kt ⚠️ MISSING
│   └── ProjectStageTransitionTest.kt ⚠️ MISSING
├── pipeline/task/
│   ├── CreateTaskPipelineTest.kt ✅ EXISTS
│   ├── CompleteTaskPipelineTest.kt ⚠️ MISSING
│   ├── UpdateTaskProgressTest.kt ⚠️ MISSING
│   └── TaskRequirementTest.kt ⚠️ MISSING
└── database/integration/
    ├── ProjectDataRetrievalTest.kt ⚠️ MISSING
    ├── TaskDataRetrievalTest.kt ⚠️ MISSING
    └── DependencyGraphTest.kt ⚠️ MISSING
```

**Critical Database Integration Tests**:
1. **Project Data Retrieval** (Must complete before Sprint 2 features)
   - Replace MockProjects with real database queries
   - Test GetProjectByIdStep with various access scenarios
   - Validate project location and metadata retrieval
   - Test performance with large project datasets

2. **Task Data Management** (Foundation for task workflows)
   - Replace MockTasks with database operations
   - Test task requirements (ItemRequirement, ActionRequirement)
   - Validate task progress tracking and completion
   - Test task assignment and ownership

3. **Dependency Graph Operations** (Complex business logic)
   - Replace MockDependencies with database queries
   - Test circular dependency detection algorithms
   - Validate dependency chain traversal
   - Test cross-project dependency restrictions

#### 🔧 Frontend Tests - Project Management Workflows
**Status**: Basic tests exist, needs comprehensive user scenarios

**Required Test Coverage**:
```typescript
tests/
├── project/
│   ├── project-creation.spec.ts ⚠️ ENHANCE EXISTING
│   ├── project-stage-management.spec.ts ⚠️ MISSING
│   ├── project-settings.spec.ts ⚠️ MISSING
│   └── project-deletion.spec.ts ⚠️ MISSING
├── task/
│   ├── task-creation.spec.ts ⚠️ ENHANCE EXISTING
│   ├── task-completion.spec.ts ⚠️ MISSING
│   ├── task-requirements.spec.ts ⚠️ MISSING
│   └── task-assignment.spec.ts ⚠️ MISSING
└── ui/
    ├── htmx-fragments.spec.ts ⚠️ MISSING
    └── form-validation.spec.ts ⚠️ MISSING
```

**Test Scenarios**:
1. **Project Lifecycle Management**
   - Create project with all fields and validation
   - Update project stage through complete workflow
   - Test stage transition business rules
   - Delete project with confirmation and cascade effects

2. **Task Management Workflows**
   - Create countable vs action tasks
   - Update task progress with various completion methods
   - Test task assignment and reassignment
   - Complete tasks and verify project stage updates

3. **HTMX Fragment Updates**
   - Verify PUT/PATCH/POST responses replace correct DOM elements
   - Test error handling and validation message display
   - Validate loading states and user feedback
   - Test concurrent user interactions

### Sprint 3 - Advanced Features & Edge Cases

#### 🔗 Backend Tests - Dependency Management
**Status**: Not yet implemented, needs comprehensive test coverage

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── pipeline/dependency/
│   ├── CreateDependencyPipelineTest.kt ⚠️ MISSING
│   ├── ValidateDependencyCyclesTest.kt ⚠️ MISSING
│   ├── RemoveDependencyPipelineTest.kt ⚠️ MISSING
│   └── DependencyGraphVisualizationTest.kt ⚠️ MISSING
├── domain/model/
│   ├── ProjectDependencyTest.kt ⚠️ MISSING
│   └── TaskDependencyTest.kt ⚠️ MISSING
└── algorithm/
    ├── CycleDetectionTest.kt ⚠️ MISSING
    └── DependencyOrderingTest.kt ⚠️ MISSING
```

**Complex Algorithm Testing**:
1. **Cycle Detection Algorithms**
   - Test various dependency cycle scenarios
   - Validate performance with large dependency graphs
   - Test edge cases (self-dependencies, indirect cycles)
   - Verify error messages for cycle violations

2. **Dependency Ordering**
   - Test topological sort implementation
   - Validate build order recommendations
   - Test parallel dependency resolution
   - Verify dependency chain traversal accuracy

#### 📍 Backend Tests - Resource Management
**Status**: Not yet implemented, needs design and test coverage

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── pipeline/resource/
│   ├── CreateResourcePipelineTest.kt ⚠️ MISSING
│   ├── UpdateResourceRateTest.kt ⚠️ MISSING
│   └── ResourceCalculationTest.kt ⚠️ MISSING
├── domain/model/
│   ├── ResourceProductionTest.kt ⚠️ MISSING
│   └── ResourceRequirementTest.kt ⚠️ MISSING
└── calculation/
    ├── ResourceEfficiencyTest.kt ⚠️ MISSING
    └── ProductionRateTest.kt ⚠️ MISSING
```

#### 🔗 Frontend Tests - Advanced User Workflows
**Status**: Not implemented, needs comprehensive scenarios

**Required Test Coverage**:
```typescript
tests/
├── dependency/
│   ├── dependency-creation.spec.ts ⚠️ MISSING
│   ├── dependency-visualization.spec.ts ⚠️ MISSING
│   └── cycle-prevention.spec.ts ⚠️ MISSING
├── resource/
│   ├── resource-management.spec.ts ⚠️ MISSING
│   ├── resource-calculation.spec.ts ⚠️ MISSING
│   └── resource-mapping.spec.ts ⚠️ MISSING
└── collaboration/
    ├── multi-user-scenarios.spec.ts ⚠️ MISSING
    └── permission-boundaries.spec.ts ⚠️ MISSING
```

### Sprint 4 - Notification & Admin Systems

#### 🔔 Backend Tests - Notification System
**Status**: Implementation completed, needs comprehensive testing

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── pipeline/notification/
│   ├── GetUserNotificationsTest.kt ⚠️ MISSING
│   ├── MarkNotificationReadTest.kt ⚠️ MISSING
│   └── BulkMarkReadTest.kt ⚠️ MISSING
├── domain/model/
│   └── NotificationTest.kt ⚠️ MISSING
└── integration/
    ├── NotificationWorkflowTest.kt ⚠️ MISSING
    └── InvitationNotificationTest.kt ⚠️ MISSING
```

#### 🛡️ Backend Tests - Admin Functions
**Status**: Basic implementation exists, needs security testing

**Required Test Files**:
```
src/test/kotlin/app/mcorg/
├── pipeline/admin/
│   ├── UpdateUserGlobalRoleTest.kt ⚠️ MISSING
│   ├── BanUserTest.kt ⚠️ MISSING
│   └── DeleteUserAccountTest.kt ⚠️ MISSING
├── security/
│   ├── GlobalRoleValidationTest.kt ⚠️ MISSING
│   └── AdminPermissionTest.kt ⚠️ MISSING
└── integration/
    ├── AdminWorkflowTest.kt ⚠️ MISSING
    └── CascadingDeleteTest.kt ⚠️ MISSING
```

## 🧪 Testing Implementation Guidelines

### Backend Test Patterns

#### 1. Pipeline Testing Template
```kotlin
class FeaturePipelineTest {
    
    @Test
    fun `should succeed with valid input`() {
        // Arrange
        val validInput = FeatureInput(...)
        val expectedOutput = FeatureOutput(...)
        
        // Act
        val result = FeaturePipeline().process(validInput)
        
        // Assert
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isEqualTo(expectedOutput)
    }
    
    @Test
    fun `should fail with invalid input`() {
        // Arrange
        val invalidInput = FeatureInput(...)
        
        // Act
        val result = FeaturePipeline().process(invalidInput)
        
        // Assert
        assertThat(result).isFailure()
        assertThat(result.error).isInstanceOf(FeatureFailures.ValidationError::class.java)
    }
}
```

#### 2. Database Integration Testing Pattern
```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {
    
    @BeforeAll
    fun setupDatabase() {
        // Initialize test database with Flyway migrations
        // Create test data fixtures
    }
    
    @Test
    fun `should retrieve correct data from database`() {
        // Test database operations with real data
        // Verify data integrity and relationships
        // Test performance with realistic datasets
    }
    
    @AfterAll
    fun cleanupDatabase() {
        // Clean up test data
        // Reset database state
    }
}
```

### Frontend Test Patterns

#### 1. Page Object Model Template
```typescript
class ProjectPage {
    constructor(private page: Page) {}
    
    // Locators
    get createButton() { return this.page.locator('[data-testid="create-project"]') }
    get projectForm() { return this.page.locator('#project-form') }
    get projectList() { return this.page.locator('.project-list') }
    
    // Actions
    async createProject(name: string, description: string) {
        await this.createButton.click()
        await this.page.fill('[name="name"]', name)
        await this.page.fill('[name="description"]', description)
        await this.page.click('[type="submit"]')
    }
    
    // Assertions
    async expectProjectVisible(name: string) {
        await expect(this.page.locator(`text=${name}`)).toBeVisible()
    }
}
```

#### 2. HTMX Fragment Testing Template
```typescript
test('should update UI fragment after action', async ({ page }) => {
    // Arrange
    await page.goto('/app/feature')
    const targetElement = page.locator('#target-element')
    
    // Act - Trigger HTMX request
    await page.click('[hx-put="/api/endpoint"]')
    
    // Assert - Verify fragment replacement
    await expect(targetElement).toContainText('Updated content')
    await expect(targetElement).toHaveAttribute('data-updated', 'true')
})
```

## 📋 Testing Execution Strategy

### Test-First Development Protocol
1. **Red**: Write failing tests for new functionality
2. **Green**: Implement minimum code to make tests pass
3. **Refactor**: Improve code while maintaining test coverage

### Continuous Integration Requirements
```yaml
CI Pipeline Tests:
  Backend:
    - Unit tests: mvn test
    - Integration tests: mvn verify
    - Code coverage: minimum 80%
    
  Frontend:
    - E2E tests: npm run test
    - Cross-browser: Chrome, Firefox, Safari
    - Mobile viewport testing
    
  Database:
    - Migration testing: flyway:migrate && flyway:undo
    - Performance testing with large datasets
    - Data integrity validation
```

### Testing Environment Setup

#### Local Development Testing
```bash
# Backend testing
mvn clean test                    # Unit tests
mvn clean verify                  # Integration tests  
mvn flyway:migrate                # Database setup
mvn flyway:clean flyway:migrate   # Fresh database

# Frontend testing
cd src/test/javascript
npm install
npm run test                      # All E2E tests
npm run test:ui                   # Interactive test runner
```

#### Test Data Management
- **Fixtures**: Standardized test data for consistent scenarios
- **Factories**: Generate test data with realistic variations
- **Cleanup**: Automatic test data cleanup between test runs
- **Isolation**: Each test runs with clean database state

## 🎯 Success Criteria & Coverage Goals

### Backend Test Coverage Goals
- **Unit Tests**: 90% code coverage for business logic
- **Integration Tests**: 100% coverage for database operations
- **Pipeline Tests**: All success and failure paths covered
- **Error Scenarios**: All defined failure types tested

### Frontend Test Coverage Goals
- **User Workflows**: 100% coverage of critical user paths
- **HTMX Interactions**: All dynamic updates tested
- **Responsive Design**: Mobile and desktop viewports covered
- **Accessibility**: Basic WCAG 2.1 compliance verified

### Performance Testing Goals
- **Page Load Times**: Under 2 seconds for all pages
- **Database Queries**: No N+1 query issues
- **Memory Usage**: No memory leaks in long-running tests
- **Concurrent Users**: Support for 100+ simultaneous users

## 📊 Testing Metrics & Reporting

### Test Execution Metrics
- Test execution time (target: under 5 minutes for full suite)
- Test stability (target: 95% pass rate on retry)
- Code coverage percentage
- Test maintenance overhead

### Quality Metrics
- Bug detection rate in testing vs production
- Regression prevention effectiveness
- User workflow completion rates
- Performance benchmark compliance

This comprehensive testing plan ensures robust validation of the MC-ORG application across all implementation sprints while maintaining development velocity and code quality standards.
