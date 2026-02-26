package app.mcorg.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Centralized cache manager for reducing database round-trips.
 *
 * Every cache is bounded by size and TTL. Only successful results are cached.
 * Write operations must call the appropriate invalidation method after committing.
 */
object CacheManager {
    private val logger = LoggerFactory.getLogger(CacheManager::class.java)

    // ── Tier 1: Plugin existence checks ──────────────────────────────────

    /** User ban status. Key: userId */
    val bannedUsers: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** World existence. Key: worldId */
    val worldExists: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    /** Project existence. Key: "worldId:projectId" */
    val projectExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    /** Action task existence. Key: "projectId:taskId" */
    val taskExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** Resource gathering existence. Key: "projectId:rgId" */
    val resourceGatheringExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** World member role check. Key: "userId:worldId:maxRoleLevel" */
    val worldMemberRole: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** Idea existence. Key: ideaId */
    val ideaExists: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    /** Idea comment existence. Key: commentId */
    val ideaCommentExists: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    /** Invite existence. Key: inviteId */
    val inviteExists: Cache<Int, Boolean> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** World member existence. Key: "memberId:worldId" */
    val worldMemberExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** Notification existence. Key: "userId:notificationId" */
    val notificationExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** Project production item existence. Key: "itemId:projectId" */
    val projectProductionItemExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /** Project dependency existence. Key: "projectId:dependencyId" */
    val projectDependencyExists: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    // ── Tier 2: Reference data ───────────────────────────────────────────

    /** Unread notification count. Key: userId */
    val unreadNotificationCount: Cache<Int, Int> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build()

    /** Supported Minecraft versions. Key: "versions" (singleton) */
    val supportedVersions: Cache<String, Any> = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build()

    // ── Invalidation helpers ─────────────────────────────────────────────

    fun onWorldCreated(worldId: Int) {
        worldExists.put(worldId, true)
        logger.debug("Cache: world {} marked as existing", worldId)
    }

    fun onWorldDeleted(worldId: Int) {
        worldExists.invalidate(worldId)
        logger.debug("Cache: world {} invalidated", worldId)
    }

    fun onProjectCreated(worldId: Int, projectId: Int) {
        projectExists.put("$worldId:$projectId", true)
        logger.debug("Cache: project {}:{} marked as existing", worldId, projectId)
    }

    fun onProjectDeleted(worldId: Int, projectId: Int) {
        projectExists.invalidate("$worldId:$projectId")
        logger.debug("Cache: project {}:{} invalidated", worldId, projectId)
    }

    fun onTaskCreated(projectId: Int, taskId: Int) {
        taskExists.put("$projectId:$taskId", true)
        logger.debug("Cache: task {}:{} marked as existing", projectId, taskId)
    }

    fun onTaskDeleted(projectId: Int, taskId: Int) {
        taskExists.invalidate("$projectId:$taskId")
        logger.debug("Cache: task {}:{} invalidated", projectId, taskId)
    }

    fun onResourceGatheringCreated(projectId: Int, rgId: Int) {
        resourceGatheringExists.put("$projectId:$rgId", true)
        logger.debug("Cache: resource gathering {}:{} marked as existing", projectId, rgId)
    }

    fun onResourceGatheringDeleted(projectId: Int, rgId: Int) {
        resourceGatheringExists.invalidate("$projectId:$rgId")
        logger.debug("Cache: resource gathering {}:{} invalidated", projectId, rgId)
    }

    fun onMemberRoleChanged(userId: Int, worldId: Int) {
        // Invalidate all role checks for this user+world (any role level)
        worldMemberRole.asMap().keys
            .filter { it.startsWith("$userId:$worldId:") }
            .forEach { worldMemberRole.invalidate(it) }
        worldMemberExists.invalidate("$userId:$worldId")
        logger.debug("Cache: member role for user {} in world {} invalidated", userId, worldId)
    }

    fun onMemberAdded(userId: Int, worldId: Int) {
        worldMemberExists.put("$userId:$worldId", true)
        logger.debug("Cache: member {}:{} marked as existing", userId, worldId)
    }

    fun onMemberRemoved(userId: Int, worldId: Int) {
        onMemberRoleChanged(userId, worldId)
        logger.debug("Cache: member {}:{} removed", userId, worldId)
    }

    fun onIdeaCreated(ideaId: Int) {
        ideaExists.put(ideaId, true)
        logger.debug("Cache: idea {} marked as existing", ideaId)
    }

    fun onIdeaDeleted(ideaId: Int) {
        ideaExists.invalidate(ideaId)
        logger.debug("Cache: idea {} invalidated", ideaId)
    }

    fun onIdeaCommentCreated(commentId: Int) {
        ideaCommentExists.put(commentId, true)
    }

    fun onIdeaCommentDeleted(commentId: Int) {
        ideaCommentExists.invalidate(commentId)
    }

    fun onInviteChanged(inviteId: Int) {
        inviteExists.invalidate(inviteId)
    }

    fun onNotificationCreated(userId: Int) {
        unreadNotificationCount.invalidate(userId)
        logger.debug("Cache: unread notification count invalidated for user {}", userId)
    }

    fun onNotificationRead(userId: Int) {
        unreadNotificationCount.invalidate(userId)
        logger.debug("Cache: unread notification count invalidated for user {}", userId)
    }

    fun onSupportedVersionsChanged() {
        supportedVersions.invalidateAll()
        logger.debug("Cache: supported versions invalidated")
    }

    fun onBanStatusChanged(userId: Int) {
        bannedUsers.invalidate(userId)
        logger.debug("Cache: ban status invalidated for user {}", userId)
    }

    fun onProjectProductionItemCreated(itemId: Int, projectId: Int) {
        projectProductionItemExists.put("$itemId:$projectId", true)
    }

    fun onProjectProductionItemDeleted(itemId: Int, projectId: Int) {
        projectProductionItemExists.invalidate("$itemId:$projectId")
    }

    fun onProjectDependencyCreated(projectId: Int, dependencyId: Int) {
        projectDependencyExists.put("$projectId:$dependencyId", true)
    }

    fun onProjectDependencyDeleted(projectId: Int, dependencyId: Int) {
        projectDependencyExists.invalidate("$projectId:$dependencyId")
    }

    /** Invalidate all caches. Useful for testing. */
    fun invalidateAll() {
        bannedUsers.invalidateAll()
        worldExists.invalidateAll()
        projectExists.invalidateAll()
        taskExists.invalidateAll()
        resourceGatheringExists.invalidateAll()
        worldMemberRole.invalidateAll()
        ideaExists.invalidateAll()
        ideaCommentExists.invalidateAll()
        inviteExists.invalidateAll()
        worldMemberExists.invalidateAll()
        notificationExists.invalidateAll()
        projectProductionItemExists.invalidateAll()
        projectDependencyExists.invalidateAll()
        unreadNotificationCount.invalidateAll()
        supportedVersions.invalidateAll()
        logger.info("All caches invalidated")
    }
}
