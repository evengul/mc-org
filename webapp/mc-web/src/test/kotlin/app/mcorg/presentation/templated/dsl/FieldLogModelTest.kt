package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldLogModelTest {

    private fun project(
        id: Int,
        name: String,
        state: ProjectState = ProjectState.ACTIVE,
        gathered: Int = 0,
        required: Int = 100,
    ) = ProjectListItem(
        id = id,
        name = name,
        stage = ProjectStage.RESOURCE_GATHERING,
        state = state,
        tasksTotal = 0,
        tasksDone = 0,
        resourcesRequired = required,
        resourcesGathered = gathered,
        itemCount = 1,
        nextTaskName = null,
    )

    private fun edge(
        consumer: ProjectListItem,
        producer: ProjectListItem,
        item: String? = "hopper",
    ) = ProjectResourceEdge(
        consumerId = consumer.id,
        consumerName = consumer.name,
        producerId = producer.id,
        producerName = producer.name,
        itemName = item,
        producerState = producer.state,
    )

    @Test
    fun `projects without dependencies are never blocked`() {
        val a = project(1, "Iron Farm")
        val b = project(2, "Storage Hall")

        val model = FieldLogModel.of(listOf(a, b), emptyList())

        assertFalse(model.isBlocked(1))
        assertFalse(model.isBlocked(2))
        assertEquals(2, model.active.size)
    }

    @Test
    fun `blocked project sinks to the bottom of active`() {
        val producer = project(1, "Slime Farm", gathered = 90)
        val blocked = project(2, "Sorting System", gathered = 99)
        val free = project(3, "Storage Hall", gathered = 10)

        val model = FieldLogModel.of(
            listOf(producer, blocked, free),
            listOf(edge(blocked, producer, "sticky piston"))
        )

        assertTrue(model.isBlocked(2))
        assertEquals(2, model.active.last().id)
    }

    @Test
    fun `blocking is released when the producer is done`() {
        val producer = project(1, "Slime Farm", state = ProjectState.DONE)
        val consumer = project(2, "Sorting System")

        val model = FieldLogModel.of(
            listOf(producer, consumer),
            listOf(edge(consumer, producer))
        )

        assertFalse(model.isBlocked(2))
    }

    @Test
    fun `partially blocked project lists each upstream producer`() {
        val slime = project(1, "Slime Farm")
        val iron = project(2, "Iron Farm")
        val sorting = project(3, "Sorting System")

        val model = FieldLogModel.of(
            listOf(slime, iron, sorting),
            listOf(
                edge(sorting, slime, "sticky piston"),
                edge(sorting, iron, "hopper"),
            )
        )

        assertTrue(model.isBlocked(3))
        assertEquals(setOf(1, 2), model.blockedBy(3).map { it.producerId }.toSet())
    }

    @Test
    fun `producer shows feeds edges for live consumers only`() {
        val producer = project(1, "Iron Farm")
        val liveConsumer = project(2, "Sorting System")
        val doneConsumer = project(3, "Old Build", state = ProjectState.DONE)

        val model = FieldLogModel.of(
            listOf(producer, liveConsumer, doneConsumer),
            listOf(
                edge(liveConsumer, producer, "hopper"),
                edge(doneConsumer, producer, "iron block"),
            )
        )

        assertEquals(listOf(2), model.feeds(1).map { it.consumerId })
    }

    @Test
    fun `duplicate edges collapse to one per producer consumer item`() {
        val producer = project(1, "Slime Farm")
        val consumer = project(2, "Sorting System")

        val model = FieldLogModel.of(
            listOf(producer, consumer),
            listOf(
                edge(consumer, producer, "sticky piston"),
                edge(consumer, producer, "sticky piston"),
            )
        )

        assertEquals(1, model.blockedBy(2).size)
        assertEquals(1, model.feeds(1).size)
    }

    @Test
    fun `unblocked actives sort by progress descending`() {
        val far = project(1, "Far", gathered = 10)
        val close = project(2, "Close", gathered = 90)

        val model = FieldLogModel.of(listOf(far, close), emptyList())

        assertEquals(listOf(2, 1), model.active.map { it.id })
    }

    @Test
    fun `terminal states group into their own buckets`() {
        val model = FieldLogModel.of(
            listOf(
                project(1, "A", state = ProjectState.DONE),
                project(2, "B", state = ProjectState.CANCELLED),
                project(3, "C", state = ProjectState.ARCHIVED),
                project(4, "D", state = ProjectState.PAUSED),
                project(5, "E", state = ProjectState.PENDING),
            ),
            emptyList()
        )

        assertEquals(listOf(1), model.done.map { it.id })
        assertEquals(listOf(2), model.cancelled.map { it.id })
        assertEquals(listOf(3), model.archived.map { it.id })
        assertEquals(listOf(4), model.paused.map { it.id })
        assertEquals(listOf(5), model.pending.map { it.id })
        assertTrue(model.active.isEmpty())
    }
}
