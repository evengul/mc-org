package app.mcorg.pipeline.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-459 — the batch import wizard's whole state, which is two query parameters.
 *
 * The cases that matter here are the ones where the queue is *absent or wrong*: every existing
 * door into the review screen sends neither parameter, and a single import must keep behaving
 * exactly as it did before the wizard existed.
 */
class ImportQueueTest {

    private val queue = ImportQueue(ideaIds = listOf(3, 7, 12), returnToProjectId = 42)

    // ---- position and traversal ------------------------------------------------------

    @Test
    fun `position is 1-based so it can be read aloud`() {
        assertEquals(1, queue.positionOf(3))
        assertEquals(2, queue.positionOf(7))
        assertEquals(3, queue.positionOf(12))
    }

    @Test
    fun `a design outside the queue has no position`() {
        // A hand-edited URL naming a design the batch never selected. The handler turns this
        // into a plain single import rather than rendering "Review 0 of 3".
        assertNull(queue.positionOf(99))
    }

    @Test
    fun `next walks the queue and stops at the end`() {
        assertEquals(7, queue.nextAfter(3))
        assertEquals(12, queue.nextAfter(7))
        assertNull(queue.nextAfter(12), "the last step has nowhere to go but the plan")
    }

    @Test
    fun `next from outside the queue is null`() {
        assertNull(queue.nextAfter(99))
    }

    // ---- parsing ---------------------------------------------------------------------

    @Test
    fun `a queue round-trips through its own query string`() {
        val parsed = ImportQueue.from("3,7,12", "42")

        assertEquals(queue, parsed)
    }

    @Test
    fun `no parameters means no batch`() {
        // The important case, not an error case: the idea page, the world picker and any
        // bookmark all arrive this way and must stay single imports.
        assertNull(ImportQueue.from(null, null))
    }

    @Test
    fun `a queue without a return target is not a batch`() {
        // Without it there is nowhere to land at the end, which is the whole feature.
        assertNull(ImportQueue.from("3,7,12", null))
    }

    @Test
    fun `a return target without a queue is not a batch`() {
        assertNull(ImportQueue.from(null, "42"))
    }

    @Test
    fun `garbage degrades to no batch rather than a partial one`() {
        assertNull(ImportQueue.from("nonsense", "42"))
        assertNull(ImportQueue.from("3,7,12", "nonsense"))
        assertNull(ImportQueue.from("", "42"))
    }

    @Test
    fun `unparseable ids are dropped, not fatal`() {
        val parsed = ImportQueue.from("3,oops,12", "42")

        assertEquals(listOf(3, 12), parsed?.ideaIds)
    }

    @Test
    fun `duplicates are dropped so the wizard cannot loop`() {
        // The plan cannot render one design twice, so this is a hand-edited URL. Keeping the
        // repeat would make nextAfter(3) return 3 forever, since both key off the first index.
        val parsed = ImportQueue.from("3,7,3,12", "42")

        assertEquals(listOf(3, 7, 12), parsed?.ideaIds)
        assertEquals(7, parsed?.nextAfter(3))
    }

    @Test
    fun `whitespace around ids is tolerated`() {
        assertEquals(listOf(3, 7), ImportQueue.from(" 3 , 7 ", "42")?.ideaIds)
    }

    // ---- links -----------------------------------------------------------------------

    @Test
    fun `the review link carries the world and the whole queue`() {
        val href = queue.reviewHref(ideaId = 7, worldId = 5)

        assertTrue(href.contains("/import/review?worldId=5"), href)
        assertTrue(href.contains("queue=3,7,12"), href)
        assertTrue(href.contains("returnTo=42"), href)
    }

    @Test
    fun `done lands on the plan the batch started from`() {
        assertEquals("/worlds/5/projects/42", queue.returnHref(worldId = 5))
    }

    @Test
    fun `hidden fields carry the same two values the URL does`() {
        // The POST reads the queue back off the form, so the two encodings must agree.
        val fields = queue.hiddenFields()

        assertEquals("3,7,12", fields[ImportQueue.QUEUE_PARAM])
        assertEquals("42", fields[ImportQueue.RETURN_PARAM])
        assertEquals(queue, ImportQueue.from(fields[ImportQueue.QUEUE_PARAM], fields[ImportQueue.RETURN_PARAM]))
    }
}
