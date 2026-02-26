# MC-ORG Architecture Reference

**Complete system architecture, technology stack, and domain model**

---

## 📋 Table of Contents

1. [Technology Stack](#technology-stack)
2. [Domain Model](#domain-model)
3. [Database Schema](#database-schema)
4. [Module Structure](#module-structure)
5. [Routing Architecture](#routing-architecture)
6. [Authentication & Authorization](#authentication--authorization)
7. [External API Integrations](#external-api-integrations)
8. [Data Flow](#data-flow)

---

## 🛠️ Technology Stack

### Backend

| Component          | Technology   | Version  | Purpose                 |
|--------------------|--------------|----------|-------------------------|
| **Framework**      | Ktor         | 3.0.3    | Kotlin web framework    |
| **Language**       | Kotlin       | 2.1.10   | Primary language        |
| **Runtime**        | Java         | 21       | JVM runtime             |
| **Server**         | Netty        | Embedded | HTTP server (port 8080) |
| **Database**       | PostgreSQL   | Latest   | Primary data store      |
| **Migrations**     | Flyway       | Latest   | Database versioning     |
| **Authentication** | JWT          | Custom   | Token-based auth        |
| **Serialization**  | Kotlinx JSON | Latest   | Internal data handling  |

### Frontend

| Component         | Technology      | Purpose                                  |
|-------------------|-----------------|------------------------------------------|
| **Rendering**     | Kotlin HTML DSL | Server-side HTML generation              |
| **Styling**       | Static CSS      | Component-based architecture             |
| **Interactivity** | HTMX            | Dynamic updates without full page reload |
| **Assets**        | Static files    | Icons (SVG), fonts (Minecraft font)      |
| **Testing**       | Playwright      | End-to-end testing                       |

### Build & Deployment

| Component            | Technology            | Purpose                    |
|----------------------|-----------------------|----------------------------|
| **Build Tool**       | Maven                 | Project build (NOT Gradle) |
| **Shell**            | Zsh                   | Command execution (macOS)  |
| **Containerization** | Docker                | Application packaging      |
| **Hosting**          | Fly.io                | Cloud deployment           |
| **Environment**      | Environment variables | Configuration management   |

### Key Characteristics

- **Architecture Pattern**: Server-side rendering with HTMX for dynamic updates
- **Response Type**: HTML (NOT JSON APIs)
- **State Management**: Server-side session + JWT tokens
- **Database Access**: Direct SQL with type-safe SafeSQL wrapper
- **Error Handling**: Result<E, S> pattern with AppFailure hierarchy

---

## 🏗️ Domain Model

### Core Entity Relationships

```
User (TokenProfile)
  ├─ Global Roles (List<String>: "superadmin", "moderator", "idea_creator", "banned")
  └─ MinecraftProfile (UUID, username)

World
  ├─ Creator (User, Owner role)
  ├─ WorldMembers (User + Role: Owner/Admin/Member/Banned)
  ├─ Projects[]
  ├─ Invites[]
  └─ MinecraftVersion

Project
  ├─ World (parent)
  ├─ Name, Description, Type
  ├─ Stage (Planning → Design → Building → Complete)
  ├─ Location (X, Y, Z coordinates)
  ├─ Tasks[] (ItemTask, ActionTask)
  ├─ Dependencies[] (ProjectDependency)
  ├─ Resources[] (ResourceProduction)
  ├─ Idea? (imported from)
  └─ Audit fields (createdBy, createdAt, updatedAt)

Task (Abstract)
  ├─ ItemTask
  │   ├─ RequiredItems[] (item, quantity)
  │   └─ Progress (done / total)
  └─ ActionTask
      ├─ ActionRequirement (description)
      └─ Complete (boolean)

Idea
  ├─ Name, Description
  ├─ Category (IdeaCategory with schema)
  ├─ CategoryData (JSONB - dynamic fields)
  ├─ Author, SubAuthors
  ├─ Labels[]
  ├─ Difficulty (Easy, Medium, Hard, Expert)
  ├─ Rating (average, count)
  ├─ MinecraftVersionRange
  └─ PerformanceTestData[]

Invite
  ├─ World (target)
  ├─ InvitedUser (User)
  ├─ InvitedBy (User)
  ├─ Role (Member or Admin)
  ├─ Status (Pending, Accepted, Declined)
  └─ Timestamps

Notification
  ├─ User (recipient)
  ├─ Type (InviteReceived, InviteAccepted, etc.)
  ├─ RelatedEntityId (world, project, etc.)
  ├─ Message
  ├─ Read (boolean)
  └─ Timestamps

ProjectDependency
  ├─ DependentProject (requires)
  ├─ DependencyProject (provides)
  └─ No circular dependencies allowed
```

### Detailed Entity Definitions

#### User & Authentication

**TokenProfile** (Authenticated user context)

```kotlin
data class TokenProfile(
    val id: Int,                    // User ID
    val uuid: String,               // Minecraft UUID
    val minecraftUsername: String,  // Minecraft username
    val displayName: String,        // Display name
    val roles: List<String>,        // System-wide roles
    // ... JWT claims
) {
    // Computed properties based on roles list
    val isSuperAdmin: Boolean = roles.contains("superadmin")
    val isModerator: Boolean = isSuperAdmin || roles.contains("moderator")
    val isIdeaCreator: Boolean = isSuperAdmin || roles.contains("idea_creator")
    val isBanned: Boolean = roles.contains("banned")
    val isDemoUserInProduction: Boolean = roles.contains("demo_user") && AppConfig.env == Production
}
```

**User** (Database entity)

```kotlin
data class User(
    val id: Int,
    val username: String,
    val minecraftUuid: String?,
    val displayName: String,
    // Note: Global roles stored in separate table global_user_roles
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
```

**Global Roles** (System administration)

Global roles are stored as strings in the `global_user_roles` table and checked in TokenProfile:

- `"superadmin"` - Full system access
- `"moderator"` - Community management
- `"idea_creator"` - Can create ideas
- `"banned"` - Banned from system
- `"demo_user"` - Demo account with write restrictions in production

#### World Management

**World** (Minecraft world/server container)

```kotlin
data class World(
    val id: Int,
    val name: String,
    val description: String,
    val minecraftVersion: MinecraftVersion,
    val createdBy: Int,             // User ID
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
```

**WorldMember** (User access to world)

```kotlin
data class WorldMember(
    val worldId: Int,
    val userId: Int,
    val role: Role,                  // Owner/Admin/Member/Banned
    val joinedAt: ZonedDateTime
)
```

**Role** (World-level permissions)

```kotlin
enum class Role(val level: Int) {
    OWNER(0),    // Full control, cannot be transferred
    ADMIN(10),   // Manage settings, invite users
    MEMBER(100), // Create/edit projects and tasks
    BANNED(1000) // No access
}
```

**MinecraftVersion** (Version tracking)

```kotlin
sealed class MinecraftVersion {
    data class Release(val version: String) : MinecraftVersion()  // "1.20.1"
    data class Snapshot(val version: String) : MinecraftVersion() // "23w31a"

    companion object {
        fun fromString(version: String): MinecraftVersion?
    }
}
```

#### Project Management

**Project** (Building project within world)

```kotlin
data class Project(
    val id: Int,
    val worldId: Int,
    val name: String,
    val description: String,
    val type: ProjectType,
    val stage: ProjectStage,
    val ideaId: Int?,                // Imported from idea
    val createdBy: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
```

**ProjectType** (Build categories)

```kotlin
enum class ProjectType {
    BUILDING,      // Structures, houses, castles
    CONTRAPTION,   // Redstone machines, farms
    INFRASTRUCTURE,// Roads, railways, utilities
    DECORATION,    // Gardens, statues, art
    OTHER          // Miscellaneous
}
```

**ProjectStage** (Lifecycle)

```kotlin
enum class ProjectStage {
    PLANNING,          // Initial design
    DESIGN,            // Detailed planning
    RESOURCE_GATHERING,// Collecting materials
    BUILDING,          // Active construction
    REVIEW,            // Quality check
    COMPLETE,          // Finished
    ARCHIVED           // Stored for reference
}
```

**ProjectLocation** (World coordinates)

```kotlin
data class ProjectLocation(
    val projectId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
)
```

#### Task Management

**Task System** (Split as of V2_20_0)

```kotlin
// Abstract concept - not a single table
sealed interface Task {
    val id: Int
    val projectId: Int
    val name: String
    val description: String
    val priority: TaskPriority
    val assignedTo: Int?
    val createdBy: Int
    val createdAt: ZonedDateTime
    val updatedAt: ZonedDateTime
}
```

**ItemTask** (Collection tasks)

```kotlin
data class ItemTask(
    override val id: Int,
    override val projectId: Int,
    override val name: String,
    override val description: String,
    override val priority: TaskPriority,
    override val assignedTo: Int?,
    val requirements: List<ItemRequirement>,
    override val createdBy: Int,
    override val createdAt: ZonedDateTime,
    override val updatedAt: ZonedDateTime
) : Task
```

**ItemRequirement** (What items to collect)

```kotlin
data class ItemRequirement(
    val itemId: String,       // Minecraft item ID
    val quantityRequired: Int,// Total needed
    val quantityDone: Int     // Currently collected
)
```

**ActionTask** (Completion tasks)

```kotlin
data class ActionTask(
    override val id: Int,
    override val projectId: Int,
    override val name: String,
    override val description: String,
    override val priority: TaskPriority,
    override val assignedTo: Int?,
    val requirement: ActionRequirement,
    val completed: Boolean,
    override val createdBy: Int,
    override val createdAt: ZonedDateTime,
    override val updatedAt: ZonedDateTime
) : Task
```

**ActionRequirement** (What action to complete)

```kotlin
data class ActionRequirement(
    val description: String  // "Place 32x32 foundation"
)
```

**TaskPriority** (Importance levels)

```kotlin
enum class TaskPriority {
    CRITICAL,      // Must be done
    NORMAL,        // Standard priority
    NICE_TO_HAVE   // Optional improvement
}
```

#### Ideas System

**Idea** (Design library)

```kotlin
data class Idea(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val author: Author,
    val subAuthors: List<Author>,
    val labels: List<String>,
    val favouritesCount: Int,
    val rating: RatingSummary,
    val difficulty: IdeaDifficulty,
    val worksInVersionRange: MinecraftVersionRange,
    val testData: List<PerformanceTestData>,
    val categoryData: Map<String, CategoryValue>, // Dynamic JSONB
    val createdBy: Int,
    val createdAt: ZonedDateTime
)
```

**IdeaCategory** (Idea types with schemas)

```kotlin
enum class IdeaCategory {
    FARM,           // Automated farms
    CONTRAPTION,    // Redstone machines
    BUILDING,       // Structures
    DECORATION,     // Aesthetics
    UTILITY,        // Tools and helpers
    OTHER           // Miscellaneous
}
```

**IdeaCategorySchema** (Dynamic form fields)

```kotlin
data class IdeaCategorySchema(
    val category: IdeaCategory,
    val fields: List<SchemaField>
)

sealed class SchemaField {
    data class TextField(val name: String, val label: String, val required: Boolean) : SchemaField()
    data class NumberField(val name: String, val label: String, val min: Int?, val max: Int?) : SchemaField()
    data class SelectField(val name: String, val label: String, val options: List<String>) : SchemaField()
    data class MultiSelectField(val name: String, val label: String, val options: List<String>) : SchemaField()
    data class BooleanField(val name: String, val label: String) : SchemaField()
    data class RateField(val name: String, val label: String, val unit: String) : SchemaField()
    data class DimensionsField(val name: String, val label: String) : SchemaField()
}
```

**IdeaDifficulty** (Build complexity)

```kotlin
enum class IdeaDifficulty {
    EASY,    // Simple builds
    MEDIUM,  // Moderate complexity
    HARD,    // Advanced builds
    EXPERT   // Highly complex
}
```

#### Collaboration

**Invite** (World invitations)

```kotlin
data class Invite(
    val id: Int,
    val worldId: Int,
    val invitedUserId: Int,
    val invitedBy: Int,
    val role: Role,              // Member or Admin only
    val status: InviteStatus,
    val createdAt: ZonedDateTime,
    val respondedAt: ZonedDateTime?
)
```

**InviteStatus** (Invitation states)

```kotlin
enum class InviteStatus {
    PENDING,   // Awaiting response
    ACCEPTED,  // User joined world
    DECLINED,  // User rejected
    CANCELLED  // Inviter cancelled
}
```

**Notification** (User alerts)

```kotlin
data class Notification(
    val id: Int,
    val userId: Int,
    val type: NotificationType,
    val relatedEntityType: String?, // "world", "project", "invite"
    val relatedEntityId: Int?,
    val message: String,
    val read: Boolean,
    val createdAt: ZonedDateTime,
    val readAt: ZonedDateTime?
)
```

**NotificationType** (Alert categories)

```kotlin
enum class NotificationType {
    INVITE_RECEIVED,    // New world invitation
    INVITE_ACCEPTED,    // Your invitation was accepted
    INVITE_DECLINED,    // Your invitation was declined
    PROJECT_COMPLETED,  // Project finished
    TASK_ASSIGNED,      // Task assigned to you
    DEPENDENCY_READY,   // Blocking dependency completed
    ROLE_CHANGED        // Your role in world changed
}
```

#### Dependencies & Resources

**ProjectDependency** (Project relationships)

```kotlin
data class ProjectDependency(
    val id: Int,
    val dependentProjectId: Int,  // This project requires...
    val dependencyProjectId: Int, // ...that project
    val createdBy: Int,
    val createdAt: ZonedDateTime
)
```

**ResourceProduction** (Project resources)

```kotlin
data class ResourceProduction(
    val id: Int,
    val projectId: Int,
    val itemId: String,          // Minecraft item ID
    val productionRate: Double,  // Items per hour
    val currentStock: Int,       // Current amount
    val active: Boolean,         // Currently producing
    val createdBy: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
```

---

## 🗄️ Database Schema

### Current State

**Migration Version**: V2_21_0 (49+ migrations total)
**Database**: PostgreSQL
**Migration Tool**: Flyway
**Schema Management**: Versioned SQL files in `src/main/resources/db/migration/`

### Core Tables

```sql
-- User Authentication
users
(id, username, minecraft_uuid, display_name, global_role, created_at, updated_at)
minecraft_profiles
    (user_id, uuid, username, last_login)

-- World Management
    worlds
(id, name, description, minecraft_version, created_by, created_at, updated_at)
world_members
    (world_id, user_id, role, joined_at)

-- Project Management
    projects
(id, world_id, name, description, type, stage, idea_id, created_by, created_at, updated_at)
project_locations
    (project_id, x, y, z, description)
    project_stage_changes
    (id, project_id, from_stage, to_stage, changed_by, changed_at)
    project_dependencies
(id, dependent_project_id, dependency_project_id, created_by, created_at)

-- Task Management (Split System - V2_20_0)
item_tasks
(id, project_id, name, description, priority, assigned_to, created_by, created_at, updated_at)
item_task_requirements
    (id, item_task_id, item_id, quantity_required, quantity_done)
    action_tasks
(id, project_id, name, description, priority, assigned_to, completed, created_by, created_at, updated_at)
action_task_requirements
    (id, action_task_id, description)

-- Ideas System (V2_12_0)
ideas
    (id, name, description, category, author JSONB, sub_authors JSONB[], labels TEXT[],
     favourites_count, rating_average, rating_count, difficulty,
     minecraft_version_range JSONB, category_data JSONB,
     created_by, created_at, updated_at)
idea_test_data
    (id, idea_id, mspt, hardware, minecraft_version, created_at)
idea_favourites
    (user_id, idea_id, created_at) -- PK: (user_id, idea_id)
idea_ratings
    (id, idea_id, rater_id, rater_name, score, content, created_at, updated_at)
idea_comments
    (id, idea_id, author_id, author_name, content, created_at, updated_at)

-- Collaboration
    invites
(id, world_id, invited_user_id, invited_by, role, status, created_at, responded_at)
notifications
(id, user_id, type, related_entity_type, related_entity_id, message, read, created_at, read_at)

-- Resources
project_productions
(id, project_id, item_id, production_rate, current_stock, active, created_by, created_at, updated_at)
```

### Key Relationships

```
users 1---N world_members N---1 worlds
users 1---N projects (creator)
worlds 1---N projects
projects 1---N item_tasks
projects 1---N action_tasks
item_tasks 1---N item_task_requirements
action_tasks 1---N action_task_requirements
projects N---N projects (via project_dependencies)
ideas 1---N projects (import source)
users 1---N invites (inviter)
users 1---N invites (invitee)
users 1---N notifications
```

### Indexing Strategy

**Performance-critical indexes:**

- `world_members(world_id, user_id)` - Access control lookups
- `projects(world_id)` - World project queries
- `item_tasks(project_id)` - Task retrieval
- `action_tasks(project_id)` - Task retrieval
- `project_dependencies(dependent_project_id)` - Dependency traversal
- `project_dependencies(dependency_project_id)` - Reverse lookup
- `notifications(user_id, read)` - Unread notifications
- `ideas(category)` - Category filtering

**JSONB indexes:**

- `ideas(category_data)` - GIN index for JSONB queries

### Migration History Highlights

**V1.x Series** (Initial development)

- V1_0_0: Initial schema
- V1_2_0 to V1_14: Iterative improvements

**V2.x Series** (Major refactor)

- V2_1_0: Worlds table
- V2_2_0: Users refactor
- V2_3_0: Invites system
- V2_4_0: Notifications system
- V2_5_0: Projects table
- V2_6_0: Tasks table
- V2_7_0: Project dependencies
- V2_10_0: Role integer conversion
- V2_12_0: Ideas system
- V2_17_0: Link projects to ideas
- V2_20_0 - V2_20_2: Task split (ItemTask/ActionTask)
- V2_21_0: Item ID cleanup

---

## 📦 Module Structure

### Package Organization

```
app.mcorg/
├── Application.kt                    # Main entry point, server config
├── config/                          # Configuration management
│   ├── ApiProvider.kt              # External API clients
│   └── DefaultApiProvider.kt
├── domain/                          # Business logic & domain models
│   ├── Env.kt                      # Environment variables
│   ├── model/                      # Domain entities
│   │   ├── admin/                  # Admin-related models
│   │   ├── idea/                   # Idea system models
│   │   │   ├── Idea.kt
│   │   │   ├── IdeaCategory.kt
│   │   │   ├── IdeaDifficulty.kt
│   │   │   └── schema/             # Dynamic schema system
│   │   ├── invite/                 # Invitation models
│   │   ├── minecraft/              # Minecraft-specific models
│   │   │   └── MinecraftVersion.kt
│   │   ├── notification/           # Notification models
│   │   ├── project/                # Project models
│   │   │   ├── Project.kt
│   │   │   ├── ProjectType.kt
│   │   │   ├── ProjectStage.kt
│   │   │   └── ProjectDependency.kt
│   │   ├── resources/              # Resource models
│   │   ├── task/                   # Task models
│   │   │   ├── ItemTask.kt
│   │   │   ├── ActionTask.kt
│   │   │   └── TaskPriority.kt
│   │   ├── user/                   # User models
│   │   │   ├── User.kt
│   │   │   ├── TokenProfile.kt
│   │   │   ├── Role.kt
│   │   │   └── GlobalUserRole.kt
│   │   └── world/                  # World models
│   │       ├── World.kt
│   │       └── WorldMember.kt
│   └── pipeline/                   # Pipeline infrastructure
│       ├── Step.kt                 # Step interface
│       ├── Result.kt               # Result<E, S> type
│       └── PipelineScope.kt         # Pipeline DSL with bind/run/parallel
├── nbt/                            # NBT file parsing (Litematica)
│   ├── tag/                        # NBT tag types
│   └── util/                       # NBT utilities
├── pipeline/                       # Application-level pipeline steps
│   ├── SafeSQL.kt                  # Type-safe SQL builder
│   ├── DatabaseSteps.kt            # Database operations
│   ├── ValidationSteps.kt          # Input validation
│   └── failure/                    # Error types
│       ├── AppFailure.kt           # Error hierarchy
│       └── ValidationFailure.kt    # Validation errors
└── presentation/                   # Web layer
    ├── consts/                     # Constants
    │   └── AuthConsts.kt
    ├── handler/                    # Request handlers
    │   ├── AdminHandler.kt
    │   ├── ErrorHandler.kt
    │   ├── HomeHandler.kt
    │   ├── IdeaHandler.kt
    │   ├── InviteHandler.kt
    │   ├── NotificationHandler.kt
    │   ├── ProfileHandler.kt
    │   ├── WorldHandler.kt
    │   ├── handleAuth.kt
    │   └── handleLanding.kt
    ├── hx.kt                       # HTMX helper functions
    ├── plugins/                    # Ktor plugins
    │   ├── AuthPlugin.kt           # JWT authentication
    │   ├── BannedPlugin.kt         # Ban enforcement
    │   ├── EnvPlugin.kt            # Environment validation
    │   ├── ProjectParamPlugin.kt   # Project ID extraction
    │   ├── TaskParamPlugin.kt      # Task ID extraction
    │   └── WorldParamPlugin.kt     # World ID extraction
    ├── router/                     # Route configuration
    │   ├── AppRouterV2.kt          # Main app routes
    │   ├── AuthRouter.kt           # Auth routes
    │   └── mainRouter.kt           # Root router
    ├── security/                   # Security utilities
    │   └── jwt.kt                  # JWT operations
    ├── templated/                  # HTML templates (Kotlin HTML DSL)
    │   ├── common/                 # Reusable components
    │   │   ├── button/
    │   │   ├── form/
    │   │   ├── icon/
    │   │   ├── layout/
    │   │   └── link/
    │   ├── pages/                  # Full page templates
    │   │   ├── home/
    │   │   ├── idea/
    │   │   ├── profile/
    │   │   ├── world/
    │   │   └── createPage.kt       # Base page template
    │   └── partials/               # Partial templates
    └── utils/                      # Presentation utilities
        ├── authUtils.kt            # Auth helpers (getUser, etc.)
        ├── BreadcrumbBuilder.kt    # Navigation breadcrumbs
        ├── envUtils.kt             # Environment helpers
        ├── htmlResponseUtils.kt    # respondHtml, respondBadRequest
        └── paramUtils.kt           # Parameter extraction
```

### Module Responsibilities

**domain/** - Pure business logic

- Domain entities (data classes)
- Business rules (validation, constraints)
- Pipeline abstractions (Step, Result)
- No Ktor dependencies

**pipeline/** - Application logic

- Database operations (SafeSQL, DatabaseSteps)
- Validation steps
- Error handling (AppFailure)
- Bridges domain and presentation

**presentation/** - Web layer

- HTTP request/response handling
- HTML template rendering
- Route configuration
- Authentication/authorization
- HTMX integration

**nbt/** - External format support

- NBT file parsing
- Litematica schematic imports
- Independent module

---

## 🛣️ Routing Architecture

### Route Hierarchy

```
/ (root)
├── / (GET) - Landing page
├── /test/
│   ├── /ping (GET) - Health check
│   └── /page (GET) - Development test page
├── /auth/
│   ├── /sign-in (GET) - Sign in page
│   ├── /sign-in/demo (POST) - Demo authentication
│   ├── /sign-in/microsoft (POST) - Microsoft authentication
│   ├── /sign-in/microsoft/callback (GET) - OAuth callback
│   └── /sign-out (GET) - Sign out
└── /app/ (requires authentication)
    ├── /home (GET) - User dashboard
    ├── /profile (GET) - User profile
    ├── /admin (GET) - Admin dashboard (GlobalUserRole required)
    ├── /notifications (GET) - Notifications list
    │   ├── /{id}/read (PATCH) - Mark as read
    │   └── /read (PATCH) - Mark all as read
    ├── /invites (GET) - User invitations
    │   ├── /{id}/accept (PATCH) - Accept invitation
    │   └── /{id}/decline (PATCH) - Decline invitation
    ├── /ideas (GET) - Ideas library
    │   ├── /search (GET) - Filter ideas
    │   ├── /create (GET, POST) - Create idea
    │   └── /{id} (GET) - Idea details
    └── /worlds/
        ├── / (POST) - Create world
        └── /{worldId}/ (requires world access)
            ├── / (GET) - World dashboard
            ├── / (PUT) - Update world
            ├── / (DELETE) - Delete world
            ├── /settings (GET) - World settings
            │   ├── /name (PATCH)
            │   ├── /description (PATCH)
            │   ├── /version (PATCH)
            │   ├── /invitations (POST) - Create invitation
            │   ├── /invitations/{id} (DELETE) - Cancel invitation
            │   ├── /members/role (PATCH) - Update role
            │   └── /members (DELETE) - Remove member
            ├── /projects/
            │   ├── / (POST) - Create project
            │   └── /{projectId}/ (requires project access)
            │       ├── / (GET) - Project details
            │       ├── / (PUT) - Update project
            │       ├── /stage (PATCH) - Update stage
            │       ├── /location (PATCH) - Update location
            │       ├── /tasks (POST) - Create task
            │       ├── /tasks/{taskId}/complete (PATCH) - Complete task
            │       ├── /tasks/{taskId} (DELETE) - Delete task
            │       ├── /resources (POST) - Add resource
            │       ├── /resources/{resourceId} (DELETE) - Delete resource
            │       ├── /dependencies/{dependencyProjectId} (POST) - Add dependency
            │       └── /dependencies/{dependencyProjectId} (DELETE) - Remove dependency
            └── /resources (GET) - World resources
```

### Plugin Pipeline

**Order of execution:**

1. **EnvPlugin** (all routes)
    - Validates environment configuration
    - Ensures required environment variables present

2. **AuthPlugin** (`/app/*` routes)
    - Validates JWT token
    - Extracts TokenProfile
    - Rejects unauthenticated requests

3. **BannedPlugin** (`/app/*` routes, after Auth)
    - Checks user ban status
    - Rejects banned users

4. **WorldParamPlugin** (`/app/worlds/{worldId}/*` routes)
    - Extracts worldId from path
    - Validates world exists
    - Checks user has world access

5. **ProjectParamPlugin** (`/app/worlds/{worldId}/projects/{projectId}/*` routes)
    - Extracts projectId from path
    - Validates project exists
    - Checks project belongs to world

6. **TaskParamPlugin** (`/app/worlds/{worldId}/projects/{projectId}/tasks/{taskId}/*` routes)
    - Extracts taskId from path
    - Validates task exists
    - Checks task belongs to project

### Route Registration Pattern

```kotlin
fun Route.featureRoutes() {
    route("/feature") {
        get {
            call.handleGetFeature()
        }
        post {
            call.handleCreateFeature()
        }
        route("/{id}") {
            install(FeatureParamPlugin)
            get {
                call.handleGetFeatureDetail()
            }
            put {
                call.handleUpdateFeature()
            }
            delete {
                call.handleDeleteFeature()
            }
        }
    }
}
```

---

## 🔐 Authentication & Authorization

### Authentication Flow

**JWT-Based Authentication:**

1. **Sign In**
    - User authenticates (demo or Microsoft OAuth)
    - Server generates JWT token with TokenProfile claims
    - Token stored in HTTP-only cookie

2. **Request Processing**
    - AuthPlugin extracts JWT from cookie
    - Validates token signature and expiration
    - Converts claims to TokenProfile
    - Makes TokenProfile available via `call.getUser()`

3. **Sign Out**
    - Clears JWT cookie
    - Redirects to landing page

**Token Structure:**

```json
{
  "sub": "user-id",
  "username": "player_name",
  "uuid": "minecraft-uuid",
  "displayName": "Display Name",
  "globalRole": "DEVELOPER",
  "iat": 1234567890,
  "exp": 1234567890
}
```

### Authorization Model

**Two-Level Permission System:**

#### 1. World-Level Roles

```kotlin
enum class Role(val level: Int) {
    OWNER(0),    // Full control
    ADMIN(10),   // Management
    MEMBER(100), // Standard access
    BANNED(1000) // No access
}
```

**Permission Comparison:**

```kotlin
fun Role.isHigherThanOrEqualTo(other: Role): Boolean {
    return this.level <= other.level  // Lower number = higher authority
}
```

**Role Capabilities:**

| Action                | Owner | Admin           | Member | Banned |
|-----------------------|-------|-----------------|--------|--------|
| View world            | ✅     | ✅               | ✅      | ❌      |
| Create projects       | ✅     | ✅               | ✅      | ❌      |
| Edit own projects     | ✅     | ✅               | ✅      | ❌      |
| Edit any project      | ✅     | ✅               | ❌      | ❌      |
| Delete projects       | ✅     | ✅               | ❌      | ❌      |
| Invite users (Member) | ✅     | ✅               | ❌      | ❌      |
| Invite users (Admin)  | ✅     | ✅               | ❌      | ❌      |
| Change member roles   | ✅     | ✅ (Member only) | ❌      | ❌      |
| Delete world          | ✅     | ❌               | ❌      | ❌      |

#### 2. Global System Roles

Global roles are stored as strings in the `global_user_roles` table. Users can have multiple global roles:

- **"superadmin"** - Full system access, can access admin dashboard
- **"moderator"** - Community management (also has superadmin privileges)
- **"idea_creator"** - Can create ideas in the idea library
- **"banned"** - Banned from the system
- **"demo_user"** - Demo account with write restrictions in production

**Checking global roles:**
```kotlin
val user = call.getUser()  // TokenProfile

if (user.isSuperAdmin) {
    // Full system access
}

if (user.isModerator) {
    // Moderator or superadmin
}

if (user.isIdeaCreator) {
    // Can create ideas
}

if (user.isBanned) {
    // User is banned
}
```

**Global Role Capabilities:**

- Access admin dashboard
- View all worlds
- Modify user global roles
- Ban/unban users
- Delete any world (emergency)
- View system metrics

### Access Control Pattern

**Standard authorization check:**

```kotlin
object ValidateWorldAccessStep : Step<Input, AppFailure.AuthError, Input> {
    override suspend fun process(input: Input): Result<AppFailure.AuthError, Input> {
        val membership = getWorldMembership(input.user.id, input.worldId)

        return if (membership != null && membership.role != Role.BANNED) {
            Result.success(input)
        } else {
            Result.failure(AppFailure.AuthError.NotAuthorized)
        }
    }
}
```

**Role-based authorization:**

```kotlin
object ValidateAdminRoleStep : Step<Input, AppFailure.AuthError, Input> {
    override suspend fun process(input: Input): Result<AppFailure.AuthError, Input> {
        val membership = getWorldMembership(input.user.id, input.worldId)
        val hasPermission = membership?.role?.isHigherThanOrEqualTo(Role.ADMIN) == true

        return if (hasPermission) {
            Result.success(input)
        } else {
            Result.failure(AppFailure.AuthError.NotAuthorized)
        }
    }
}
```

### Security Principles

1. **Default Deny** - All worlds private by default
2. **Explicit Access** - Invitation required for world access
3. **Role-Based** - Permissions tied to roles, not individual users
4. **Hierarchical** - Role levels allow easy comparison
5. **Immutable Owner** - Owner role cannot be transferred or removed
6. **Audit Trail** - All actions logged with user ID and timestamp

---

## 🌐 External API Integrations

### Overview

MC-ORG integrates with several external APIs for authentication and Minecraft-related data. All API calls use the `ApiProvider` pattern with built-in rate limiting, error handling, and testability.

### Integrated APIs

| API | Purpose | Config Object |
|-----|---------|---------------|
| **Microsoft OAuth** | User authentication via Microsoft accounts | `MicrosoftLoginApiConfig` |
| **Xbox Live** | Xbox authentication for Minecraft accounts | `XboxAuthApiConfig` |
| **XSTS** | Xbox Secure Token Service authorization | `XstsAuthorizationApiConfig` |
| **Minecraft Services** | Official Minecraft profile & auth | `MinecraftApiConfig` |
| **Modrinth** | Minecraft mod repository, game versions | `ModrinthApiConfig` |
| **Fabric MC** | Fabric modding framework versions | `FabricMcApiConfig` |
| **GitHub Gists** | Server JAR download links | `GithubGistsApiConfig` |

### Authentication Flow

```
User clicks "Sign in with Microsoft"
    ↓
Microsoft OAuth (MicrosoftLoginApiConfig)
    → Exchange code for access token
    ↓
Xbox Live Auth (XboxAuthApiConfig)
    → Exchange Microsoft token for Xbox token
    ↓
XSTS Auth (XstsAuthorizationApiConfig)
    → Exchange Xbox token for XSTS token
    ↓
Minecraft Services (MinecraftApiConfig)
    → Exchange XSTS token for Minecraft access
    → Fetch Minecraft profile (UUID, username)
    ↓
Create/update user record
Generate JWT token
Set HTTP-only cookie
```

### ApiProvider Pattern

```kotlin
// All API configs extend ApiConfig sealed class
sealed class ApiConfig(internal val baseUrl: String) {
    abstract fun getContentType(): ContentType
    fun getProvider(): ApiProvider  // Get DefaultApiProvider or FakeApiProvider
    fun useFakeProvider(responses: ...)  // Switch to mock for testing
}

// ApiProvider provides Step-based HTTP operations
sealed class ApiProvider(config: ApiConfig) {
    fun <I, S> get(url, headerBuilder): Step<I, AppFailure.ApiError, S>
    fun <I, S> post(url, headerBuilder, bodyBuilder): Step<I, AppFailure.ApiError, S>
    fun <I> getRaw(url, headerBuilder): Step<I, AppFailure.ApiError, InputStream>
}
```

### Built-in Features

**Rate Limiting:**
- Automatically tracks `X-RateLimit-*` headers
- Returns `AppFailure.ApiError.RateLimitExceeded` when limits hit
- Per-API-endpoint tracking

**Timeouts:**
- Request timeout: 30 seconds
- Connect timeout: 10 seconds
- Socket timeout: 30 seconds

**Error Handling:**
- `NetworkError` - Connection failures
- `TimeoutError` - Request timeouts
- `HttpError(statusCode, body)` - HTTP error responses
- `SerializationError` - JSON parsing failures

**Testing Support:**
```kotlin
// In tests, use fake provider
ModrinthApiConfig.useFakeProvider { method, url ->
    if (url.contains("/versions")) {
        Result.success("""{"versions": [...]}""")
    } else {
        Result.failure(AppFailure.ApiError.UnknownError)
    }
}

// Reset after tests
ModrinthApiConfig.resetProvider()
```

### Configuration

API base URLs can be overridden via environment variables:

```env
MODRINTH_BASE_URL=https://api.modrinth.com/v2
MICROSOFT_LOGIN_BASE_URL=https://login.microsoftonline.com
XBOX_AUTH_BASE_URL=https://user.auth.xboxlive.com
XSTS_AUTH_BASE_URL=https://xsts.auth.xboxlive.com
MINECRAFT_BASE_URL=https://api.minecraftservices.com
FABRIC_MC_BASE_URL=https://meta.fabricmc.net/v2
GITHUB_GISTS_BASE_URL=https://gist.githubusercontent.com
```

---

## 🔄 Data Flow

### Complete Request Flow Diagram

```
HTTP Request (POST /worlds/123/projects)
    ↓
[Ktor Routing Layer]
    ↓
    │
    ├──> [EnvPlugin] ────────────────────→ Reject if invalid environment
    │                                       (400 Bad Request)
    ↓
    ├──> [AuthPlugin] ───────────────────→ Reject if no/invalid JWT
    │                                       (401 Unauthorized)
    ↓
    ├──> [BannedPlugin] ─────────────────→ Reject if user banned
    │                                       (403 Forbidden)
    ↓
    ├──> [WorldParamPlugin] ─────────────→ Extract & validate worldId
    │                                       Store in call.attributes
    ↓
    ├──> [WorldAdminPlugin] ─────────────→ Reject if insufficient permissions
    │                                       Check world_members table
    │                                       (403 Forbidden)
    ↓
[Handler Function: ApplicationCall.handleCreateProject()]
    ↓
    Extract parameters (call.receiveParameters())
    Extract user (call.getUser())
    Extract worldId (call.getWorldId())
    ↓
    Execute Pipeline
    ↓
handlePipeline {
    ↓
    ValidateProjectNameStep.run(params)
        Input: ProjectParams
        Validates: name is 3-100 chars, non-empty
        Output: ValidatedParams | short-circuits with ValidationError
    ↓
    ValidateProjectTypeStep.run(validated)
        Input: ValidatedParams
        Validates: type is valid ProjectType enum
        Output: ValidatedParams | short-circuits with ValidationError
    ↓
    CheckCyclicDependenciesStep.run(validated)
        Input: ValidatedParams
        Validates: no circular dependency graph
        Output: ValidatedParams | short-circuits with BusinessRuleError
    ↓
    CreateProjectInDatabaseStep.run(validated)
        Input: ValidatedParams
        Database: INSERT INTO projects (...)
        Output: Project | short-circuits with DatabaseError
    ↓
    CreateNotificationStep.run(project)
        Input: Project
        Database: INSERT INTO notifications (...)
        Output: Notification | short-circuits with DatabaseError
    ↓
    Result<AppFailure, Project>
}
    ↓
    ├──> onSuccess(project) ────→ respondHtml(createHTML().div {
    │                               div("project-card") { /* Project HTML */ }
    │                             })
    │                             HTTP 200 OK with HTML fragment
    │                             HTMX updates #projects-list
    │
    └──> onFailure(error) ─────→ when (error) {
                                   ValidationError → respondBadRequest("Invalid input")
                                   DatabaseError → respondBadRequest("Operation failed")
                                   AuthError → respondForbidden("Access denied")
                                 }
                                 HTTP 400/403/500 with error HTML
    ↓
HTTP Response (HTML Fragment)
```

### Request Processing Flow

```
HTTP Request
    ↓
Ktor Server (Netty)
    ↓
Route Matching
    ↓
Plugin Pipeline (Env → Auth → Banned → Param extraction)
    ↓
Handler Function (ApplicationCall.handleXxx)
    ↓
handlePipeline {
    ↓
    Step 1: ValidateInputStep.run(params)
    ↓
    Step 2: Execute Business Logic (Domain operations)
    ↓
    Step 3: Database Operations (SafeSQL + DatabaseSteps)
    ↓
    Step 4: Get Updated Data
    ↓
    Success value (or short-circuits on failure)
}
    ↓
onSuccess: Generate HTML (Kotlin HTML DSL)
onFailure: Return Error Response
    ↓
HTTP Response (HTML)
```

### HTMX Interaction Flow

```
User Action (click button, submit form)
    ↓
HTMX Intercepts Event
    ↓
HTTP Request (PUT/PATCH/POST/DELETE with hx-* attributes)
    ↓
Server Processes Request (pipeline)
    ↓
Server Returns HTML Fragment (not full page)
    ↓
HTMX Receives Response
    ↓
HTMX Swaps Target Element (hxTarget)
    ↓
UI Updated (no full page reload)
```

### Database Transaction Flow

```
DatabaseSteps.transaction {
    ↓
    Set connection.autoCommit = false
    ↓
    Execute Step.process()
        ↓
        Query 1 (SELECT)
        Query 2 (INSERT/UPDATE/DELETE)
        Query 3 (UPDATE audit fields)
    ↓
    If Result.Success:
        connection.commit()
        Return success
    ↓
    If Result.Failure or Exception:
        connection.rollback()
        Return failure
    ↓
    Finally:
        Reset connection state
}
```

### Error Propagation Flow

```
Step.run() calls process() which returns Result.Failure
    ↓
.bind() throws PipelineFailure → short-circuits (remaining steps skipped)
    ↓
handlePipeline default error handler
    ↓
Match AppFailure type
    ↓
Generate appropriate response:
    - AuthError → 401/403
    - ValidationError → 400 with messages
    - DatabaseError → 500
    - Redirect → 302 with location
    ↓
respondHtml() or respondBadRequest()
    ↓
HTTP Response to client
```

---

## 🎯 Architectural Decision Records (ADRs)

### ADR-001: Server-Side HTML Generation

**Decision**: Use Kotlin HTML DSL + HTMX instead of SPA framework (React, Vue, Angular)

**Context**:
- Target audience: Minecraft players, often on mobile devices
- Need fast page loads and low JavaScript overhead
- Team expertise in Kotlin, not JavaScript ecosystem
- Desire for simple deployment and debugging

**Consequences**:
- ✅ Fast initial page loads (no JavaScript bundle download)
- ✅ Progressive enhancement (works without JavaScript)
- ✅ Type-safe HTML generation with Kotlin
- ✅ Server has full context (no API versioning issues)
- ❌ Must return HTML fragments from all endpoints
- ❌ Cannot use rich client-side libraries (charts, etc.)
- ❌ More server resources per request

**Implementation**: All handlers use `respondHtml()` with Kotlin HTML DSL, HTMX for dynamic updates

---

### ADR-002: Railway-Oriented Pipeline Pattern

**Decision**: Use Pipeline pattern with Result<E,S> and Step interface for all business logic

**Context**:
- Need explicit error handling (no uncaught exceptions)
- Want composable, testable business logic
- Desire clear separation between validation, business rules, and data access
- Team familiar with functional programming concepts

**Consequences**:
- ✅ Explicit error paths (no hidden exceptions)
- ✅ Highly testable (each Step is independent)
- ✅ Composable pipelines (reuse Steps)
- ✅ Clear data flow (input → output)
- ❌ More verbose than direct imperative code
- ❌ Learning curve for developers unfamiliar with pattern
- ❌ Every operation must be wrapped in a Step

**Implementation**: All business logic as Steps, composed into Pipelines, executed with `.fold()`

---

### ADR-003: Plugin-Based Authorization

**Decision**: Handle authorization at Ktor plugin layer on routes, NOT in business logic Steps

**Context**:
- Authorization is cross-cutting concern
- Want fail-fast security (reject before business logic)
- Need clear separation: routing layer = "who can access", business layer = "what happens"
- Ktor provides plugin architecture for this exact purpose

**Consequences**:
- ✅ Security enforced before handler execution
- ✅ Cannot accidentally forget auth check
- ✅ Clear separation of concerns
- ✅ Easy to audit (check routes, not business logic)
- ❌ Cannot have conditional auth within business logic
- ❌ Must extract entities (world, project) at route level
- ❌ Route definitions more complex

**Implementation**: `WorldAdminPlugin`, `ProjectMemberPlugin`, etc. installed on routes

---

### ADR-004: PostgreSQL with JSONB for Flexible Schema

**Decision**: Use PostgreSQL with JSONB columns for category-specific data in Ideas system

**Context**:
- Ideas have different attributes per category (Farm: afkable, Contraption: redstone_size, etc.)
- Don't want 50+ sparse columns or complex inheritance hierarchy
- Need to query/filter on these attributes efficiently
- PostgreSQL JSONB provides indexing and query support

**Consequences**:
- ✅ Flexible schema (add new categories without migrations)
- ✅ Can index JSONB with GIN indexes for performance
- ✅ Type-safe at application layer with sealed interface hierarchy
- ❌ Schema validation at application layer, not database
- ❌ JSONB queries more complex than column queries
- ❌ Cannot use foreign keys within JSONB

**Implementation**: `ideas.category_data JSONB` with GIN index, validated by Kotlin sealed classes

---

### ADR-005: Type-Safe SQL with SafeSQL Factory Methods

**Decision**: Use SafeSQL wrapper with factory methods (`.select()`, `.insert()`, etc.) instead of raw strings

**Context**:
- SQL injection is critical security risk
- Want compile-time validation of query types
- Need to distinguish SELECT from INSERT/UPDATE/DELETE for transaction handling
- Kotlin type system can enforce correct usage

**Consequences**:
- ✅ Prevents SQL injection (parameterized queries enforced)
- ✅ Compile-time query type checking
- ✅ Clear intent (`.select()` vs `.insert()`)
- ✅ Cannot accidentally use constructor with unsanitized input
- ❌ More verbose than raw strings
- ❌ Learning curve (must use factory methods)
- ❌ Cannot use for dynamic query building easily

**Implementation**: `SafeSQL` sealed class with private constructor, public factory methods

---

### ADR-006: Split Task System (ItemTask vs ActionTask)

**Decision**: Split unified tasks into ItemTask (material collection) and ActionTask (work steps)

**Context**: 
- V2_20_0 refactor to better match Minecraft workflow
- Material gathering is fundamentally different from building work
- ItemTasks need quantity tracking, ActionTasks need completion status
- Users think of these as separate concepts

**Consequences**:
- ✅ Better domain modeling (matches user mental model)
- ✅ Simpler UI (different forms for different task types)
- ✅ Clearer validation rules (quantity vs completion)
- ❌ More tables (item_tasks, action_tasks instead of one)
- ❌ Cannot easily convert between types
- ❌ Migration complexity (split existing tasks)

**Implementation**: Separate tables, handlers, and domain models since V2_20_0

---

## 📚 Additional Resources

- **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** - Quick orientation
- **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Implementation patterns
- **[BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)** - Domain rules
- **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)** - Styling guide
- **[PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)** - Feature status

---

**Document Version**: 2.1  
**Last Updated**: January 13, 2026  
**Maintained By**: Development Team

