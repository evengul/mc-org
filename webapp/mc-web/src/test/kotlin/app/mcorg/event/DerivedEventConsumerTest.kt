package app.mcorg.event

import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for derived-event logic (MCO-228): the milestone-crossing rules and the
 * [DerivedEventConsumer]'s re-publishing, including the one-level-of-derivation guard.
 */
class DerivedEventConsumerTest {

    // ── milestoneReached ─────────────────────────────────────────────────────

    @Test
    fun `crossing a single milestone reports that milestone`() {
        assertEquals(25, milestoneReached(previousDone = 20, newDone = 30, requiredTotal = 100))
    }

    @Test
    fun `a jump across several milestones reports only the highest crossed`() {
        assertEquals(75, milestoneReached(previousDone = 0, newDone = 80, requiredTotal = 100))
    }

    @Test
    fun `a jump straight to 100 reports only 100`() {
        assertEquals(100, milestoneReached(previousDone = 10, newDone = 100, requiredTotal = 100))
    }

    @Test
    fun `exactly landing on a milestone counts as crossing it`() {
        assertEquals(50, milestoneReached(previousDone = 49, newDone = 50, requiredTotal = 100))
    }

    @Test
    fun `no progress across a boundary reports null`() {
        assertEquals(null, milestoneReached(previousDone = 26, newDone = 40, requiredTotal = 100))
    }

    @Test
    fun `already complete then further update reports null`() {
        assertEquals(null, milestoneReached(previousDone = 100, newDone = 120, requiredTotal = 100))
    }

    @Test
    fun `zero required total reports null`() {
        assertEquals(null, milestoneReached(previousDone = 0, newDone = 0, requiredTotal = 0))
    }

    // ── DerivedEventConsumer ─────────────────────────────────────────────────

    private class RecordingBus : EventBus {
        val published = mutableListOf<SeamEvent>()
        override fun publish(event: SeamEvent) { published.add(event) }
        override fun subscribe(handler: EventHandler) = Unit
    }

    private val ts = Instant.parse("2026-06-21T12:00:00Z")

    @Test
    fun `resource count update re-publishes milestone and completion`() = runBlocking {
        val bus = RecordingBus()
        DerivedEventConsumer(bus).handle(
            ResourceCountUpdated(
                worldId = 1, actorId = 7, timestamp = ts, projectId = 9, itemId = "minecraft:stone",
                previousDone = 5, newDone = 10,
                projectPreviousDone = 90, projectNewDone = 100, projectRequired = 100,
            )
        )
        assertEquals(
            listOf<SeamEvent>(ResourceMilestoneReached(1, 7, ts, 9, 100), ProjectResourcesComplete(1, 7, ts, 9)),
            bus.published,
        )
    }

    @Test
    fun `dependency removal fans out one ProjectUnblocked per dependent`() = runBlocking {
        val bus = RecordingBus()
        DerivedEventConsumer(bus).handle(
            DependencyEdgeRemoved(
                worldId = 1, actorId = null, timestamp = ts, projectId = 4, dependsOnProjectId = 2,
                unblockedDependentProjectIds = listOf(11, 12),
            )
        )
        assertEquals(
            listOf<SeamEvent>(ProjectUnblocked(1, null, ts, 11, 4), ProjectUnblocked(1, null, ts, 12, 4)),
            bus.published,
        )
    }

    @Test
    fun `derived events are not re-derived`() = runBlocking {
        val bus = RecordingBus()
        DerivedEventConsumer(bus).handle(ProjectResourcesComplete(1, 7, ts, 9))
        assertTrue(bus.published.isEmpty())
    }
}
