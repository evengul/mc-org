package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-460 — the gunpowder/cobblestone shape, end to end against the real queries.
 *
 * `RoadmapCyclesTest` covers the detection arithmetic on constructed edges. This covers what
 * only the database can answer: that the farm-scale threshold actually reaches the SQL, that a
 * surviving loop is reported as a cycle rather than as two contradicting rows, and that a saved
 * ordering removes the edge on both surfaces.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class RoadmapCycleIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 21, 4)

    // ---- the reported shape ----------------------------------------------------------

    @Test
    fun `a loop resting on a footnote is settled without asking`() = runBlocking {
        // Even's case: the cobblestone farm needs 20 gunpowder for TNT, the witch farm makes
        // gunpowder, and that used to be an edge exactly as strong as 75,151 cobblestone.
        val world = createWorld("Threshold world")
        val cobble = createProject(world, "Cobblestone Farm")
        val witch = createProject(world, "Witch Hut Farm")

        addProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        addProduction(witch, "minecraft:gunpowder", "Gunpowder")
        addDemand(cobble, "minecraft:gunpowder", "Gunpowder", 20)
        addDemand(witch, "minecraft:cobblestone", "Cobblestone", 75_151)

        val roadmap = (GetWorldRoadMapStep(world).process(Unit) as Result.Success).value

        // The footnote is the edge set aside to break the loop, so it comes off the table with
        // it — one graph behind every claim. Small edges are only ever dropped when they are
        // load-bearing in a cycle; a Beacon needing 32 iron keeps its edge (GetWorldRoadMapStepTest).
        assertEquals(
            listOf("Witch Hut Farm" to "Cobblestone Farm"),
            roadmap.edges.map { it.fromNodeName to it.toNodeName },
            "the farm-scale claim is what sequences the pair",
        )
        // But the loop it closes is settled on the threshold, without interrupting anyone.
        assertTrue(
            roadmap.cycles.isEmpty(),
            "20 gunpowder against 75,151 cobblestone is not a judgement call: ${roadmap.cycles}",
        )
        // The ordering claims agree: the witch farm waits, the cobblestone farm does not.
        assertEquals(0, roadmap.nodes.single { it.projectName == "Cobblestone Farm" }.layer)
        assertEquals(1, roadmap.nodes.single { it.projectName == "Witch Hut Farm" }.layer)
    }

    @Test
    fun `two farm-scale claims both ways is reported as a cycle`() = runBlocking {
        val world = createWorld("Real cycle world")
        val cobble = createProject(world, "Cobblestone Farm")
        val witch = createProject(world, "Witch Hut Farm")

        addProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        addProduction(witch, "minecraft:gunpowder", "Gunpowder")
        // Both above the 1,728 default, so no principle picks a winner.
        addDemand(cobble, "minecraft:gunpowder", "Gunpowder", 2_400)
        addDemand(witch, "minecraft:cobblestone", "Cobblestone", 75_151)

        val roadmap = (GetWorldRoadMapStep(world).process(Unit) as Result.Success).value

        val cycle = roadmap.cycles.single()
        assertEquals(listOf("Cobblestone Farm", "Witch Hut Farm"), cycle.projectNames)
        assertEquals(2, cycle.options.size, "either farm could be the one to go first")
        assertEquals("Cobblestone Farm", cycle.breaking.firstProjectName, "the smaller claim gives way")

        // AC 1, and the part that is easy to half-fix: it is not enough for the *layers* to
        // disagree while the columns still say each blocks the other. Every claim on the page
        // comes off one graph.
        val layers = roadmap.nodes.associate { it.projectName to it.layer }
        assertEquals(0, layers.getValue("Cobblestone Farm"))
        assertEquals(1, layers.getValue("Witch Hut Farm"))

        val cobbleNode = roadmap.nodes.single { it.projectName == "Cobblestone Farm" }
        val witchNode = roadmap.nodes.single { it.projectName == "Witch Hut Farm" }
        assertTrue(!cobbleNode.isBlocked, "it is at depth 0; it cannot also be blocked")
        assertTrue(cobbleNode.blockingProjectIds.isEmpty())
        assertTrue(witchNode.isBlocked, "and the one that waits says so")
        assertEquals(listOf(cobbleNode.projectId), witchNode.blockingProjectIds)

        assertEquals(
            listOf("Witch Hut Farm" to "Cobblestone Farm"),
            roadmap.edges.map { it.fromNodeName to it.toNodeName },
            "the set-aside edge is off the table too, not just out of the layering",
        )
    }

    @Test
    fun `a saved ordering removes the edge and closes the question`() = runBlocking {
        val world = createWorld("Ordered cycle world")
        val cobble = createProject(world, "Cobblestone Farm")
        val witch = createProject(world, "Witch Hut Farm")

        addProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        addProduction(witch, "minecraft:gunpowder", "Gunpowder")
        addDemand(cobble, "minecraft:gunpowder", "Gunpowder", 2_400)
        addDemand(witch, "minecraft:cobblestone", "Cobblestone", 75_151)

        // The user picks the other direction than the guess: the witch farm goes first.
        saveOrder(world, first = witch, waiting = cobble)

        val roadmap = (GetWorldRoadMapStep(world).process(Unit) as Result.Success).value

        assertTrue(roadmap.cycles.isEmpty(), "answered, so no longer an open question")
        assertEquals(
            listOf("Witch Hut Farm" to "Cobblestone Farm"),
            roadmap.resolvedOrders.map { it.firstProjectName to it.waitingProjectName },
            "but still visible, or it could never be changed",
        )
        assertTrue(
            roadmap.edges.none { it.fromNodeName == "Witch Hut Farm" },
            "the witch farm no longer waits on anything: ${roadmap.edges.map { it.fromNodeName to it.toNodeName }}",
        )
        assertEquals(0, roadmap.nodes.single { it.projectName == "Witch Hut Farm" }.layer)
        assertEquals(1, roadmap.nodes.single { it.projectName == "Cobblestone Farm" }.layer)
    }

    @Test
    fun `an operational farm is nobody's prerequisite`() = runBlocking {
        // MCO-287's rule, unchanged by any of this: DONE supplies now, so it cannot be in a
        // loop with what it supplies.
        val world = createWorld("Done farm world")
        val cobble = createProject(world, "Cobblestone Farm", state = ProjectState.DONE)
        val witch = createProject(world, "Witch Hut Farm")

        addProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        addProduction(witch, "minecraft:gunpowder", "Gunpowder")
        addDemand(cobble, "minecraft:gunpowder", "Gunpowder", 2_400)
        addDemand(witch, "minecraft:cobblestone", "Cobblestone", 75_151)

        val roadmap = (GetWorldRoadMapStep(world).process(Unit) as Result.Success).value

        assertTrue(
            roadmap.nodes.single { it.projectName == "Witch Hut Farm" }.blockingProjectIds.isEmpty(),
            "a running cobblestone farm blocks nothing",
        )
    }

    // ---- fixtures --------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(
        worldId: Int,
        name: String,
        state: ProjectState = ProjectState.ACTIVE,
    ): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state.name)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun addProduction(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 1000)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    /**
     * Writes materialised plan demand directly (MCO-316's `project_demand`).
     *
     * The roadmap derives missing demand itself, but that needs an ingested Minecraft graph
     * this test has no reason to build — the question here is what the edge query does with
     * demand, not how demand is produced.
     */
    private fun addDemand(projectId: Int, itemId: String, itemName: String, quantity: Long) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand (project_id, item_id, item_name, quantity, activity_group, node_status) " +
                    "VALUES (?, ?, ?, ?, 'GATHER', 'RAW_GATHER')"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, itemName)
                stmt.setLong(4, quantity)
            }
        ).process(Unit)
    }

    private fun saveOrder(worldId: Int, first: Int, waiting: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO roadmap_cycle_order (world_id, consumer_project_id, producer_project_id) VALUES (?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, first)
                stmt.setInt(3, waiting)
            }
        ).process(Unit)
    }
}
