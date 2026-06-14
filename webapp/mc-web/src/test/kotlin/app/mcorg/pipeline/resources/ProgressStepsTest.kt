package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByRgIdInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressDeltaInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressDeltaStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressStep
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

/**
 * Integration tests for progress persistence steps:
 * - [UpsertProgressStep]: set absolute value, clamping, upsert idempotency
 * - [UpsertProgressDeltaStep]: increment, decrement, clamping
 * - [GetProgressForProjectStep]: load all progress for a project
 * - Round-trip: progress is reflected via GetAllResourceGatheringItemsStep
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProgressStepsTest : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UpsertProgressStep — absolute value
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `UpsertProgressStep - sets collected and is readable via GetProgressForProjectStep`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:iron_ingot", required = 64)

        val upsert = runBlocking {
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 32))
        }
        assertIs<Result.Success<Unit>>(upsert)

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(32, progress.value["minecraft:iron_ingot"])
    }

    @Test
    fun `UpsertProgressStep - clamps value above required`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:gold_ingot", required = 10)

        runBlocking { UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 999)) }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(10, progress.value["minecraft:gold_ingot"], "should be clamped to required=10")
    }

    @Test
    fun `UpsertProgressStep - clamps negative value to 0`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:diamond", required = 10)

        runBlocking { UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, -5)) }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(0, progress.value.getOrDefault("minecraft:diamond", 0))
    }

    @Test
    fun `UpsertProgressStep - second call replaces first`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:emerald", required = 20)

        runBlocking {
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 5))
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 15))
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(15, progress.value["minecraft:emerald"], "second upsert should overwrite first")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UpsertProgressDeltaStep — delta
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `UpsertProgressDeltaStep - increments from zero`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:oak_log", required = 64)

        runBlocking { UpsertProgressDeltaStep.process(UpsertProgressDeltaInput(rgId, 10)) }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(10, progress.value["minecraft:oak_log"])
    }

    @Test
    fun `UpsertProgressDeltaStep - accumulates multiple increments`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:stone", required = 100)

        runBlocking {
            UpsertProgressDeltaStep.process(UpsertProgressDeltaInput(rgId, 30))
            UpsertProgressDeltaStep.process(UpsertProgressDeltaInput(rgId, 25))
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(55, progress.value["minecraft:stone"])
    }

    @Test
    fun `UpsertProgressDeltaStep - decrement clamps to 0`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:gravel", required = 50)

        runBlocking {
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 5))
            UpsertProgressDeltaStep.process(UpsertProgressDeltaInput(rgId, -100))
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(0, progress.value.getOrDefault("minecraft:gravel", 0))
    }

    @Test
    fun `UpsertProgressDeltaStep - increment clamps to required`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:cobblestone", required = 20)

        runBlocking {
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 18))
            UpsertProgressDeltaStep.process(UpsertProgressDeltaInput(rgId, 10))
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(20, progress.value["minecraft:cobblestone"], "should be clamped to required=20")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetProgressForProjectStep
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GetProgressForProjectStep - returns empty map when no progress exists`() {
        val pid = createProject(worldId)

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertTrue(progress.value.isEmpty(), "new project should have no progress rows")
    }

    @Test
    fun `GetProgressForProjectStep - only returns progress for the requested project`() {
        val pid1 = createProject(worldId)
        val pid2 = createProject(worldId)
        val rg1 = createResourceGathering(pid1, "minecraft:sand", required = 10)
        val rg2 = createResourceGathering(pid2, "minecraft:gravel", required = 10)

        runBlocking {
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rg1, 3))
            UpsertProgressStep.process(UpsertProgressByRgIdInput(rg2, 7))
        }

        val progress1 = runBlocking { GetProgressForProjectStep.process(pid1) }
        assertIs<Result.Success<Map<String, Int>>>(progress1)
        assertEquals(setOf("minecraft:sand"), progress1.value.keys)

        val progress2 = runBlocking { GetProgressForProjectStep.process(pid2) }
        assertIs<Result.Success<Map<String, Int>>>(progress2)
        assertEquals(setOf("minecraft:gravel"), progress2.value.keys)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Round-trip — progress surfaces on the resource item read
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `progress is reflected as collected via GetAllResourceGatheringItemsStep`() {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:copper_ingot", required = 64)

        runBlocking { UpsertProgressStep.process(UpsertProgressByRgIdInput(rgId, 40)) }

        val items = runBlocking { GetAllResourceGatheringItemsStep.process(pid) }
        assertIs<Result.Success<*>>(items)
        val item = (items.value as List<*>)
            .map { it as app.mcorg.domain.model.resources.ResourceGatheringItem }
            .single { it.itemId == "minecraft:copper_ingot" }
        assertEquals(40, item.collected, "collected should read through the progress table join")
        assertEquals(64, item.required)
    }

    @Test
    fun `item with no progress row reads collected as 0`() {
        val pid = createProject(worldId)
        createResourceGathering(pid, "minecraft:redstone", required = 32)

        val items = runBlocking { GetAllResourceGatheringItemsStep.process(pid) }
        assertIs<Result.Success<*>>(items)
        val item = (items.value as List<*>)
            .map { it as app.mcorg.domain.model.resources.ResourceGatheringItem }
            .single { it.itemId == "minecraft:redstone" }
        assertEquals(0, item.collected, "absent progress row should COALESCE to 0")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UpsertProgressByItemStep — item-keyed delta (for plan activities)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `UpsertProgressByItemStep - creates progress row from scratch`() {
        val pid = createProject(worldId)

        val result = runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:netherite_ingot", delta = 5, required = 20)
            )
        }
        assertIs<Result.Success<Unit>>(result)

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(5, progress.value["minecraft:netherite_ingot"])
    }

    @Test
    fun `UpsertProgressByItemStep - accumulates increments`() {
        val pid = createProject(worldId)

        runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:lapis_lazuli", delta = 10, required = 64)
            )
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:lapis_lazuli", delta = 15, required = 64)
            )
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(25, progress.value["minecraft:lapis_lazuli"])
    }

    @Test
    fun `UpsertProgressByItemStep - decrement clamps to 0`() {
        val pid = createProject(worldId)

        runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:quartz", delta = 5, required = 100)
            )
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:quartz", delta = -1000, required = 100)
            )
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(0, progress.value.getOrDefault("minecraft:quartz", 0))
    }

    @Test
    fun `UpsertProgressByItemStep - increment clamps to required`() {
        val pid = createProject(worldId)

        runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:blaze_rod", delta = 1728, required = 10)
            )
        }

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(10, progress.value["minecraft:blaze_rod"], "should be clamped to required=10")
    }

    @Test
    fun `UpsertProgressByItemStep - does not require a resource_gathering row`() {
        val pid = createProject(worldId)
        // No resource_gathering row inserted — derived plan activity

        val result = runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(pid, "minecraft:slime_ball", delta = 3, required = 64)
            )
        }
        assertIs<Result.Success<Unit>>(result)

        val progress = runBlocking { GetProgressForProjectStep.process(pid) }
        assertIs<Result.Success<Map<String, Int>>>(progress)
        assertEquals(3, progress.value["minecraft:slime_ball"])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fixture helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "ProgressSteps IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4"),
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES ('ProgressSteps Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, itemId: String, required: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) " +
                    "VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, itemId.substringAfterLast(":"))
                stmt.setInt(4, required)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
