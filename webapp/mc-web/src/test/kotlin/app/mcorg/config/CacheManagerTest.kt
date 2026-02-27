package app.mcorg.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CacheManagerTest {

    @BeforeEach
    fun setUp() {
        CacheManager.invalidateAll()
    }

    // ── Cache population and retrieval ──────────────────────────────

    @Test
    fun `bannedUsers cache stores and retrieves values`() {
        CacheManager.bannedUsers.put(1, true)
        CacheManager.bannedUsers.put(2, false)

        assertEquals(true, CacheManager.bannedUsers.getIfPresent(1))
        assertEquals(false, CacheManager.bannedUsers.getIfPresent(2))
        assertNull(CacheManager.bannedUsers.getIfPresent(3))
    }

    @Test
    fun `worldExists cache stores and retrieves values`() {
        CacheManager.worldExists.put(10, true)

        assertEquals(true, CacheManager.worldExists.getIfPresent(10))
        assertNull(CacheManager.worldExists.getIfPresent(20))
    }

    @Test
    fun `projectExists cache uses composite key`() {
        CacheManager.projectExists.put("1:100", true)

        assertEquals(true, CacheManager.projectExists.getIfPresent("1:100"))
        assertNull(CacheManager.projectExists.getIfPresent("1:200"))
        assertNull(CacheManager.projectExists.getIfPresent("2:100"))
    }

    @Test
    fun `taskExists cache uses composite key`() {
        CacheManager.taskExists.put("100:500", true)

        assertEquals(true, CacheManager.taskExists.getIfPresent("100:500"))
        assertNull(CacheManager.taskExists.getIfPresent("100:501"))
    }

    @Test
    fun `resourceGatheringExists cache uses composite key`() {
        CacheManager.resourceGatheringExists.put("100:200", true)

        assertEquals(true, CacheManager.resourceGatheringExists.getIfPresent("100:200"))
        assertNull(CacheManager.resourceGatheringExists.getIfPresent("100:201"))
    }

    @Test
    fun `worldMemberRole cache uses userId-worldId-roleLevel key`() {
        CacheManager.worldMemberRole.put("1:10:100", true)
        CacheManager.worldMemberRole.put("1:10:10", false)

        assertEquals(true, CacheManager.worldMemberRole.getIfPresent("1:10:100"))
        assertEquals(false, CacheManager.worldMemberRole.getIfPresent("1:10:10"))
        assertNull(CacheManager.worldMemberRole.getIfPresent("1:10:0"))
    }

    @Test
    fun `ideaExists cache stores and retrieves values`() {
        CacheManager.ideaExists.put(5, true)

        assertEquals(true, CacheManager.ideaExists.getIfPresent(5))
        assertNull(CacheManager.ideaExists.getIfPresent(6))
    }

    @Test
    fun `ideaCommentExists cache stores and retrieves values`() {
        CacheManager.ideaCommentExists.put(42, true)

        assertEquals(true, CacheManager.ideaCommentExists.getIfPresent(42))
        assertNull(CacheManager.ideaCommentExists.getIfPresent(43))
    }

    @Test
    fun `inviteExists cache stores and retrieves values`() {
        CacheManager.inviteExists.put(7, true)

        assertEquals(true, CacheManager.inviteExists.getIfPresent(7))
        assertNull(CacheManager.inviteExists.getIfPresent(8))
    }

    @Test
    fun `worldMemberExists cache uses composite key`() {
        CacheManager.worldMemberExists.put("5:10", true)

        assertEquals(true, CacheManager.worldMemberExists.getIfPresent("5:10"))
        assertNull(CacheManager.worldMemberExists.getIfPresent("5:11"))
    }

    @Test
    fun `notificationExists cache uses composite key`() {
        CacheManager.notificationExists.put("1:99", true)

        assertEquals(true, CacheManager.notificationExists.getIfPresent("1:99"))
        assertNull(CacheManager.notificationExists.getIfPresent("1:100"))
    }

    @Test
    fun `unreadNotificationCount cache stores integer counts`() {
        CacheManager.unreadNotificationCount.put(1, 5)
        CacheManager.unreadNotificationCount.put(2, 0)

        assertEquals(5, CacheManager.unreadNotificationCount.getIfPresent(1))
        assertEquals(0, CacheManager.unreadNotificationCount.getIfPresent(2))
        assertNull(CacheManager.unreadNotificationCount.getIfPresent(3))
    }

    @Test
    fun `supportedVersions cache uses singleton key`() {
        val versions = listOf("1.20.1", "1.21.0")
        CacheManager.supportedVersions.put("versions", versions)

        assertEquals(versions, CacheManager.supportedVersions.getIfPresent("versions"))
        assertNull(CacheManager.supportedVersions.getIfPresent("other"))
    }

    @Test
    fun `projectProductionItemExists cache uses composite key`() {
        CacheManager.projectProductionItemExists.put("50:100", true)

        assertEquals(true, CacheManager.projectProductionItemExists.getIfPresent("50:100"))
        assertNull(CacheManager.projectProductionItemExists.getIfPresent("50:101"))
    }

    @Test
    fun `projectDependencyExists cache uses composite key`() {
        CacheManager.projectDependencyExists.put("10:20", true)

        assertEquals(true, CacheManager.projectDependencyExists.getIfPresent("10:20"))
        assertNull(CacheManager.projectDependencyExists.getIfPresent("10:21"))
    }

    // ── Invalidation helpers ────────────────────────────────────────

    @Test
    fun `onWorldCreated populates worldExists cache`() {
        CacheManager.onWorldCreated(42)

        assertEquals(true, CacheManager.worldExists.getIfPresent(42))
    }

    @Test
    fun `onWorldDeleted removes from worldExists cache`() {
        CacheManager.worldExists.put(42, true)

        CacheManager.onWorldDeleted(42)

        assertNull(CacheManager.worldExists.getIfPresent(42))
    }

    @Test
    fun `onProjectCreated populates projectExists cache`() {
        CacheManager.onProjectCreated(1, 100)

        assertEquals(true, CacheManager.projectExists.getIfPresent("1:100"))
    }

    @Test
    fun `onProjectDeleted removes from projectExists cache`() {
        CacheManager.projectExists.put("1:100", true)

        CacheManager.onProjectDeleted(1, 100)

        assertNull(CacheManager.projectExists.getIfPresent("1:100"))
    }

    @Test
    fun `onTaskCreated populates taskExists cache`() {
        CacheManager.onTaskCreated(100, 500)

        assertEquals(true, CacheManager.taskExists.getIfPresent("100:500"))
    }

    @Test
    fun `onTaskDeleted removes from taskExists cache`() {
        CacheManager.taskExists.put("100:500", true)

        CacheManager.onTaskDeleted(100, 500)

        assertNull(CacheManager.taskExists.getIfPresent("100:500"))
    }

    @Test
    fun `onResourceGatheringCreated populates resourceGatheringExists cache`() {
        CacheManager.onResourceGatheringCreated(100, 200)

        assertEquals(true, CacheManager.resourceGatheringExists.getIfPresent("100:200"))
    }

    @Test
    fun `onResourceGatheringDeleted removes from resourceGatheringExists cache`() {
        CacheManager.resourceGatheringExists.put("100:200", true)

        CacheManager.onResourceGatheringDeleted(100, 200)

        assertNull(CacheManager.resourceGatheringExists.getIfPresent("100:200"))
    }

    @Test
    fun `onMemberRoleChanged invalidates all role checks for user and world`() {
        // Set up cached role checks at different role levels
        CacheManager.worldMemberRole.put("5:10:0", true)     // OWNER check
        CacheManager.worldMemberRole.put("5:10:10", true)    // ADMIN check
        CacheManager.worldMemberRole.put("5:10:100", true)   // MEMBER check
        // Different user+world combo should NOT be invalidated
        CacheManager.worldMemberRole.put("5:20:10", true)
        CacheManager.worldMemberRole.put("6:10:10", true)

        CacheManager.onMemberRoleChanged(5, 10)

        // All entries for user=5, world=10 should be gone
        assertNull(CacheManager.worldMemberRole.getIfPresent("5:10:0"))
        assertNull(CacheManager.worldMemberRole.getIfPresent("5:10:10"))
        assertNull(CacheManager.worldMemberRole.getIfPresent("5:10:100"))
        // Other entries should remain
        assertEquals(true, CacheManager.worldMemberRole.getIfPresent("5:20:10"))
        assertEquals(true, CacheManager.worldMemberRole.getIfPresent("6:10:10"))
    }

    @Test
    fun `onMemberRoleChanged also invalidates worldMemberExists`() {
        CacheManager.worldMemberExists.put("5:10", true)

        CacheManager.onMemberRoleChanged(5, 10)

        assertNull(CacheManager.worldMemberExists.getIfPresent("5:10"))
    }

    @Test
    fun `onMemberAdded populates worldMemberExists cache`() {
        CacheManager.onMemberAdded(5, 10)

        assertEquals(true, CacheManager.worldMemberExists.getIfPresent("5:10"))
    }

    @Test
    fun `onMemberRemoved invalidates role and member caches`() {
        CacheManager.worldMemberRole.put("5:10:10", true)
        CacheManager.worldMemberExists.put("5:10", true)

        CacheManager.onMemberRemoved(5, 10)

        assertNull(CacheManager.worldMemberRole.getIfPresent("5:10:10"))
        assertNull(CacheManager.worldMemberExists.getIfPresent("5:10"))
    }

    @Test
    fun `onIdeaCreated populates ideaExists cache`() {
        CacheManager.onIdeaCreated(99)

        assertEquals(true, CacheManager.ideaExists.getIfPresent(99))
    }

    @Test
    fun `onIdeaDeleted removes from ideaExists cache`() {
        CacheManager.ideaExists.put(99, true)

        CacheManager.onIdeaDeleted(99)

        assertNull(CacheManager.ideaExists.getIfPresent(99))
    }

    @Test
    fun `onIdeaCommentCreated populates ideaCommentExists cache`() {
        CacheManager.onIdeaCommentCreated(42)

        assertEquals(true, CacheManager.ideaCommentExists.getIfPresent(42))
    }

    @Test
    fun `onIdeaCommentDeleted removes from ideaCommentExists cache`() {
        CacheManager.ideaCommentExists.put(42, true)

        CacheManager.onIdeaCommentDeleted(42)

        assertNull(CacheManager.ideaCommentExists.getIfPresent(42))
    }

    @Test
    fun `onInviteChanged invalidates invite cache entry`() {
        CacheManager.inviteExists.put(7, true)

        CacheManager.onInviteChanged(7)

        assertNull(CacheManager.inviteExists.getIfPresent(7))
    }

    @Test
    fun `onNotificationCreated invalidates unread count for user`() {
        CacheManager.unreadNotificationCount.put(1, 5)

        CacheManager.onNotificationCreated(1)

        assertNull(CacheManager.unreadNotificationCount.getIfPresent(1))
    }

    @Test
    fun `onNotificationCreated does not affect other users`() {
        CacheManager.unreadNotificationCount.put(1, 5)
        CacheManager.unreadNotificationCount.put(2, 3)

        CacheManager.onNotificationCreated(1)

        assertNull(CacheManager.unreadNotificationCount.getIfPresent(1))
        assertEquals(3, CacheManager.unreadNotificationCount.getIfPresent(2))
    }

    @Test
    fun `onNotificationRead invalidates unread count for user`() {
        CacheManager.unreadNotificationCount.put(1, 5)

        CacheManager.onNotificationRead(1)

        assertNull(CacheManager.unreadNotificationCount.getIfPresent(1))
    }

    @Test
    fun `onSupportedVersionsChanged invalidates all versions`() {
        CacheManager.supportedVersions.put("versions", listOf("1.20.1"))

        CacheManager.onSupportedVersionsChanged()

        assertNull(CacheManager.supportedVersions.getIfPresent("versions"))
    }

    @Test
    fun `onBanStatusChanged invalidates ban cache for user`() {
        CacheManager.bannedUsers.put(1, false)

        CacheManager.onBanStatusChanged(1)

        assertNull(CacheManager.bannedUsers.getIfPresent(1))
    }

    @Test
    fun `onBanStatusChanged does not affect other users`() {
        CacheManager.bannedUsers.put(1, false)
        CacheManager.bannedUsers.put(2, true)

        CacheManager.onBanStatusChanged(1)

        assertNull(CacheManager.bannedUsers.getIfPresent(1))
        assertEquals(true, CacheManager.bannedUsers.getIfPresent(2))
    }

    @Test
    fun `onProjectProductionItemCreated populates cache`() {
        CacheManager.onProjectProductionItemCreated(50, 100)

        assertEquals(true, CacheManager.projectProductionItemExists.getIfPresent("50:100"))
    }

    @Test
    fun `onProjectProductionItemDeleted removes from cache`() {
        CacheManager.projectProductionItemExists.put("50:100", true)

        CacheManager.onProjectProductionItemDeleted(50, 100)

        assertNull(CacheManager.projectProductionItemExists.getIfPresent("50:100"))
    }

    @Test
    fun `onProjectDependencyCreated populates cache`() {
        CacheManager.onProjectDependencyCreated(10, 20)

        assertEquals(true, CacheManager.projectDependencyExists.getIfPresent("10:20"))
    }

    @Test
    fun `onProjectDependencyDeleted removes from cache`() {
        CacheManager.projectDependencyExists.put("10:20", true)

        CacheManager.onProjectDependencyDeleted(10, 20)

        assertNull(CacheManager.projectDependencyExists.getIfPresent("10:20"))
    }

    // ── invalidateAll ───────────────────────────────────────────────

    @Test
    fun `invalidateAll clears every cache`() {
        // Populate all caches
        CacheManager.bannedUsers.put(1, true)
        CacheManager.worldExists.put(1, true)
        CacheManager.projectExists.put("1:1", true)
        CacheManager.taskExists.put("1:1", true)
        CacheManager.resourceGatheringExists.put("1:1", true)
        CacheManager.worldMemberRole.put("1:1:10", true)
        CacheManager.ideaExists.put(1, true)
        CacheManager.ideaCommentExists.put(1, true)
        CacheManager.inviteExists.put(1, true)
        CacheManager.worldMemberExists.put("1:1", true)
        CacheManager.notificationExists.put("1:1", true)
        CacheManager.projectProductionItemExists.put("1:1", true)
        CacheManager.projectDependencyExists.put("1:1", true)
        CacheManager.unreadNotificationCount.put(1, 5)
        CacheManager.supportedVersions.put("versions", listOf("1.20"))

        CacheManager.invalidateAll()

        // All caches should be empty
        assertNull(CacheManager.bannedUsers.getIfPresent(1))
        assertNull(CacheManager.worldExists.getIfPresent(1))
        assertNull(CacheManager.projectExists.getIfPresent("1:1"))
        assertNull(CacheManager.taskExists.getIfPresent("1:1"))
        assertNull(CacheManager.resourceGatheringExists.getIfPresent("1:1"))
        assertNull(CacheManager.worldMemberRole.getIfPresent("1:1:10"))
        assertNull(CacheManager.ideaExists.getIfPresent(1))
        assertNull(CacheManager.ideaCommentExists.getIfPresent(1))
        assertNull(CacheManager.inviteExists.getIfPresent(1))
        assertNull(CacheManager.worldMemberExists.getIfPresent("1:1"))
        assertNull(CacheManager.notificationExists.getIfPresent("1:1"))
        assertNull(CacheManager.projectProductionItemExists.getIfPresent("1:1"))
        assertNull(CacheManager.projectDependencyExists.getIfPresent("1:1"))
        assertNull(CacheManager.unreadNotificationCount.getIfPresent(1))
        assertNull(CacheManager.supportedVersions.getIfPresent("versions"))
    }

    // ── Isolation: create/delete pairs are independent ──────────────

    @Test
    fun `create then delete then check returns null`() {
        CacheManager.onWorldCreated(42)
        assertEquals(true, CacheManager.worldExists.getIfPresent(42))

        CacheManager.onWorldDeleted(42)
        assertNull(CacheManager.worldExists.getIfPresent(42))
    }

    @Test
    fun `deleting non-existent entry is a no-op`() {
        // Should not throw
        CacheManager.onWorldDeleted(999)
        CacheManager.onProjectDeleted(1, 999)
        CacheManager.onTaskDeleted(1, 999)
        CacheManager.onMemberRoleChanged(999, 999)
        CacheManager.onBanStatusChanged(999)
        CacheManager.onNotificationRead(999)
    }

    @Test
    fun `invalidation of one entity does not affect another entity type`() {
        CacheManager.worldExists.put(1, true)
        CacheManager.projectExists.put("1:1", true)

        CacheManager.onWorldDeleted(1)

        assertNull(CacheManager.worldExists.getIfPresent(1))
        assertEquals(true, CacheManager.projectExists.getIfPresent("1:1"))
    }
}
