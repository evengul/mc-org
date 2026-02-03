# MC-ORG Glossary

**Quick reference for technical and domain terminology**

---

## 📘 Technical Terms

### Architecture & Patterns

**Pipeline**

- A composition of Steps that transform input → output with explicit error handling
- Uses railway-oriented programming pattern
- Short-circuits on first failure
- Example: `Pipeline.create().pipe(Step1).pipe(Step2)`

**Step**

- A single unit of business logic implementing `Step<I,E,S>` interface
- Generic types: Input (I), Error (E), Success (S)
- Returns `Result<E, S>` indicating success or failure
- Testable in isolation

**Result**

- Railway-oriented programming type representing either Success or Failure
- `Result.success(value)` - successful outcome
- `Result.failure(error)` - failed outcome with error details
- Never throws exceptions for business logic errors

**ApiConfig**

- Sealed class defining configuration for external API endpoints
- Each external API (Microsoft, Xbox, Minecraft, Modrinth, etc.) has its own config object
- Contains base URL, content types, and User-Agent settings
- Supports fake providers for testing via `useFakeProvider()`

**ApiProvider**

- Sealed class providing HTTP client operations for external API calls
- Methods: `get()`, `post()`, `getRaw()` - all return Step types
- Built-in rate limiting via X-RateLimit headers
- `DefaultApiProvider` for real HTTP, `FakeApiProvider` for testing

**AppFailure**

- Sealed interface hierarchy for all application errors
- Subtypes: ValidationError, DatabaseError, AuthError, ApiError, Redirect, FileError
- Forces explicit error handling

**executePipeline**

- Handler helper function that executes a pipeline with default error handling
- Syntax: `executePipeline(onSuccess = { ... }) { pipeline DSL }`
- Automatically handles `onFailure` with appropriate HTTP responses
- Preferred over manual `Pipeline.create().fold()` pattern

**executeParallelPipeline**

- Handler helper for parallel pipeline execution
- Executes multiple independent operations concurrently
- Merges results using type-safe lambdas
- Uses DAG-based dependency resolution

**ParallelPipelineBuilder**

- Builder for creating pipelines with concurrent execution
- Methods: `singleStep()`, `pipeline()`, `merge()`, `pipe()`
- Creates DAG of operations with dependency tracking
- Used via `parallelPipeline<E> { }` builder function

**PipelineBuilder**

- Fluent DSL for constructing sequential pipelines
- Methods: `.step()`, `.map()`, `.value()`, `.validate()`, `.transform()`, `.execute()`
- Alternative to `Pipeline.create().pipe()` pattern
- Used via `pipeline<I, E>()` builder function

**PipelineRef**

- Reference to a node in a parallel pipeline graph
- Returned by `singleStep()`, `pipeline()`, `merge()` in ParallelPipelineBuilder
- Used as dependency input for `merge()` and `pipe()` operations
- Type-safe tracking of node outputs

**SafeSQL**

- SQL query wrapper providing injection protection
- Factory methods: `.select()`, `.insert()`, `.update()`, `.delete()`
- Private constructor prevents direct string usage
- Validates query structure before execution

**HTMX**

- JavaScript library for dynamic HTML updates without full page reload
- Uses attributes: `hx-get`, `hx-post`, `hx-target`, `hx-swap`
- Server returns HTML fragments, not JSON
- Enables progressive enhancement

**Plugin (Ktor)**

- Ktor extension that intercepts requests before handler execution
- Used for cross-cutting concerns: auth, parameter extraction, validation
- Installed on routes or globally
- Examples: `WorldAdminPlugin`, `AuthPlugin`, `BannedPlugin`

**Handler**

- Function that processes HTTP requests and generates responses
- Signature: `suspend ApplicationCall.handleXxx()`
- Creates and executes pipelines
- Returns HTML via `respondHtml()` or errors via `respondBadRequest()`

**DatabaseSteps**

- Utility object for database operations within Steps
- Methods: `.query()`, `.queryList()`, `.transaction()`
- Works with SafeSQL and prepared statements
- Handles connection management and error conversion

**Kotlin HTML DSL**

- Type-safe HTML generation using Kotlin code
- `createHTML().div { ... }` produces HTML string
- Import: `kotlinx.html.stream.createHTML`
- Server-side rendering (not browser-side)

---

## 🏗️ Domain Terms

### Core Entities

**World**

- A Minecraft world/server containing projects and members
- Has name, description, Minecraft version
- Owner has full control, can assign roles
- All worlds are private (invitation-only)

**Project**

- A building task or system within a World
- Has type (FARM, VILLAGE, CONTRAPTION, etc.)
- Has stage (PLANNING, BUILDING, DECORATING, COMPLETE, ABANDONED)
- Can have dependencies on other projects
- Can be created from an Idea

**Idea**

- A design template or inspiration for projects
- Has category (FARM, CONTRAPTION, UTILITY, etc.)
- Contains category-specific data (e.g., production rate for farms)
- Can have ratings, comments, favourites
- Can be imported to create projects

**Task**

- Work item within a project
- Two types: ItemTask (material gathering) and ActionTask (building steps)
- Has priority: CRITICAL, NORMAL, NICE_TO_HAVE
- Can be assigned to world members
- Tracks completion status

**ItemTask**

- Task for collecting materials/resources
- Has requirements: item type + quantity needed
- Tracks quantity done vs. required
- Example: "Collect 64 stacks of stone"

**ActionTask**

- Task for performing work steps
- Boolean completion (done or not done)
- Has action requirements (step-by-step descriptions)
- Example: "Place foundation blocks" → "Wire redstone circuit"

**Member**

- A user's membership in a specific World
- Has role: OWNER, ADMIN, MEMBER, BANNED
- Determines permissions within that world
- User can be member of multiple worlds

**Invitation**

- Request to join a World
- Status: PENDING, ACCEPTED, REJECTED, EXPIRED
- Specifies role user will receive on acceptance
- Expires after 7 days if not responded to

**Notification**

- System-generated message for user
- Types: INVITE_RECEIVED, PROJECT_COMPLETED, TASK_ASSIGNED, etc.
- Has read/unread state
- Links to related entity (world, project, task)

**Dependency**

- Relationship between projects indicating build order
- Dependent project requires dependency project to complete first
- Circular dependencies are prevented
- Stored in `project_dependencies` table

---

## 👤 User Roles & Permissions

### World-Level Roles

**OWNER (Role Level 0)**

- Creator of the world
- Full administrative privileges
- Can delete world
- Cannot transfer ownership

**ADMIN (Role Level 10)**

- Can invite users and manage members
- Can create, edit, delete projects
- Can manage world settings (except deletion)
- Can assign MEMBER or ADMIN roles to others

**MEMBER (Role Level 100)**

- Can create and edit own projects
- Can create and complete tasks
- Can view all world content
- Cannot invite others or manage settings

**BANNED (Role Level 1000)**

- No access to world
- Cannot view or perform any operations
- Can be re-invited to restore access

### Global User Roles

**GlobalUserRole.ADMIN (Superadmin)**

- System-wide administrator
- Can access any world (bypasses membership checks)
- Can manage user accounts
- Can view system metrics

**GlobalUserRole.MODERATOR**

- Community moderation powers
- Can handle user reports
- Cannot access all worlds automatically

**GlobalUserRole.IDEA_CREATOR**

- Can create and manage ideas in idea library
- Standard permission for most users
- Required to submit new idea templates

**GlobalUserRole.USER (Default)**

- Standard user account
- Can join worlds via invitation
- Can create worlds (becomes owner)
- Cannot create ideas (must request upgrade)

---

## 🔐 Authentication & Authorization

**JWT (JSON Web Token)**

- Token-based authentication mechanism
- Issued on login, stored in cookie
- Contains user ID, username, global role
- Validated by `AuthPlugin` on each request

**TokenProfile**

- Decoded JWT payload as Kotlin data class
- Properties: `id`, `username`, `role`
- Retrieved via `call.getUser()`
- Available in handlers after `AuthPlugin` validation

**Session**

- Server-side user session (not implemented)
- Currently using stateless JWT auth
- No server-side session storage

---

## 🗄️ Database Terms

**Migration**

- Versioned SQL file for schema changes
- Managed by Flyway
- Naming: `V{version}__{description}.sql`
- Example: `V2_12_0__create_ideas_system.sql`

**JSONB**

- PostgreSQL data type for JSON with indexing
- Used for flexible schema (e.g., `ideas.category_data`)
- Can be queried with JSON operators
- Indexed with GIN indexes for performance

**CTE (Common Table Expression)**

- WITH clause in SQL for complex queries
- Can be recursive (for dependency trees)
- Makes queries more readable
- Example: Project dependency traversal

**Cascade Delete**

- `ON DELETE CASCADE` foreign key constraint
- Automatically deletes dependent records
- Example: Deleting project deletes all its tasks

**Audit Fields**

- Tracking columns: `created_by`, `created_at`, `updated_at`
- Present on most tables
- Used for history and troubleshooting

---

## 🎨 Frontend Terms

**Component Class**

- Reusable CSS class for UI elements
- Example: `.btn`, `.card`, `.form-control`
- Follows BEM naming convention
- Lives in `styles/components/`

**Utility Class**

- Single-purpose CSS class
- Prefix: `u-` (e.g., `.u-flex`, `.u-margin-md`)
- Combines with component classes
- Lives in `styles/utilities.css`

**Design Token**

- CSS custom property for design values
- Example: `--clr-action`, `--spacing-md`, `--shadow-sm`
- Ensures consistency across UI
- Defined in `styles/root.css`

**BEM (Block Element Modifier)**

- CSS naming convention
- Block: `.card`
- Element: `.card__header`
- Modifier: `.card--elevated`

---

## 📋 Testing Terms

**TestDataFactory**

- Utility for creating test fixtures
- Methods like `createTestWorld()`, `createTestProject()`
- Provides realistic test data
- Used in integration and unit tests

**Integration Test**

- Tests multiple components together
- Example: Handler + Pipeline + Database
- Uses real database connections
- Slower than unit tests

**Unit Test**

- Tests single Step in isolation
- Fast execution
- No database dependencies
- Mocks external dependencies

---

## 🔧 Build & Deployment Terms

**Maven**

- Java/Kotlin build tool (not Gradle)
- `pom.xml` defines dependencies and build config
- Commands: `mvn clean compile`, `mvn test`, `mvn package`

**Flyway**

- Database migration tool
- Automatically applies versioned SQL files
- Tracks applied migrations in `flyway_schema_history` table
- Runs on application startup

**Fly.io**

- Cloud hosting platform
- Runs Docker containers
- Configuration in `fly.toml`
- Deployment: `fly deploy`

---

## 📖 Acronyms

- **ADR**: Architecture Decision Record
- **API**: Application Programming Interface
- **BEM**: Block Element Modifier
- **CTE**: Common Table Expression
- **DSL**: Domain-Specific Language
- **HTMX**: HTML eXtensions (library)
- **JWT**: JSON Web Token
- **NBT**: Named Binary Tag (Minecraft file format)
- **RBAC**: Role-Based Access Control
- **SQL**: Structured Query Language
- **TDD**: Test-Driven Development
- **UI**: User Interface
- **UUID**: Universally Unique Identifier

---

## 🔗 Related Documentation

- **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** - Quick orientation
- **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)** - System architecture
- **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Implementation patterns
- **[BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)** - Domain rules
- **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)** - Styling guide

---

**Document Version**: 1.0  
**Last Updated**: January 13, 2026  
**Maintained By**: Development Team

