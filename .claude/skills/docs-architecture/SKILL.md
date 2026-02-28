---
name: docs-architecture
description: Architecture reference for MC-ORG. Use when determining where to place new code, understanding the domain model, setting up route plugins, learning about the plugin chain, or navigating the file structure.
user-invocable: false
---

# Architecture Reference

System structure, domain model, plugin chain, file locations for MC-ORG.

---

## Architecture Decisions (Why)

- **HTML-only responses** — No JSON APIs. All endpoints return HTML fragments or full pages.
- **HTMX** — Dynamic updates without JavaScript framework. Server sends HTML, HTMX swaps it.
- **Pipeline + Steps** — Railway-oriented programming. Every business operation is a Step.
- **Plugin-based auth** — Authorization enforced in Ktor plugins at route level, NOT inside pipelines.
- **SafeSQL** — Prevents SQL injection. Factory methods enforce parameterized queries.

---

## Plugin Chain (Request Flow)

```
HTTP Request
  ↓ EnvPlugin        — environment validation
  ↓ AuthPlugin       — JWT validation, extracts TokenProfile
  ↓ BannedPlugin     — checks global ban status
  ↓ WorldParamPlugin — extracts + validates worldId from path
  ↓ ProjectParamPlugin — extracts + validates projectId
  ↓ TaskParamPlugin  — extracts + validates taskId
  ↓ Handler          — business logic (only runs if all plugins pass)
```

### Route with plugins example

```kotlin
fun Route.worldRoutes() {
    route("/worlds/{worldId}") {
        install(WorldParamPlugin)   // extracts worldId, checks membership
        install(WorldAdminPlugin)   // checks ADMIN role

        put {
            call.handleUpdateWorld()  // only runs if authorized
        }

        route("/projects") {
            post { call.handleCreateProject() }

            route("/{projectId}") {
                install(ProjectParamPlugin)

                get { call.handleGetProject() }
                put { call.handleUpdateProject() }
                delete { call.handleDeleteProject() }
            }
        }
    }
}
```

---

## Domain Model

### User

```kotlin
data class TokenProfile(
    val id: Int,                    // use user.id — NOT user.userId
    val uuid: String,               // Minecraft UUID
    val minecraftUsername: String,
    val displayName: String,
    val roles: List<String>         // "superadmin", "moderator", "idea_creator", "banned"
) {
    val isSuperAdmin: Boolean       // roles.contains("superadmin")
    val isModerator: Boolean        // isSuperAdmin || "moderator"
    val isIdeaCreator: Boolean      // isSuperAdmin || "idea_creator"
    val isBanned: Boolean           // roles.contains("banned")
}
```

### World & Membership

```kotlin
data class World(val id: Int, val name: String, val description: String,
                 val minecraftVersion: MinecraftVersion, val createdBy: Int, ...)

data class WorldMember(val worldId: Int, val userId: Int, val role: Role, val joinedAt: ZonedDateTime)

enum class Role(val level: Int) {
    OWNER(0), ADMIN(10), MEMBER(100), BANNED(1000)
}
// Lower number = higher authority
// Check: role.isHigherThanOrEqualTo(Role.ADMIN)
```

### Project

```kotlin
data class Project(val id: Int, val worldId: Int, val name: String, val description: String,
                   val type: ProjectType, val stage: ProjectStage, val ideaId: Int?, val createdBy: Int, ...)

enum class ProjectType { BUILDING, CONTRAPTION, INFRASTRUCTURE, DECORATION, OTHER }
enum class ProjectStage { PLANNING, DESIGN, RESOURCE_GATHERING, BUILDING, REVIEW, COMPLETE, ARCHIVED }
```

### Tasks

```kotlin
data class ItemTask(val id: Int, val projectId: Int, val name: String, val priority: TaskPriority,
                    val requirements: List<ItemRequirement>, ...)

data class ItemRequirement(val itemId: String, val quantityRequired: Int, val quantityDone: Int)

data class ActionTask(val id: Int, val projectId: Int, val name: String, val priority: TaskPriority,
                      val requirement: ActionRequirement, val completed: Boolean, ...)

data class ActionRequirement(val description: String)

enum class TaskPriority { CRITICAL, NORMAL, NICE_TO_HAVE }
```

### Other Entities

```kotlin
data class Idea(val id: Int, val name: String, val category: IdeaCategory,
                val difficulty: IdeaDifficulty, val categoryData: Map<String, CategoryValue>, ...)

data class Invite(val id: Int, val worldId: Int, val invitedUserId: Int, val role: Role,
                  val status: InviteStatus, ...)
enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

data class Notification(val id: Int, val userId: Int, val type: NotificationType,
                        val message: String, val read: Boolean, ...)
```

---

## Module Structure

All modules live under `webapp/`. Dependencies flow inward: `mc-web` → `mc-data`/`mc-engine`/`mc-nbt` → `mc-domain`/`mc-pipeline`.

```
webapp/
├── mc-domain/           # Pure domain models, no dependencies
│   └── src/main/kotlin/app/mcorg/domain/model/
│       ├── user/        # User, Role
│       ├── world/       # World, WorldStatistics, Roadmap
│       ├── project/     # Project, ProjectStage, ProjectType
│       ├── task/        # ActionTask, TaskPriority
│       ├── idea/        # Idea, IdeaCategory, IdeaDifficulty, Comment
│       ├── invite/      # Invite, InviteStatus
│       ├── notification/# Notification
│       ├── resources/   # ResourceSource, ResourceQuantity, ResourceMap
│       ├── minecraft/   # Item, MinecraftVersion, Litematica, Dimension
│       └── admin/       # AdminStatistics, ManagedUser, ManagedWorld
│
├── mc-pipeline/         # Generic pipeline framework, no dependencies
│   └── src/main/kotlin/app/mcorg/pipeline/
│       ├── Step.kt      # Step<I, E, S> interface
│       ├── Result.kt    # Result<E, V> sealed class (Success/Failure)
│       ├── PipelineScope.kt
│       └── MergeSteps.kt
│
├── mc-engine/           # Game logic — depends on mc-domain
│   └── src/main/kotlin/app/mcorg/engine/
│       ├── model/       # ItemSourceGraph, ResourceGatheringPlan
│       └── service/     # PathSuggestionService, graph building & scoring
│
├── mc-nbt/              # NBT binary parser — depends on mc-domain, mc-pipeline
│   └── src/main/kotlin/app/mcorg/nbt/
│       ├── io/          # BinaryNbtDeserializer, input streams
│       ├── tag/         # Tag sealed class (TAG_BYTE, TAG_COMPOUND, etc.)
│       ├── util/        # LitematicaReader
│       └── failure/     # NBTFailure, BinaryParseFailure
│
├── mc-data/             # Minecraft data extraction — depends on mc-domain, mc-pipeline
│   └── src/main/kotlin/app/mcorg/data/minecraft/
│       ├── extract/     # Items, recipes, loot tables, tags
│       │   ├── recipe/  # Shaped, shapeless, smelting, smithing parsers
│       │   └── loot/    # Loot table & pool parsers
│       └── failure/     # ExtractionFailure
│
└── mc-web/              # HTTP layer — depends on all modules
    └── src/main/kotlin/app/mcorg/
        ├── Application.kt
        ├── config/                        # AppConfig, ApiProvider
        ├── pipeline/
        │   ├── SafeSQL.kt
        │   ├── DatabaseSteps.kt
        │   ├── ValidationSteps.kt
        │   ├── failure/                   # AppFailure, ValidationFailure
        │   └── {feature}/                 # Feature pipelines, extractors, validators
        └── presentation/
            ├── handler/                   # Request handlers (one file per feature)
            ├── plugins/                   # Ktor plugins (Auth, Role, Param extraction)
            ├── router/                    # Route configuration (AppRouterV2.kt)
            ├── hx.kt                      # HTMX helper functions
            ├── templated/
            │   ├── common/                # Reusable components
            │   ├── pages/                 # Full page templates + createPage.kt
            │   └── partials/              # Partial templates
            └── utils/                     # authUtils, htmlResponseUtils, paramUtils
    └── src/main/resources/
        ├── db/migration/                  # Flyway SQL migrations
        └── static/
            ├── styles/                    # CSS files
            └── icons/                     # SVG icons
```

## Where to Put New Code

| New code type         | Module         | Location                                       |
|-----------------------|----------------|-------------------------------------------------|
| Domain entity         | `mc-domain`    | `domain/model/{feature}/`                       |
| Pipeline step (generic)| `mc-pipeline` | `pipeline/`                                     |
| Game/resource logic   | `mc-engine`    | `engine/service/` or `engine/model/`            |
| NBT/schematic parsing | `mc-nbt`       | `nbt/`                                          |
| Minecraft data parser | `mc-data`      | `data/minecraft/extract/`                       |
| Handler               | `mc-web`       | `presentation/handler/{Feature}Handler.kt`      |
| Pipeline step (app)   | `mc-web`       | `pipeline/{feature}/`                           |
| Route                 | `mc-web`       | `presentation/router/AppRouterV2.kt`            |
| Full page template    | `mc-web`       | `presentation/templated/pages/{feature}/`       |
| Component             | `mc-web`       | `presentation/templated/common/`                |
| DB migration          | `mc-web`       | `src/main/resources/db/migration/`              |

---

## Database Schema (Core Tables)

```sql
users (id, username, minecraft_uuid, display_name, created_at, updated_at)
worlds (id, name, description, minecraft_version, created_by, created_at, updated_at)
world_members (world_id, user_id, role, joined_at)
projects (id, world_id, name, description, type, stage, idea_id, created_by, created_at, updated_at)
item_tasks (id, project_id, name, description, priority, assigned_to, created_by, ...)
item_task_requirements (id, item_task_id, item_id, quantity_required, quantity_done)
action_tasks (id, project_id, name, description, priority, assigned_to, completed, ...)
action_task_requirements (id, action_task_id, description)
ideas (id, name, description, category, author JSONB, labels TEXT[], category_data JSONB, ...)
invites (id, world_id, invited_user_id, invited_by, role, status, created_at, responded_at)
notifications (id, user_id, type, related_entity_type, related_entity_id, message, read, ...)
project_dependencies (id, dependent_project_id, dependency_project_id, created_by, created_at)
```

Migration files are in `mc-web/src/main/resources/db/migration/`.
