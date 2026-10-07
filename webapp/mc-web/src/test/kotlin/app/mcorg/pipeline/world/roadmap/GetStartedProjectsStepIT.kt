package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
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

/**
 * MCO-579's interim rule: a project is started once anything is collected or any task is done,
 * whatever state it was declared in.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetStartedProjectsStepIT : WithUser() {

    @Test
    fun `a project is started by something collected or a task done, not by its declared state`() = runBlocking {
        val worldId = createWorld("Started World")
        val untouchedImport = createProject(worldId, "Untouched Import", state = "ACTIVE")
        val collecting = createProject(worldId, "Collecting").also { collect(it, "minecraft:stone", 12) }
        val zeroRow = createProject(worldId, "Zero Row").also { collect(it, "minecraft:stone", 0) }
        val taskDone = createProject(worldId, "Task Done").also { task(it, completed = true) }
        val taskOpen = createProject(worldId, "Task Open").also { task(it, completed = false) }

        val otherWorld = createWorld("Other World")
        createProject(otherWorld, "Elsewhere").also { collect(it, "minecraft:stone", 5) }

        val started = (GetStartedProjectsStep(worldId).process(Unit) as Result.Success).value

        assertEquals(setOf(collecting, taskDone), started)
        listOf(untouchedImport, zeroRow, taskOpen).forEach { assert(it !in started) }

        deleteWorld(worldId)
        deleteWorld(otherWorld)
    }

    private suspend fun createWorld(name: String): Int =
        (CreateWorldStep(user).process(
            CreateWorldInput("$name-${System.nanoTime()}", "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value

    private suspend fun deleteWorld(worldId: Int) {
        DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(worldId)
    }

    private suspend fun createProject(worldId: Int, name: String, state: String = "PENDING"): Int =
        (DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state)
            }
        ).process(Unit) as Result.Success).value

    private suspend fun collect(projectId: Int, itemId: String, collected: Int) {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO resource_gathering_progress (project_id, item_id, collected) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setInt(3, collected)
            }
        ).process(Unit)
    }

    private suspend fun task(projectId: Int, completed: Boolean) {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO action_task (project_id, name, completed) VALUES (?, 'Place the hoppers', ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setBoolean(2, completed)
            }
        ).process(Unit)
    }
}
