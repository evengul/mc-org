# AI Agent Documentation for MC-ORG Web Application

## Executive Summary

This is a **Ktor-based Kotlin web application** using **server-side HTML generation** with Kotlin HTML DSL. The application manages Minecraft world projects with user collaboration, task management, and project dependencies.

**Critical for AI Agents**: This application serves HTML, not JSON APIs. All endpoints return server-rendered HTML using Kotlin HTML DSL with CSS styling. **The application uses HTMX for dynamic interactions**, which means PUT/PATCH/POST/DELETE endpoints return only HTML fragments that replace specific parts of the page, not full page responses.

## Technology Stack

### Backend
- **Framework**: Ktor 3.0.3 (Kotlin web framework)
- **Language**: Kotlin 2.1.10 + Java 21
- **Server**: Netty embedded server (port 8080)
- **Database**: PostgreSQL with Flyway migrations
- **Authentication**: JWT-based with custom pipeline
- **Serialization**: Kotlinx JSON (for internal data handling)

### Frontend
- **Rendering**: Server-side HTML generation using Kotlin HTML DSL
- **Styling**: Static CSS files served from `/static/styles/`
- **Assets**: Icons and fonts served from `/static/` directory
- **Testing**: Playwright for end-to-end testing

### Build & Deployment
- **Build Tool**: Maven with specialized Flyway integration
- **Database Migrations**: Flyway (27 migrations from V1 to V2)
- **Containerization**: Docker with Fly.io deployment
- **Environment Config**: Environment-based configuration

## Application Architecture

### Entry Point
```kotlin
// Main class: app.mcorg.ApplicationKt
// Server: Netty on port 8080
// Module configuration: HTTP, Monitoring, Routing, Status Pages
```

### Module Hierarchy
```
app.mcorg/
├── Application.kt           # Entry point & server configuration
├── config/                  # Environment & configuration management
├── domain/                  # Business logic & domain models
│   ├── model/              # Domain entities (User, World, Project, Task)
│   └── pipeline/           # Business process pipelines
├── pipeline/               # API processing steps & cleanup
└── presentation/           # Web layer (routes, handlers, templates)
    ├── handler/            # Request handlers by feature
    ├── plugins/            # Ktor plugins (Auth, Environment, Banned)
    ├── router/             # Route configuration
    ├── security/           # Authentication & authorization
    ├── templated/          # HTML DSL templates by feature
    └── utils/              # Presentation utilities
```

### Routing Structure
```
/ (landing page)
/test/ping (health check)
/test/page (development test page)
/auth/* (authentication routes)
/app/* (main application routes - requires authentication)
  ├── home
  ├── profile
  ├── admin
  ├── notifications
  ├── invites
  ├── world
  └── ideas
```

## Domain Model Overview

### Core Entities

#### User Management
- **User**: Core user entity with profile information
- **Role**: Hierarchical permissions (OWNER=0, ADMIN=10, MEMBER=100, BANNED=1000)
- **GlobalUserRole**: System-wide roles for developers, support, moderators
- **Authentication**: JWT-based with invitation system

#### World & Project Management
- **World**: Minecraft world container for projects
- **Project**: Individual building projects within worlds
- **Task**: Granular tasks within projects
- **ProjectDependency**: Dependencies between projects/tasks (with cycle detection)

#### Collaboration
- **Invite**: World access invitations with role assignment
- **Notification**: User notification system
- **TaskAssignment**: User assignments to projects/tasks

### Permission Model
```yaml
World Access:
  - Via invitation with role (Member/Admin)
  - Owner role reserved for world creator
  - No hierarchical permissions within world
  
Global Roles:
  - Super-user access for system administration
  - Developer, support, moderator roles
  
Validation Order:
  1. Authentication check
  2. Access authorization
  3. Input validation
  4. Business logic execution
```

## AI Agent Guidelines

### Code Modification Permissions
✅ **ALLOWED**:
- Database schema changes via Flyway migrations
- New API endpoint creation
- Frontend UI component updates using Kotlin HTML DSL
- Authentication/authorization logic modifications
- Ktor plugin integration (with prior consultation)

⚠️ **REQUIRES CONSULTATION**:
- Ktor plugin modifications
- Database migration creation
- Major architectural changes

### Development Patterns

#### 1. Creating New Endpoints
```kotlin
// Pattern: Handler-based routing with pipeline execution
fun Route.newFeatureRoutes() {
    get("/new-feature") {
        call.respondHtml(createNewFeatureTemplate())
    }
    post("/new-feature") {
        call.handleCreateNewFeature() // Uses pipeline pattern
    }
}
```

#### 2. HTML DSL Templates
```kotlin
// Pattern: createPage as foundation, subdivided into specialized components
fun createNewFeatureTemplate(
    user: TokenProfile,
    data: FeatureData,
    options: FeatureOptions = FeatureOptions()
): String = createPage(
    pageTitle = "Feature Title",
    pageScripts = setOf(PageScript.HTMX),
    pageStyles = PageStyle.entries.toSet(),
    user = user
) {
    // Page-specific CSS classes
    classes += "feature-page"
    
    // Subdivide into specialized components
    featureHeader(data, options)
    featureContent(data, options) 
    featureActions(data, options)
}

// Component subdivision pattern
private fun MAIN.featureHeader(data: FeatureData, options: FeatureOptions) {
    div("feature-header") {
        // Further subdivision into smaller components
        featureTitle(data.title)
        featureBreadcrumb(data.path)
    }
}
```

#### 3. Database Migrations
```sql
-- Pattern: Flyway versioned migrations
-- File: V{major}_{minor}_{patch}__{description}.sql
-- Example: V2_12_0__add_new_feature_table.sql
```

#### 4. Domain Model Updates
```kotlin
// Pattern: Immutable data classes with validation
data class NewEntity(
    val id: Int,
    val name: String,
    // Include creation/update timestamps
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
```

### Critical Business Rules

#### 1. Permission Hierarchy
```kotlin
// Role comparison (lower level = higher authority)
role.isHigherThanOrEqualTo(requiredRole)
// OWNER(0) > ADMIN(10) > MEMBER(100) > BANNED(1000)
```

#### 2. Dependency Validation
```
Project dependencies must not create cycles
- Validate before creation
- Fail fast on cycle detection
- Support cross-project dependencies
```

#### 3. World Access Control
```
User access via invitation only
- One invitation per world
- Role assigned at invitation (Member/Admin/Owner)
- Owner role restricted to world creator
```

### Testing Requirements
- **Unit Tests**: Kotlin tests for business logic
- **Integration Tests**: Playwright tests for full user workflows
- **Database Tests**: Migration validation and rollback testing

### Static Asset Management
```
/static/
├── fonts/          # Typography assets
├── icons/          # SVG icon library
├── scripts/        # Client-side JavaScript (minimal)
└── styles/         # CSS stylesheets
```

## AI Agent Operational Guidelines

### 1. Before Making Changes
- Read existing code structure in the affected module
- Understand data flow through domain -> presentation layers
- Check for existing similar patterns to follow
- Validate against business rules

### 2. When Creating Features
- Follow handler-based routing pattern
- Use Kotlin HTML DSL for templates
- Implement proper validation pipeline
- Add appropriate tests (unit + integration)

### 3. Database Changes
- Always create new Flyway migrations
- Never modify existing migration files
- Test migrations with rollback scenarios
- Update domain models accordingly

### 4. Frontend Changes
- Use existing CSS classes and patterns
- Follow responsive design principles
- Ensure accessibility compliance
- Test across different screen sizes

### 5. Security Considerations
- Always validate user input
- Respect role-based access controls
- Use parameterized queries for database access
- Implement proper error handling without information leakage

This documentation enables AI agents to understand, maintain, and safely extend the MC-ORG codebase across all technology layers while preserving critical business rules and architectural patterns.
