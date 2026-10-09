package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FarmRuns.load] against real rows (MCO-603): modes read from `project_production_modes`, the
 * same farm the plan names when two make an item, and the planned project's own farm left out.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FarmRunsLoadIT : WithUser() {

    @Test
    fun `a plan's farm runs name the farm the plan does, and its modes`() {
        val worldId = createWorld("Farm Runs World")
        val storage = createProject(worldId, "Storage System")
        val tree = createProject(worldId, "Tree Farm")
        val oak = mode(tree, "Oak Mode", 0)
        val spruce = mode(tree, "Spruce Mode", 1)
        val azalea = mode(tree, "Azalea Mode", 2)
        produce(tree, "minecraft:oak_log", 48_000, oak)
        produce(tree, "minecraft:spruce_log", 37_600, spruce)
        produce(tree, "minecraft:oak_log", 38_100, azalea)
        // A slower farm that sorts first by name: it must not take Oak Log from the Tree Farm.
        val alpha = createProject(worldId, "Alpha Logs")
        produce(alpha, "minecraft:oak_log", 1_000)
        // The planned project makes Spruce Log itself, and that never supplies its own plan.
        produce(storage, "minecraft:spruce_log", 99_999)
        listOf(tree, alpha).forEach { runBlocking { UpdateProjectStageStep(it).process(ProjectStage.COMPLETED) } }

        val runs = runBlocking {
            FarmRuns.load(
                worldId = worldId,
                projectId = storage,
                lines = listOf(
                    SuppliedLine("minecraft:spruce_log", "Spruce Log", quantity = 28_191, logged = 0),
                    SuppliedLine("minecraft:oak_log", "Oak Log", quantity = 57, logged = 0),
                ),
            )
        }

        val run = (runs as Result.Success).value.single()
        assertEquals("Tree Farm", run.projectName)
        assertEquals(listOf("Oak Mode", "Spruce Mode"), run.modes.map { it.modeName })
        assertEquals("minecraft:spruce_log", run.setBy?.line?.itemId)
        assertEquals(45.0, run.hoursLeft!! * 60, 0.5)

        deleteWorld(worldId)
    }

    @Test
    fun `no supplied lines need no queries and give no runs`() {
        val runs = runBlocking { FarmRuns.load(worldId = 0, projectId = 0, lines = emptyList()) }

        assertTrue((runs as Result.Success).value.isEmpty())
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = MinecraftVersion.fromString("1.20.1"))
        )
        (result as Result.Success).value
    }

    private fun deleteWorld(worldId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(worldId)
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun mode(projectId: Int, name: String, position: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_production_modes (project_id, name, position) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, name)
                stmt.setInt(3, position)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun produce(projectId: Int, itemId: String, ratePerHour: Int, modeId: Int? = null) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_productions (project_id, mode_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                if (modeId == null) stmt.setNull(2, java.sql.Types.INTEGER) else stmt.setInt(2, modeId)
                stmt.setString(3, itemId)
                stmt.setString(4, itemId.substringAfter(':'))
                stmt.setInt(5, ratePerHour)
            }
        ).process(Unit)
    }
}
