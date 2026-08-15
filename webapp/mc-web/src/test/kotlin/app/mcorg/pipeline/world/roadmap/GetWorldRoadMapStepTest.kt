package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.CreateProjectInput
import app.mcorg.pipeline.project.CreateProjectStep
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@Tag("database")
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

    // -------------------------------------------------------------------------
    // Derived edges (MCO-288): the roadmap reads resource relationships, not just
    // the manual project_dependencies table.
    // -------------------------------------------------------------------------

    @Test
    fun `an item-level solved-by link is a roadmap edge naming the resource`(): Unit = runBlocking {
        val worldId = createTestWorld("Solved-by World")
        val consumer = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.PLANNING)
        val producer = createTestProject(worldId, "Iron Farm", ProjectType.FARMING, ProjectStage.BUILDING)
        val requirementId = createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        solveRequirementBy(requirementId, producer)

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        val edge = roadmap.edges.single()
        assertEquals(consumer, edge.fromNodeId)
        assertEquals(producer, edge.toNodeId)
        assertEquals("Iron Ingot", edge.itemName, "the blocked-by cell needs the resource, not just the project")
        assertTrue(edge.isBlocking, "the producer is not DONE")
        assertEquals(1, roadmap.nodes.single { it.projectId == consumer }.layer)
        assertTrue(roadmap.nodes.single { it.projectId == consumer }.isBlocked)

        deleteTestWorld(worldId)
    }

    @Test
    fun `a project that produces what another needs is an edge even with nothing declared`(): Unit = runBlocking {
        // Nobody set solved_by_project_id and there is no project_dependencies row: the farm
        // simply produces an item the other project requires (MCO-287).
        val worldId = createTestWorld("Farm Supply World")
        val consumer = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.PLANNING)
        val farm = createTestProject(worldId, "Iron Farm", ProjectType.FARMING, ProjectStage.BUILDING)
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        val edge = roadmap.edges.single()
        assertEquals(farm, edge.toNodeId)
        assertEquals("Iron Ingot", edge.itemName)
        assertEquals(32L, edge.quantity, "the edge carries the derived demand (MCO-316)")
        assertTrue(edge.isBlocking, "a farm that is not running yet still blocks")

        deleteTestWorld(worldId)
    }

    @Test
    fun `an operational farm supplies without blocking`(): Unit = runBlocking {
        val worldId = createTestWorld("Operational Farm World")
        val consumer = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.PLANNING)
        val farm = createTestProject(worldId, "Iron Farm", ProjectType.FARMING, ProjectStage.COMPLETED)
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        assertFalse(roadmap.edges.single().isBlocking, "DONE means producing (MCO-287)")
        assertFalse(roadmap.nodes.single { it.projectId == consumer }.isBlocked)

        deleteTestWorld(worldId)
    }

    @Test
    fun `a requirement solved by the same farm produces one edge, not two`(): Unit = runBlocking {
        val worldId = createTestWorld("Dedupe World")
        val consumer = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.PLANNING)
        val farm = createTestProject(worldId, "Iron Farm", ProjectType.FARMING, ProjectStage.BUILDING)
        val requirementId = createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        solveRequirementBy(requirementId, farm)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        assertEquals(1, roadmap.edges.size, "declared and derived describe the same relationship")

        deleteTestWorld(worldId)
    }

    @Test
    fun `an ignored requirement is not demand`(): Unit = runBlocking {
        val worldId = createTestWorld("Ignored Requirement World")
        val consumer = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.PLANNING)
        val farm = createTestProject(worldId, "Iron Farm", ProjectType.FARMING, ProjectStage.BUILDING)
        val requirementId = createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        ignoreRequirement(requirementId)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        assertTrue(roadmap.edges.isEmpty(), "an ignored row (MCO-247) asks for nothing")

        deleteTestWorld(worldId)
    }

    @Test
    fun `the roadmap carries the world's real name`(): Unit = runBlocking {
        val worldId = createTestWorld("Nether Hub Server")

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        assertEquals("Nether Hub Server", roadmap.worldName)

        deleteTestWorld(worldId)
    }

    @Test
    fun `task counts distinguish completed from total`(): Unit = runBlocking {
        val worldId = createTestWorld("Task Count World")
        val projectId = createTestProject(worldId, "Beacon", ProjectType.BUILDING, ProjectStage.BUILDING)
        createTask(projectId, "Dig out area", completed = true)
        createTask(projectId, "Place hoppers", completed = false)
        createTask(projectId, "Wire redstone", completed = false)

        val roadmap = (GetWorldRoadMapStep(worldId).process(Unit) as Result.Success).value

        val node = roadmap.nodes.single()
        assertEquals(3, node.tasksTotal)
        assertEquals(1, node.tasksCompleted)

        deleteTestWorld(worldId)
    }

    private fun createRequirement(projectId: Int, itemId: String, name: String, required: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setString(2, itemId)
                statement.setString(3, name)
                statement.setInt(4, required)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun solveRequirementBy(requirementId: Int, producerId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE resource_gathering SET solved_by_project_id = ? WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, producerId)
                statement.setInt(2, requirementId)
            }
        ).process(Unit)
    }

    private fun ignoreRequirement(requirementId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE resource_gathering SET ignored = TRUE WHERE id = ?"),
            parameterSetter = { statement, _ -> statement.setInt(1, requirementId) }
        ).process(Unit)
    }

    /**
     * Seeds derived plan demand (MCO-316) — what farm edges now match, in place of the declared
     * `resource_gathering` rows they used to. Deriving it for real needs an ingested
     * item-source graph, which these tests do not have; the derivation is covered where it lives.
     */
    private fun createDemand(projectId: Int, itemId: String, name: String, quantity: Long) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand
                    (project_id, item_id, item_name, quantity, activity_group, node_status)
                VALUES (?, ?, ?, ?, 'GATHER', 'RESOLVED')
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setString(2, itemId)
                statement.setString(3, name)
                statement.setLong(4, quantity)
            }
        ).process(Unit)
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand_state (project_id, fingerprint)
                VALUES (?, 'seeded') ON CONFLICT (project_id) DO NOTHING
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun createProduction(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 0)"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setString(2, itemId)
                statement.setString(3, name)
            }
        ).process(Unit)
    }

    private fun createTask(projectId: Int, name: String, completed: Boolean) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO action_task (project_id, name, completed) VALUES (?, ?, ?)"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setString(2, name)
                statement.setBoolean(3, completed)
            }
        ).process(Unit)
    }
}
