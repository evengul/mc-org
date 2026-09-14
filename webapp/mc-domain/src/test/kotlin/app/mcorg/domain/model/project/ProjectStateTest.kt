package app.mcorg.domain.model.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectStateTest {

    @Test
    fun `fromStage maps planning stages to pending`() {
        assertEquals(ProjectState.PENDING, ProjectState.fromStage(ProjectStage.IDEA))
        assertEquals(ProjectState.PENDING, ProjectState.fromStage(ProjectStage.DESIGN))
        assertEquals(ProjectState.PENDING, ProjectState.fromStage(ProjectStage.PLANNING))
    }

    @Test
    fun `fromStage maps work stages to active`() {
        assertEquals(ProjectState.ACTIVE, ProjectState.fromStage(ProjectStage.RESOURCE_GATHERING))
        assertEquals(ProjectState.ACTIVE, ProjectState.fromStage(ProjectStage.BUILDING))
        assertEquals(ProjectState.ACTIVE, ProjectState.fromStage(ProjectStage.TESTING))
    }

    @Test
    fun `fromStage maps completed to done`() {
        assertEquals(ProjectState.DONE, ProjectState.fromStage(ProjectStage.COMPLETED))
    }

    @Test
    fun `pending can activate cancel or archive`() {
        assertTrue(ProjectState.PENDING.canTransitionTo(ProjectState.ACTIVE))
        assertTrue(ProjectState.PENDING.canTransitionTo(ProjectState.CANCELLED))
        assertTrue(ProjectState.PENDING.canTransitionTo(ProjectState.ARCHIVED))
        assertFalse(ProjectState.PENDING.canTransitionTo(ProjectState.PAUSED))
        assertFalse(ProjectState.PENDING.canTransitionTo(ProjectState.DONE))
    }

    @Test
    fun `active can pause complete or cancel`() {
        assertTrue(ProjectState.ACTIVE.canTransitionTo(ProjectState.PAUSED))
        assertTrue(ProjectState.ACTIVE.canTransitionTo(ProjectState.DONE))
        assertTrue(ProjectState.ACTIVE.canTransitionTo(ProjectState.CANCELLED))
        assertFalse(ProjectState.ACTIVE.canTransitionTo(ProjectState.PENDING))
        assertFalse(ProjectState.ACTIVE.canTransitionTo(ProjectState.ARCHIVED))
    }

    @Test
    fun `paused can resume cancel or archive`() {
        assertTrue(ProjectState.PAUSED.canTransitionTo(ProjectState.ACTIVE))
        assertTrue(ProjectState.PAUSED.canTransitionTo(ProjectState.CANCELLED))
        assertTrue(ProjectState.PAUSED.canTransitionTo(ProjectState.ARCHIVED))
        assertFalse(ProjectState.PAUSED.canTransitionTo(ProjectState.DONE))
    }

    @Test
    fun `done can reopen, archive or decommission`() {
        assertTrue(ProjectState.DONE.canTransitionTo(ProjectState.ACTIVE))
        assertTrue(ProjectState.DONE.canTransitionTo(ProjectState.ARCHIVED))
        assertTrue(ProjectState.DONE.canTransitionTo(ProjectState.DECOMMISSIONED))
        assertFalse(ProjectState.DONE.canTransitionTo(ProjectState.PAUSED))
        assertFalse(ProjectState.DONE.canTransitionTo(ProjectState.CANCELLED))
    }

    @Test
    fun `decommissioned can come back online or be archived`() {
        assertTrue(ProjectState.DECOMMISSIONED.canTransitionTo(ProjectState.DONE))
        assertTrue(ProjectState.DECOMMISSIONED.canTransitionTo(ProjectState.ARCHIVED))
        assertFalse(ProjectState.DECOMMISSIONED.canTransitionTo(ProjectState.ACTIVE))
        assertFalse(ProjectState.DECOMMISSIONED.canTransitionTo(ProjectState.PENDING))
        assertFalse(ProjectState.DECOMMISSIONED.canTransitionTo(ProjectState.CANCELLED))
    }

    @Test
    fun `only a done project can be decommissioned, because it means this was built`() {
        assertEquals(
            listOf(ProjectState.DONE),
            ProjectState.entries.filter { it.canTransitionTo(ProjectState.DECOMMISSIONED) },
        )
    }

    @Test
    fun `no state can transition to itself`() {
        ProjectState.entries.forEach { state ->
            assertFalse(state.canTransitionTo(state), "$state should not transition to itself")
        }
    }

    @Test
    fun `terminal states are done cancelled archived and decommissioned`() {
        assertTrue(ProjectState.DONE.isTerminal)
        assertTrue(ProjectState.CANCELLED.isTerminal)
        assertTrue(ProjectState.ARCHIVED.isTerminal)
        assertTrue(ProjectState.DECOMMISSIONED.isTerminal)
        assertFalse(ProjectState.PENDING.isTerminal)
        assertFalse(ProjectState.ACTIVE.isTerminal)
        assertFalse(ProjectState.PAUSED.isTerminal)
    }
}
