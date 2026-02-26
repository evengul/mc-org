# Database Caching Plan

## Current State

**Zero caching** exists today. Every request executes fresh queries against a remote Neon PostgreSQL instance.

### Query volume per request

The plugin chain stacks existence-check queries before the handler even runs:

| Request depth | Plugin chain queries | Example route |
|---|---|---|
| `/app/*` | 1 (BannedPlugin) | Home page |
| `/app/worlds/{id}` | 2 (+ WorldParamPlugin) | World page |
| `/app/worlds/{id}/projects/{id}` | 3 (+ ProjectParamPlugin) | Project page |
| `/app/worlds/{id}/projects/{id}/tasks/{id}` | 4 (+ ActionTaskParamPlugin) | Task action |
| `/app/worlds/{id}/projects/{id}/resources/gathering/{id}` | 4 (+ ResourceGatheringIdParamPlugin) | Resource action |
| `/app/worlds/{id}/settings/*` | 3 (+ WorldAdminPlugin via ValidateWorldMemberRole) | Settings page |

The handler then runs 1-5 additional queries. A typical project page request hits **5-8 queries**, all over the network to Neon.

Additionally, `getUnreadNotificationsOrZero()` runs on nearly every page load (referenced in 9 pipelines), and `GetSupportedVersionsStep` runs on 6 different pages with data that almost never changes.

### Connection pool

- Production: max 10 connections, min idle 2
- Local: max 5 connections, min idle 1
- With 6-8 queries per request, the pool is under heavy contention during concurrent use

### Database operation totals

| Operation | Count | Target |
|---|---|---|
| `DatabaseSteps.query()` | 90 | Primary cache targets (reads) |
| `DatabaseSteps.update()` | 54 | Cache invalidation triggers (writes) |
| `DatabaseSteps.transaction()` | 10 | Multi-step invalidation triggers |
| `DatabaseSteps.batchUpdate()` | 7 | Bulk invalidation triggers |

---

## Recommended approach: In-process cache with Caffeine

### Why Caffeine, not Redis

| Factor | Caffeine | Redis |
|---|---|---|
| Latency | Sub-microsecond (heap access) | 1-5ms network roundtrip |
| Infrastructure | Zero (JVM library) | Separate service to run/pay for |
| Deployment fit | Single Fly.io instance | Overkill for single-instance |
| Complexity | One dependency | Connection management, serialization, another failure mode |
| Memory | Bounded, configurable per cache | Separate memory budget |

Caffeine is the right choice because MC-ORG runs as a single instance on Fly.io and the dataset is small (collaboration tool, not millions of users). If the app scales to multiple instances, a shared cache (Redis) or cache-aside with short TTLs can be revisited.

### Dependency

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

---

## Cache tiers

### Tier 1: Plugin existence checks (highest impact)

These fire on **every single request** and are simple boolean lookups. Caching them eliminates 2-4 queries per request.

| Cache | Key | Query eliminated | TTL | Max size |
|---|---|---|---|---|
| `bannedUsers` | `userId: Int` | `SELECT 1 FROM global_user_roles WHERE user_id = ? AND role = 'banned'` | 5 min | 1,000 |
| `worldExists` | `worldId: Int` | `SELECT EXISTS(SELECT 1 FROM world WHERE id = ?)` | 10 min | 500 |
| `projectExists` | `worldId:projectId` | `SELECT EXISTS(SELECT 1 FROM projects WHERE id = ? AND world_id = ?)` | 10 min | 2,000 |
| `taskExists` | `projectId:taskId` | `SELECT EXISTS(SELECT 1 FROM action_task WHERE id = ? AND project_id = ?)` | 5 min | 5,000 |
| `resourceGatheringExists` | `projectId:rgId` | `SELECT EXISTS(SELECT 1 FROM resource_gathering WHERE id = ? AND project_id = ?)` | 5 min | 5,000 |
| `worldMemberRole` | `userId:worldId` | `SELECT EXISTS(SELECT 1 FROM world_members WHERE user_id = ? AND world_id = ? AND world_role <= ?)` | 5 min | 2,000 |
| `ideaExists` | `ideaId: Int` | `SELECT EXISTS(SELECT 1 FROM ideas WHERE id = ?)` | 10 min | 1,000 |

**Estimated savings**: 2-4 DB round-trips eliminated per request.

**Invalidation**:
- `bannedUsers`: Invalidate on role change (admin action, infrequent)
- `worldExists`: Invalidate on world create/delete
- `projectExists`: Invalidate on project create/delete
- `taskExists`: Invalidate on task create/delete
- `resourceGatheringExists`: Invalidate on resource create/delete
- `worldMemberRole`: Invalidate on member add/remove/role-change
- `ideaExists`: Invalidate on idea create/delete

### Tier 2: Static/near-static reference data

Data that changes extremely rarely but is fetched on many page loads.

| Cache | Key | Current behavior | TTL | Max size |
|---|---|---|---|---|
| `supportedVersions` | singleton | `SELECT DISTINCT version FROM minecraft_version` - called on 6+ pages | 1 hour | 1 |
| `unreadNotificationCount` | `userId: Int` | `SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL` - called on ~9 pages | 1 min | 1,000 |

**Estimated savings**: 1-2 DB round-trips per page load.

**Invalidation**:
- `supportedVersions`: Invalidate when minecraft version data is updated (batch import, very rare)
- `unreadNotificationCount`: Invalidate on notification create/read. Short TTL (1 min) makes staleness acceptable.

### Tier 3: Entity lookups by ID

Frequently accessed entities that are loaded by primary key. These are the handler-level queries that run after the plugin chain.

| Cache | Key | Query | TTL | Max size |
|---|---|---|---|---|
| `worldById` | `worldId: Int` | GetWorldStep (JOIN with project counts) | 5 min | 200 |
| `projectById` | `projectId: Int` | GetProjectByIdStep (JOIN with idea + task stats subqueries) | 5 min | 1,000 |
| `ideaById` | `ideaId: Int` | GetIdeaStep (JOIN with json_agg test data) | 10 min | 500 |
| `userPermittedWorlds` | `userId: Int` | GetPermittedWorldsStep | 5 min | 500 |

**Estimated savings**: 1-2 DB round-trips per page load for repeat visits.

**Invalidation**:
- `worldById`: Invalidate on world update (name, description, version)
- `projectById`: Invalidate on project update, task create/complete/delete, resource changes
- `ideaById`: Invalidate on idea update, comment add/delete, favourite toggle
- `userPermittedWorlds`: Invalidate on world create/delete, member add/remove, invite accept

### Tier 4: Search/list results (optional, lower priority)

These are parameterized queries with dynamic filters. Caching is possible but has lower hit rates.

| Cache | Key | Query | TTL | Max size |
|---|---|---|---|---|
| `projectSearch` | `worldId:sort:query:stage` hash | SearchProjectsStep | 2 min | 500 |
| `ideaSearch` | filter hash | SearchIdeasPipeline | 5 min | 200 |
| `allIdeas` | singleton | GetAllIdeasStep (loads entire ideas library) | 15 min | 1 |

Only implement if Tier 1-3 are insufficient. The short TTLs mean stale list results resolve quickly.

---

## Implementation design

### CacheManager singleton

```kotlin
object CacheManager {
    // Tier 1: Existence checks
    val bannedUsers: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    val worldExists: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    // ... other caches

    // Tier 2: Reference data
    val supportedVersions: Cache<Unit, List<MinecraftVersion.Release>> = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build()

    // Invalidation helpers
    fun onWorldCreated(worldId: Int) {
        worldExists.invalidate(worldId)
    }

    fun onWorldDeleted(worldId: Int) {
        worldExists.invalidate(worldId)
        worldById.invalidate(worldId)
    }

    fun onProjectChanged(projectId: Int) {
        projectById.invalidate(projectId)
    }

    fun onMemberRoleChanged(userId: Int, worldId: Int) {
        worldMemberRole.invalidate("$userId:$worldId")
        userPermittedWorlds.invalidate(userId)
    }
    // ...
}
```

### CachedStep wrapper

A decorator that wraps any `DatabaseSteps.query` step with cache-aside logic:

```kotlin
class CachedStep<I, S>(
    private val cache: Cache<I, S>,
    private val delegate: Step<I, AppFailure.DatabaseError, S>
) : Step<I, AppFailure.DatabaseError, S> {
    override suspend fun process(input: I): Result<AppFailure.DatabaseError, S> {
        cache.getIfPresent(input)?.let { return Result.success(it) }

        return delegate.process(input).also { result ->
            if (result is Result.Success) {
                cache.put(input, result.value)
            }
        }
    }
}
```

This lets us wrap existing steps without changing their implementation:

```kotlin
// Before (no cache)
val step = DatabaseSteps.query<Int, Boolean>(sql, setter, mapper)

// After (cached)
val step = CachedStep(CacheManager.worldExists, DatabaseSteps.query<Int, Boolean>(sql, setter, mapper))
```

### Invalidation integration points

Write operations must invalidate related caches. The cleanest approach is to add invalidation calls right after the write step succeeds in each pipeline:

```kotlin
// In CreateProjectPipeline, after the insert step succeeds:
handlePipeline(onSuccess = { ... }) {
    val projectId = CreateProjectStep.run(input)
    CacheManager.onProjectCreated(worldId)  // invalidate world's project list
    GetProjectByIdStep.run(projectId)
}
```

For transactions, invalidation should happen **after commit** (already guaranteed by the pipeline pattern since invalidation runs in the success path after the transaction step completes).

---

## Implementation phases

### Phase 1: Plugin existence caches (biggest bang for buck)

**Files to modify:**
- New: `webapp/src/main/kotlin/app/mcorg/config/CacheManager.kt`
- Modify: `presentation/plugins/ParamPlugins.kt` - wrap each `ensureParamEntityExists` call
- Modify: `presentation/plugins/RolePlugins.kt` - wrap `BannedPlugin` query and `ValidateWorldMemberRole`
- Modify: `pipeline/world/ValidateWorldMemberRole.kt` - check cache before DB

**Invalidation triggers (files to modify):**
- `pipeline/world/CreateWorldPipeline.kt` - invalidate `worldExists`
- `pipeline/world/settings/general/*` - invalidate `worldById` on name/desc/version change
- `pipeline/world/DeleteWorldPipeline.kt` (if exists) - invalidate `worldExists`, `worldById`
- `pipeline/project/CreateProjectPipeline.kt` - invalidate `projectExists`
- `pipeline/project/DeleteProjectPipeline.kt` - invalidate `projectExists`, `projectById`
- `pipeline/task/CreateActionTaskPipeline.kt` - invalidate `taskExists`
- `pipeline/task/DeleteActionTaskPipeline.kt` - invalidate `taskExists`
- `pipeline/resources/ResourceGatheringPlanSteps.kt` - invalidate `resourceGatheringExists`
- `pipeline/invitation/AcceptInvitationPipeline.kt` - invalidate `worldMemberRole`, `userPermittedWorlds`
- `pipeline/world/settings/members/UpdateMemberRolePipeline.kt` - invalidate `worldMemberRole`
- Admin ban/unban handler - invalidate `bannedUsers`

**Expected result**: 2-4 fewer DB round-trips per request. Most noticeable on deep routes.

### Phase 2: Reference data + notification count

**Files to modify:**
- Modify: `pipeline/minecraft/GetSupportedVersionsStep.kt` - cache the version list
- Modify: `pipeline/notification/GetUnreadNotificationCountStep.kt` - cache per-user count
- Modify: `pipeline/notification/CreateNotificationStep.kt` (if exists) - invalidate count cache
- Modify: `pipeline/notification/ReadNotificationPipeline.kt` (if exists) - invalidate count cache

**Expected result**: 1-2 fewer DB round-trips on nearly every page load.

### Phase 3: Entity by-ID caches

**Files to modify:**
- Wrap `GetWorldStep` / `GetWorldPipeline`
- Wrap `GetProjectByIdStep` / `GetProjectPipeline`
- Wrap `GetIdeaStep` / `GetIdeaPipeline`
- Wrap `GetPermittedWorldsStep`
- Add invalidation calls in all write pipelines that modify these entities

**Expected result**: Repeat page views served from cache. Especially impactful for the Ideas library which is read-heavy and write-rare.

### Phase 4 (optional): Search result caches

Only pursue if monitoring shows search queries are a bottleneck. The low cache hit rate makes this less impactful.

---

## Estimated impact

| Metric | Before | After Phase 1-2 | After Phase 1-3 |
|---|---|---|---|
| Queries per project page load | 6-8 | 2-4 | 1-2 (cache hit) |
| Queries per home page load | 4-5 | 2-3 | 1-2 (cache hit) |
| Plugin chain queries | 2-4 | 0 (cache hit) | 0 (cache hit) |
| Neon connection pressure | High | ~50% reduction | ~70% reduction |
| p50 latency improvement | - | ~30-50ms saved | ~50-80ms saved |

The latency savings come from eliminating network round-trips to Neon (typically 10-20ms each from Fly.io).

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Stale cache after write | Explicit invalidation on every write path + short TTLs as safety net |
| Memory growth | Bounded `maximumSize` on every cache; Caffeine evicts LRU |
| Cache poisoning (caching error states) | Only cache `Result.Success` values, never failures |
| Missed invalidation in new code | Document cache contract; add to development checklist |
| Multi-instance deployment (future) | Short TTLs (5 min) make eventual consistency acceptable; can add Redis later |

---

## Testing strategy

- Unit test `CachedStep`: verify cache hit skips delegate, cache miss calls delegate, failure not cached
- Unit test invalidation: verify write operations clear the right cache keys
- Integration test: verify full request flow uses cache on second call (mock DB, assert query count)
- Load test: compare query count per request before/after with a counter wrapper on `Database.getConnection()`
