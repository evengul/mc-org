# API Contracts & Endpoint Specifications for AI Agents

## HTML-Based API Endpoints with HTMX

**CRITICAL**: This application serves HTML responses, not JSON. All endpoints return server-rendered HTML using Kotlin HTML DSL. **The application uses HTMX for dynamic interactions**:

- **GET endpoints**: Return full pages using `createPage()` 
- **PUT/PATCH/POST/DELETE endpoints**: Return HTML fragments that replace specific page sections
- **HTMX attributes**: Used in templates to define dynamic behavior (`hxTarget`, `hxPut`, `hxDelete`, etc.)
- **Partial updates**: Only the affected DOM elements are replaced, not entire pages

## Authentication & Authorization Flow

### Authentication Pipeline
```yaml
Pipeline Order:
  1. EnvPlugin - Environment validation
  2. AuthPlugin - JWT token validation  
  3. BannedPlugin - User ban status check (app routes only)
  4. Route-specific parameter plugins (WorldParamPlugin, ProjectParamPlugin, TaskParamPlugin)
```

### JWT Token Structure
```kotlin
// TokenProfile structure for authenticated users
data class TokenProfile(
    val userId: Int,
    val username: String,
    val minecraftUuid: String?,
    val globalRole: GlobalUserRole?,
    // ... additional profile fields
)
```

## Core API Endpoints

### World Management API
```yaml
Base Path: /app/worlds

POST /app/worlds:
  Description: Create new world
  Authentication: Required
  Request: HTML form data
  Fields:
    - name: String (required)
    - description: String (required)  
    - version: MinecraftVersion (required)
  Response: HTML redirect to world page
  Pipeline: call.handleCreateWorld()

GET /app/worlds/{worldId}:
  Description: Display world dashboard
  Authentication: Required
  Authorization: World access via invitation
  Plugins: [WorldParamPlugin]
  Response: HTML world dashboard page
  Pipeline: call.handleGetWorld()
  Template: worldPage(user, world, projects, tab, toggles)

PUT /app/worlds/{worldId}:
  Description: Update world settings
  Authentication: Required
  Authorization: Admin+ role in world
  Pipeline: call.handleUpdateWorld()

DELETE /app/worlds/{worldId}:
  Description: Delete world
  Authentication: Required
  Authorization: Owner role required
  Pipeline: call.handleDeleteWorld()
```

### Project Management API
```yaml
Base Path: /app/worlds/{worldId}/projects

POST /app/worlds/{worldId}/projects:
  Description: Create project in world
  Authentication: Required
  Authorization: Member+ role in world
  Pipeline: call.handleCreateProject()
  
GET /app/worlds/{worldId}/projects/{projectId}:
  Description: View project details
  Authentication: Required
  Authorization: World access required
  Plugins: [WorldParamPlugin, ProjectParamPlugin]
  Pipeline: call.handleGetProject()

PATCH /app/worlds/{worldId}/projects/{projectId}/stage:
  Description: Update project stage
  Authentication: Required
  Authorization: Member+ role in world
```

### User & Profile Management
```yaml
Base Path: /app

GET /app/profile:
  Description: User profile page
  Authentication: Required
  Handler: ProfileHandler.profileRoutes()

GET /app/home:
  Description: User dashboard
  Authentication: Required  
  Handler: HomeHandler.homeRoute()
```

### Admin Functions
```yaml
Base Path: /app/admin

Access Control: GlobalUserRole required
Handler: AdminHandler.adminRoutes()
Functions:
  - User management
  - System monitoring
  - Global role assignment
```

### Invitation System
```yaml
Base Path: /app/invites

GET /app/invites:
  Description: List user invitations
  Authentication: Required
  
POST /app/worlds/{worldId}/settings/invitations:
  Description: Create world invitation
  Authentication: Required
  Authorization: Admin+ role in world
  Pipeline: call.handleCreateInvitation()
```

## Request/Response Patterns

### Standard Form Processing Pattern
```kotlin
// Actual pattern: Pipeline-based processing with Result type
suspend fun ApplicationCall.handleCreateFeature() {
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
        step(Step.value(parameters))
            .step(ValidateFeatureInputStep)
            .step(ValidatePermissionsStep(user, worldId))
            .step(CreateFeatureStep(worldId))
            .step(GetUpdatedDataStep)
    }
}
```

### Error Handling Pattern
```kotlin

// Error handling in pipeline steps
object ValidateFeatureInputStep : Step<Parameters, AppFailure.ValidationError, FeatureInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, FeatureInput> {
        val name = ValidationSteps.required("name", { CreateFeatureFailures.ValidationError(listOf(it)) })
            .process(input)
        val description = ValidationSteps.optional("description")
            .process(input)
            
        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) {
            errors.addAll(name.error.errors)
        }
        
        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            Result.success(FeatureInput(name.getOrNull()!!, description.getOrNull() ?: ""))
        }
    }
}
```

## Data Validation Specifications

### Input Validation Rules
```yaml
Validation Order:
  1. Authentication (JWT token)
  2. Authorization (role/permission check)
  3. Input validation (format, length, constraints)
  4. Business logic execution

User Input Fields:
  World Name:
    - Type: String
    - Min Length: 3
    - Max Length: 50
    - Pattern: Alphanumeric + spaces
    - Required: true
    
  Project Description:
    - Type: String
    - Max Length: 1000
    - HTML escaping: Required
    - Required: false
    
  Minecraft Version:
    - Type: Validated String (MinecraftVersion.fromString())
    - Validation: Must parse to valid Release or Snapshot format
    - Examples: "1.20.1" (Release), "23w31a" (Snapshot)
    - Required: true
```

### Dependency Validation
```yaml
Project Dependencies:
  Rules:
    - Cannot create circular dependencies
    - Can depend on projects in same world
    - Can depend on projects in other accessible worlds
    - Must validate dependency chain on creation
    
Validation Algorithm:
  1. Build dependency graph
  2. Perform cycle detection (DFS)
  3. Reject if cycle found
  4. Accept if acyclic
```

## HTML Template Patterns

### Page Structure Pattern
```kotlin
// Standard page creation pattern
fun createFeaturePage(
    user: TokenProfile,
    data: DomainData,
    options: FeatureOptions = FeatureOptions()
) = createPage(
    user = user,
    pageTitle = "Feature Title"
) {
    // Page-specific CSS classes
    classes += "feature-page"
    
    // Header section
    featureHeader(data, options)
    
    // Main content
    featureContent(data, options)
    
    // Footer/actions
    featureActions(data, options)
}
```

### Component Composition Pattern
```kotlin
// Reusable component pattern
fun DIV.componentName(
    data: ComponentData,
    variant: ComponentVariant = ComponentVariant.DEFAULT
) {
    div("component-${variant.name.lowercase()}") {
        // Component structure
        componentHeader(data)
        componentContent(data)
        componentActions(data)
    }
}
```

### Form Pattern
```kotlin
// Standard form structure
fun DIV.createForm(
    action: String,
    method: FormMethod = FormMethod.post
) {
    form(action = action, method = method) {
        // CSRF protection
        hiddenInput(name = "_token", value = csrfToken)
        
        // Form fields with validation
        formField("fieldName", InputType.text) {
            required = true
            // Additional attributes
        }
        
        // Submit button
        submitInput(classes = "button primary") {
            value = "Submit"
        }
    }
}
```

## CSS & Asset Management

### CSS Class Conventions
```yaml
Naming Convention: BEM-like structure
  - Block: .component-name
  - Element: .component-name__element
  - Modifier: .component-name--modifier

Example:
  - .world-header
  - .world-header__title  
  - .world-header--compact

Common Utility Classes:
  - .button, .button--primary, .button--secondary
  - .chip, .chip--success, .chip--warning
  - .icon, .icon--small, .icon--large
  - .tabs, .tab--active
```

### Static Asset Structure
```yaml
/static/fonts/:
  - Minecraftchmc-dBlX.ttf (theme font)

/static/icons/:
  - SVG icon library
  - Usage: Icons.ICON_NAME in Kotlin HTML DSL

/static/styles/:
  - Component-specific CSS files
  - Global theme and utility styles

/static/scripts/:
  - Minimal client-side JavaScript
  - Progressive enhancement only
```

## Testing Specifications

### Playwright Test Structure
```typescript
// Standard test pattern
import { test, expect } from '@playwright/test';

test('feature workflow', async ({ page }) => {
  // 1. Authentication
  await page.goto('/auth/signin');
  await authenticateUser(page);
  
  // 2. Navigation to feature
  await page.goto('/app/feature');
  
  // 3. Interaction testing
  await page.click('[data-testid="action-button"]');
  
  // 4. Assertion
  await expect(page.locator('.success-message')).toBeVisible();
});
```

### Test Data Requirements
```yaml
Test Users:
  - Standard user (Member role)
  - Admin user (Admin role) 
  - Owner user (Owner role)
  - Banned user (Banned role)

Test Worlds:
  - Public world with projects
  - Private world with limited access
  - Empty world for creation testing

Test Projects:
  - Projects with dependencies
  - Standalone projects
  - Projects in different stages
```

## HTMX Integration Patterns

### HTMX Response Pattern for PUT/PATCH/POST/DELETE
```kotlin
// Pattern: Return HTML fragments for HTMX target replacement
suspend fun ApplicationCall.handleUpdateFeature() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val contextId = this.getContextId()

    executePipeline(
        onSuccess = { updatedData: FeatureData ->
            // Return HTML fragment that replaces the target element
            respondHtml(createHTML().div {
                form {
                    id = "feature-form" // Must match hxTarget
                    featureForm(updatedData) // Updated form content
                }
            })
        },
        onFailure = { failure: FeatureFailures ->
            // Return error message or form with validation errors
            when (failure) {
                is FeatureFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString()}")
                else -> respondBadRequest("Operation failed")
            }
        }
    ) {
        // ...pipeline steps...
    }
}
```

### HTMX Template Pattern
```kotlin
// Pattern: Forms with HTMX attributes for dynamic updates
fun FORM.featureForm(data: FeatureData) {
    id = "feature-form" // Target for HTMX replacement
    encType = FormEncType.applicationXWwwFormUrlEncoded

    // HTMX attributes for dynamic behavior
    hxTarget("#feature-form") // Replace this form with response
    hxPut("/app/features/${data.id}") // PUT request endpoint
    
    // Form fields
    label {
        htmlFor = "feature-name-input"
        +"Feature Name"
    }
    input {
        name = "name"
        id = "feature-name-input"
        type = InputType.text
        value = data.name
    }
    
    // Submit button
    actionButton("Save Changes")
}

// Pattern: Delete buttons with confirmation
fun deleteButton(item: DomainEntity) {
    dangerButton("Delete") {
        buttonBlock = {
            hxDelete("/app/items/${item.id}")
            hxConfirm("Are you sure? This action cannot be undone.")
            hxTarget("#item-list") // Replace list after deletion
        }
    }
}
```

### HTMX Attribute Reference
```kotlin
// Common HTMX attributes used in templates
hxGet("/endpoint")           // GET request
hxPost("/endpoint")          // POST request  
hxPut("/endpoint")           // PUT request
hxPatch("/endpoint")         // PATCH request
hxDelete("/endpoint")        // DELETE request

hxTarget("#element-id")      // Target element to replace
hxTarget("closest .card")    // Target relative to trigger

hxConfirm("Are you sure?")   // Confirmation dialog
hxSwap("innerHTML")          // How to swap content (default)
hxSwap("outerHTML")          // Replace entire element

// Example real usage from codebase:
hxTarget("#world-general-settings-form")
hxPut("/app/worlds/${world.id}")
hxDelete(Link.Worlds.world(world.id).to)
hxConfirm("Are you sure you want to delete this world?")
```
