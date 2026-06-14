package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.commonsteps.ClearResourceSourceStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.pipeline.resources.commonsteps.ResourceSourceAssignment
import app.mcorg.pipeline.resources.commonsteps.SetResourceSourceStep
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
import kotlin.test.assertNull

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ResourceSourceStepsTest : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var otherProjectId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId, "ResourceSource Primary")
        otherProjectId = createProject(worldId, "ResourceSource Sibling")
    }

    @Test
    fun `SetResourceSource with Manual writes source_type=manual and null project_id`() {
        val rgId = createResourceGathering(projectId)

        val result = runBlocking {
            SetResourceSourceStep(rgId).process(ResourceSourceAssignment.Manual)
        }
        assertIs<Result.Success<*>>(result)

        val stored = runBlocking { GetResourceGatheringItemStep.process(rgId) }
        assertIs<Result.Success<*>>(stored)
        val item = (stored as Result.Success).value
        assertEquals("manual", item.sourceType)
        assertNull(item.solvedByProject)
    }

    @Test
    fun `SetResourceSource with LinkedProject writes source_type=project and FK`() {
        val rgId = createResourceGathering(projectId)

        val result = runBlocking {
            SetResourceSourceStep(rgId).process(ResourceSourceAssignment.LinkedProject(otherProjectId))
        }
        assertIs<Result.Success<*>>(result)

        val stored = runBlocking { GetResourceGatheringItemStep.process(rgId) }
        assertIs<Result.Success<*>>(stored)
        val item = (stored as Result.Success).value
        assertEquals("project", item.sourceType)
        assertEquals(otherProjectId, item.solvedByProject?.first)
        assertEquals("ResourceSource Sibling", item.solvedByProject?.second)
    }

    @Test
    fun `ClearResourceSource nulls both columns`() {
        val rgId = createResourceGathering(projectId)
        runBlocking {
            SetResourceSourceStep(rgId).process(ResourceSourceAssignment.LinkedProject(otherProjectId))
        }

        val result = runBlocking { ClearResourceSourceStep(rgId).process(Unit) }
        assertIs<Result.Success<*>>(result)

        val stored = runBlocking { GetResourceGatheringItemStep.process(rgId) }
        assertIs<Result.Success<*>>(stored)
        val item = (stored as Result.Success).value
        assertNull(item.sourceType)
        assertNull(item.solvedByProject)
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "ResourceSource IT World",
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
                        "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', 10) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        (result as Result.Success).value
    }
}
