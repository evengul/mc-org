package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.CreateProjectInput
import app.mcorg.pipeline.project.CreateProjectStep
import app.mcorg.pipeline.project.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetWorldRoadMapStepTest : WithUser() {

    @Test
    fun `should return empty roadmap for world with no projects`(): Unit = runBlocking {
        // Given
        val emptyWorldId = createTestWorld("Empty World")

        // When
        val result = GetWorldRoadMapStep(emptyWorldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value
        assertTrue(roadmap.isEmpty())
        assertEquals(0, roadmap.nodes.size)
        assertEquals(0, roadmap.edges.size)
        assertEquals(0, roadmap.layers.size)

        // Cleanup
        deleteTestWorld(emptyWorldId)
    }

    @Test
    fun `should build roadmap with single project`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Single Project World")
        val projectId = createTestProject(worldId, "Foundation", ProjectType.BUILDING, ProjectStage.BUILDING)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value
        assertEquals(1, roadmap.nodes.size)
        assertEquals(0, roadmap.edges.size)
        assertEquals(1, roadmap.layers.size)

        val node = roadmap.nodes.first()
        assertEquals(projectId, node.projectId)
        assertEquals("Foundation", node.projectName)
        assertEquals(ProjectType.BUILDING, node.projectType)
        assertEquals(ProjectStage.BUILDING, node.stage)
        assertEquals(0, node.layer)
        assertFalse(node.isBlocked)

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should identify root nodes correctly`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Root Nodes Test")
        val foundation = createTestProject(worldId, "Foundation", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val building = createTestProject(worldId, "Building", ProjectType.BUILDING, ProjectStage.BUILDING)
        createDependency(building, foundation)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value
        val rootNodes = roadmap.getRootNodes()

        assertEquals(1, rootNodes.size)
        assertEquals(foundation, rootNodes.first().projectId)
        assertEquals(0, rootNodes.first().layer)

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should identify leaf nodes correctly`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Leaf Nodes Test")
        val foundation = createTestProject(worldId, "Foundation", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val building = createTestProject(worldId, "Building", ProjectType.BUILDING, ProjectStage.BUILDING)
        createDependency(building, foundation)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value
        val leafNodes = roadmap.getLeafNodes()

        assertEquals(1, leafNodes.size)
        assertEquals(building, leafNodes.first().projectId)
        assertEquals(1, leafNodes.first().layer)

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should calculate layers correctly for linear dependency chain`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Linear Chain Test")
        val p1 = createTestProject(worldId, "Layer 0", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val p2 = createTestProject(worldId, "Layer 1", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val p3 = createTestProject(worldId, "Layer 2", ProjectType.BUILDING, ProjectStage.BUILDING)

        createDependency(p2, p1)
        createDependency(p3, p2)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value

        assertEquals(3, roadmap.layers.size)
        assertEquals(3, roadmap.getMaxDepth())

        val node1 = roadmap.nodes.find { it.projectId == p1 }!!
        val node2 = roadmap.nodes.find { it.projectId == p2 }!!
        val node3 = roadmap.nodes.find { it.projectId == p3 }!!

        assertEquals(0, node1.layer)
        assertEquals(1, node2.layer)
        assertEquals(2, node3.layer)

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should detect blocked projects correctly`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Blocked Projects Test")
        val foundation = createTestProject(worldId, "Foundation", ProjectType.BUILDING, ProjectStage.BUILDING)
        val walls = createTestProject(worldId, "Walls", ProjectType.BUILDING, ProjectStage.IDEA)
        val roof = createTestProject(worldId, "Roof", ProjectType.BUILDING, ProjectStage.IDEA)

        createDependency(walls, foundation)
        createDependency(roof, walls)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value

        val foundationNode = roadmap.nodes.find { it.projectId == foundation }!!
        val wallsNode = roadmap.nodes.find { it.projectId == walls }!!
        val roofNode = roadmap.nodes.find { it.projectId == roof }!!

        assertFalse(foundationNode.isBlocked)
        assertTrue(wallsNode.isBlocked)
        assertTrue(roofNode.isBlocked)

        assertEquals(1, wallsNode.blockingProjectIds.size)
        assertEquals(foundation, wallsNode.blockingProjectIds.first())

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should handle multiple dependencies correctly`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Multiple Dependencies Test")
        val resource1 = createTestProject(worldId, "Resource 1", ProjectType.MINING, ProjectStage.COMPLETED)
        val resource2 = createTestProject(worldId, "Resource 2", ProjectType.FARMING, ProjectStage.COMPLETED)
        val building = createTestProject(worldId, "Building", ProjectType.BUILDING, ProjectStage.BUILDING)

        createDependency(building, resource1)
        createDependency(building, resource2)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value

        val buildingNode = roadmap.nodes.find { it.projectId == building }!!

        assertEquals(1, buildingNode.layer) // Max(0, 0) + 1 = 1
        assertFalse(buildingNode.isBlocked) // Both dependencies completed
        assertEquals(0, buildingNode.blockingProjectIds.size)

        // Should have 2 edges
        val buildingEdges = roadmap.edges.filter { it.fromNodeId == building }
        assertEquals(2, buildingEdges.size)

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should calculate correct layer for project with multiple dependencies at different levels`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Multi-Level Dependencies Test")
        val p1 = createTestProject(worldId, "P1", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val p2 = createTestProject(worldId, "P2", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val p3 = createTestProject(worldId, "P3", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val p4 = createTestProject(worldId, "P4", ProjectType.BUILDING, ProjectStage.BUILDING)

        // P2 depends on P1
        createDependency(p2, p1)
        // P3 depends on P2
        createDependency(p3, p2)
        // P4 depends on both P1 (layer 0) and P3 (layer 2)
        createDependency(p4, p1)
        createDependency(p4, p3)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value

        val node4 = roadmap.nodes.find { it.projectId == p4 }!!
        assertEquals(3, node4.layer) // Max(0, 2) + 1 = 3

        // Cleanup
        deleteTestWorld(worldId)
    }

    @Test
    fun `should calculate statistics correctly`(): Unit = runBlocking {
        // Given
        val worldId = createTestWorld("Statistics Test")
        createTestProject(worldId, "Completed", ProjectType.BUILDING, ProjectStage.COMPLETED)
        val inProgress = createTestProject(worldId, "In Progress", ProjectType.BUILDING, ProjectStage.BUILDING)
        val blocked = createTestProject(worldId, "Blocked", ProjectType.BUILDING, ProjectStage.IDEA)

        createDependency(blocked, inProgress)

        // When
        val result = GetWorldRoadMapStep(worldId).process(Unit)

        // Then
        assertTrue(result is Result.Success)
        val roadmap = (result as Result.Success).value
        val stats = roadmap.getStatistics()

        assertEquals(3, stats.totalProjects)
        assertEquals(1, stats.completedProjects)
        assertEquals(1, stats.blockedProjects)
        assertEquals(2, stats.rootProjects) // completed and inProgress
        assertEquals(2, stats.leafProjects) // completed and blocked (both have no dependents)
        assertEquals(1, stats.totalDependencies)

        // Cleanup
        deleteTestWorld(worldId)
    }

    // Helper functions
    private fun createTestWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "Test world for roadmap",
                version = MinecraftVersion.fromString("1.20.1")
            )
        )
        when (result) {
            is Result.Success -> result.value
            is Result.Failure -> throw IllegalStateException("Failed to create test world: $result")
        }
    }

    private fun deleteTestWorld(worldId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
            }
        ).process(Unit)
    }

    private fun createTestProject(
        worldId: Int,
        name: String,
        type: ProjectType,
        stage: ProjectStage
    ): Int = runBlocking {
        // Create project with default IDEA stage
        val result = CreateProjectStep(worldId).process(
            CreateProjectInput(
                name = name,
                description = "Test project",
                type = type
            )
        )

        val projectId = when (result) {
            is Result.Success -> result.value
            is Result.Failure -> throw IllegalStateException("Failed to create test project: $result")
        }

        // Update to desired stage if not IDEA
        if (stage != ProjectStage.IDEA) {
            updateProjectStage(projectId, stage)
        }

        projectId
    }

    private fun updateProjectStage(projectId: Int, stage: ProjectStage) = runBlocking {
        val result = UpdateProjectStageStep(projectId).process(stage)
        when (result) {
            is Result.Success -> Unit
            is Result.Failure -> throw IllegalStateException("Failed to update project stage: $result")
        }
    }

    private fun createDependency(dependentId: Int, dependencyId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_dependencies (project_id, depends_on_project_id)
                VALUES (?, ?)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, dependentId)
                statement.setInt(2, dependencyId)
            }
        ).process(Unit)
    }
}

