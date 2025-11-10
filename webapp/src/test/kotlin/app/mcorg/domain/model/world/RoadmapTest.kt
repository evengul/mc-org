package app.mcorg.domain.model.world

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoadmapTest {

    @Test
    fun `roadmap with no projects should be empty`() {
        // Given
        val roadmap = Roadmap(
            worldId = 1,
            worldName = "Test World",
            nodes = emptyList(),
            edges = emptyList(),
            layers = emptyList()
        )

        // Then
        assertTrue(roadmap.isEmpty())
        assertEquals(0, roadmap.getMaxDepth())
    }

    @Test
    fun `should identify root nodes correctly`() {
        // Given - Project 1 has no dependencies (root), Project 2 depends on Project 1
        val node1 = createTestNode(1, "Foundation", layer = 0)
        val node2 = createTestNode(2, "Building", layer = 1)
        val edge = RoadmapEdge(
            fromNodeId = 2,
            fromNodeName = "Building",
            toNodeId = 1,
            toNodeName = "Foundation",
            isBlocking = false
        )

        val roadmap = Roadmap(
            worldId = 1,
            worldName = "Test World",
            nodes = listOf(node1, node2),
            edges = listOf(edge),
            layers = listOf(
                RoadmapLayer(0, listOf(1), 1),
                RoadmapLayer(1, listOf(2), 1)
            )
        )

        // When
        val rootNodes = roadmap.getRootNodes()

        // Then
        assertEquals(1, rootNodes.size)
        assertEquals(1, rootNodes[0].projectId)
    }

    @Test
    fun `should identify leaf nodes correctly`() {
        // Given - Project 1 has dependents, Project 2 has no dependents (leaf)
        val node1 = createTestNode(1, "Foundation", layer = 0)
        val node2 = createTestNode(2, "Building", layer = 1)
        val edge = RoadmapEdge(
            fromNodeId = 2,
            fromNodeName = "Building",
            toNodeId = 1,
            toNodeName = "Foundation",
            isBlocking = false
        )

        val roadmap = Roadmap(
            worldId = 1,
            worldName = "Test World",
            nodes = listOf(node1, node2),
            edges = listOf(edge),
            layers = listOf(
                RoadmapLayer(0, listOf(1), 1),
                RoadmapLayer(1, listOf(2), 1)
            )
        )

        // When
        val leafNodes = roadmap.getLeafNodes()

        // Then
        assertEquals(1, leafNodes.size)
        assertEquals(2, leafNodes[0].projectId)
    }

    @Test
    fun `should calculate roadmap statistics correctly`() {
        // Given
        val node1 = createTestNode(1, "Foundation", stage = ProjectStage.COMPLETED, layer = 0)
        val node2 = createTestNode(2, "Building", stage = ProjectStage.BUILDING, isBlocked = true, layer = 1)
        val node3 = createTestNode(3, "Decoration", stage = ProjectStage.IDEA, layer = 2)

        val roadmap = Roadmap(
            worldId = 1,
            worldName = "Test World",
            nodes = listOf(node1, node2, node3),
            edges = listOf(
                RoadmapEdge(2, "Building", 1, "Foundation", false),
                RoadmapEdge(3, "Decoration", 2, "Building", true)
            ),
            layers = listOf(
                RoadmapLayer(0, listOf(1), 1),
                RoadmapLayer(1, listOf(2), 1),
                RoadmapLayer(2, listOf(3), 1)
            )
        )

        // When
        val stats = roadmap.getStatistics()

        // Then
        assertEquals(3, stats.totalProjects)
        assertEquals(1, stats.completedProjects)
        assertEquals(1, stats.blockedProjects)
        assertEquals(1, stats.rootProjects)
        assertEquals(1, stats.leafProjects)
        assertEquals(3, stats.maxDepth)
        assertEquals(2, stats.totalDependencies)
        assertEquals(33, stats.getOverallCompletionPercentage()) // 1/3 = 33%
        assertEquals(1, stats.getInProgressCount()) // 3 - 1 completed - 1 blocked = 1
        assertTrue(stats.hasBlockedProjects())
    }

    @Test
    fun `node should calculate completion percentage correctly`() {
        // Given
        val node = createTestNode(
            id = 1,
            name = "Test Project",
            tasksTotal = 10,
            tasksCompleted = 3
        )

        // Then
        assertEquals(30, node.getCompletionPercentage())
    }

    @Test
    fun `node with no tasks should have 0 completion percentage`() {
        // Given
        val node = createTestNode(
            id = 1,
            name = "Test Project",
            tasksTotal = 0,
            tasksCompleted = 0
        )

        // Then
        assertEquals(0, node.getCompletionPercentage())
    }

    @Test
    fun `node should identify ready to start status correctly`() {
        // Given
        val readyNode = createTestNode(
            id = 1,
            name = "Ready Project",
            stage = ProjectStage.IDEA,
            isBlocked = false
        )
        val blockedNode = createTestNode(
            id = 2,
            name = "Blocked Project",
            stage = ProjectStage.IDEA,
            isBlocked = true
        )
        val inProgressNode = createTestNode(
            id = 3,
            name = "In Progress Project",
            stage = ProjectStage.BUILDING,
            isBlocked = false
        )

        // Then
        assertTrue(readyNode.isReadyToStart())
        assertFalse(blockedNode.isReadyToStart())
        assertFalse(inProgressNode.isReadyToStart())
    }

    @Test
    fun `node should identify in-progress status correctly`() {
        // Given
        val designNode = createTestNode(id = 1, name = "Design", stage = ProjectStage.DESIGN)
        val buildingNode = createTestNode(id = 2, name = "Building", stage = ProjectStage.BUILDING)
        val ideaNode = createTestNode(id = 3, name = "Idea", stage = ProjectStage.IDEA)
        val completedNode = createTestNode(id = 4, name = "Done", stage = ProjectStage.COMPLETED)

        // Then
        assertTrue(designNode.isInProgress())
        assertTrue(buildingNode.isInProgress())
        assertFalse(ideaNode.isInProgress())
        assertFalse(completedNode.isInProgress())
    }

    @Test
    fun `node should count blocking dependencies correctly`() {
        // Given
        val node = createTestNode(
            id = 1,
            name = "Test",
            blockingProjectIds = listOf(2, 3, 4)
        )

        // Then
        assertEquals(3, node.getBlockingCount())
    }

    @Test
    fun `layer should identify root layer correctly`() {
        // Given
        val rootLayer = RoadmapLayer(depth = 0, projectIds = listOf(1, 2), projectCount = 2)
        val nonRootLayer = RoadmapLayer(depth = 1, projectIds = listOf(3), projectCount = 1)

        // Then
        assertTrue(rootLayer.isRootLayer())
        assertFalse(nonRootLayer.isRootLayer())
    }

    @Test
    fun `edge should report blocking status correctly`() {
        // Given
        val blockingEdge = RoadmapEdge(
            fromNodeId = 1,
            fromNodeName = "Dependent",
            toNodeId = 2,
            toNodeName = "Dependency",
            isBlocking = true
        )
        val nonBlockingEdge = RoadmapEdge(
            fromNodeId = 3,
            fromNodeName = "Dependent",
            toNodeId = 4,
            toNodeName = "Dependency",
            isBlocking = false
        )

        // Then
        assertTrue(blockingEdge.isCurrentlyBlocking())
        assertFalse(nonBlockingEdge.isCurrentlyBlocking())
    }

    // Helper function to create test nodes
    private fun createTestNode(
        id: Int,
        name: String,
        type: ProjectType = ProjectType.BUILDING,
        stage: ProjectStage = ProjectStage.IDEA,
        tasksTotal: Int = 0,
        tasksCompleted: Int = 0,
        isBlocked: Boolean = false,
        blockingProjectIds: List<Int> = emptyList(),
        dependentProjectIds: List<Int> = emptyList(),
        layer: Int = 0
    ): RoadmapNode = RoadmapNode(
        projectId = id,
        projectName = name,
        projectType = type,
        stage = stage,
        tasksTotal = tasksTotal,
        tasksCompleted = tasksCompleted,
        isBlocked = isBlocked,
        blockingProjectIds = blockingProjectIds,
        dependentProjectIds = dependentProjectIds,
        layer = layer
    )
}

