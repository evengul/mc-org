package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.GetProjectsInWorldStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetProjectsInWorldStepTest : WithUser() {

    private var worldId: Int = 0
    private var otherWorldId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld("GetProjectsInWorld Primary")
        otherWorldId = createWorld("GetProjectsInWorld Other")
    }

    @Test
    fun `returns empty list when world has no projects`() {
        val emptyWorldId = createWorld("GetProjectsInWorld Empty")
        val result = runBlocking { GetProjectsInWorldStep(excludeProjectId = -1).process(emptyWorldId) }
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).value.isEmpty())
    }

    @Test
    fun `returns all projects in the world except the excluded one`() {
        val anchor = createProject(worldId, "Anchor")
        val a = createProject(worldId, "Another A")
        val b = createProject(worldId, "Another B")

        val result = runBlocking { GetProjectsInWorldStep(excludeProjectId = anchor).process(worldId) }

        assertIs<Result.Success<*>>(result)
        val projects = (result as Result.Success).value
        val ids = projects.map { it.first }
        assertTrue(a in ids)
        assertTrue(b in ids)
        assertTrue(anchor !in ids)
    }

    @Test
    fun `does not return projects from other worlds`() {
        val inThisWorld = createProject(worldId, "In This World")
        val inOtherWorld = createProject(otherWorldId, "In Other World")

        val result = runBlocking { GetProjectsInWorldStep(excludeProjectId = -1).process(worldId) }

        assertIs<Result.Success<*>>(result)
        val ids = (result as Result.Success).value.map { it.first }
        assertTrue(inThisWorld in ids)
        assertTrue(inOtherWorld !in ids)
    }

    @Test
    fun `results are ordered by name`() {
        val orderedWorld = createWorld("GetProjectsInWorld Ordered")
        createProject(orderedWorld, "Zeta")
        createProject(orderedWorld, "Alpha")
        createProject(orderedWorld, "Mu")

        val result = runBlocking { GetProjectsInWorldStep(excludeProjectId = -1).process(orderedWorld) }
        assertIs<Result.Success<*>>(result)
        val names = (result as Result.Success).value.map { it.second }
        assertEquals(listOf("Alpha", "Mu", "Zeta"), names)
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4"),
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'IDEA', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
