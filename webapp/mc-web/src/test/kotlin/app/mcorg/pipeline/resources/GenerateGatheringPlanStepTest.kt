package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GenerateGatheringPlanStep]:
 * - success path: targets build from resource_gathering rows, engine plan returned
 * - world not found: fails with NotFound
 * - empty targets (all collected): fails with ValidationError
 * - linked project becomes a SUPPLIED terminal in the plan
 * - unknown version (graph not ingested): fails with NotFound
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GenerateGatheringPlanStepTest : WithUser() {

    private val version = MinecraftVersion.Release(1, 99, 0)

    private val log = Item("minecraft:oak_log", "Oak Log")
    private val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
    private val birchPlanks = Item("minecraft:birch_planks", "Birch Planks")
    private val chest = Item("minecraft:chest", "Chest")
    private val planksTag = MinecraftTag("#minecraft:planks", "Planks", listOf(oakPlanks, birchPlanks))

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        CacheManager.invalidateAll()

        // Store a small but realistic graph for version 1.99.0 (test-only)
        val serverData = ServerData(
            version = version,
            items = listOf(log, oakPlanks, birchPlanks, chest, planksTag),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/oak_log.json",
                    producedItems = listOf(log to ResourceQuantity.ItemQuantity(1))
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                    filename = "oak_planks.json",
                    requiredItems = listOf(log to ResourceQuantity.ItemQuantity(1)),
                    producedItems = listOf(oakPlanks to ResourceQuantity.ItemQuantity(4))
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "chest.json",
                    requiredItems = listOf(planksTag to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(chest to ResourceQuantity.ItemQuantity(1))
                )
            )
        )
        val stored = runBlocking { StoreMinecraftDataStep.process(serverData) }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld(version)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Success path: targets build from gathering rows → engine returns a plan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `success path - targets build from rows and engine returns a plan`() {
        val projectId = createProject(worldId)
        val rgId = createResourceGathering(projectId)
        insertGatheringItem(projectId, chest.id, chest.name, required = 4, collected = 0)

        val result = runBlocking {
            GenerateGatheringPlanStep.process(
                GatheringPlanInput(projectId, worldId, rgId)
            )
        }

        assertIs<Result.Success<*>>(result)
        val plan = (result as Result.Success).value
        val chestNode = plan.nodes[chest.id]
        assertNotNull(chestNode, "chest must be in the plan")
        assertEquals(4L, chestNode.quantity)
        assertEquals(PlanNodeStatus.RESOLVED, chestNode.status)
        // planks tag is OPEN_TAG — 4 chests × 8 planks
        val planksNode = plan.nodes[planksTag.id]
        assertNotNull(planksNode, "#minecraft:planks must be in the plan")
        assertEquals(32L, planksNode.quantity)
        assertEquals(PlanNodeStatus.OPEN_TAG, planksNode.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure: world not found
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `failure - world not found returns NotFound`() {
        val projectId = createProject(worldId)
        val rgId = createResourceGathering(projectId)

        val result = runBlocking {
            GenerateGatheringPlanStep.process(
                GatheringPlanInput(projectId, worldId = Int.MAX_VALUE, resourceGatheringId = rgId)
            )
        }

        assertIs<Result.Failure<*>>(result)
        assertTrue(
            (result as Result.Failure).error is app.mcorg.pipeline.failure.AppFailure.DatabaseError.NotFound,
            "Expected NotFound for unknown worldId"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure: version has no ingested graph
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `failure - version with no ingested graph returns NotFound`() {
        // Create a world with a version that has no resource_source data
        val uningestedVersion = MinecraftVersion.fromString("0.0.1")
        val uningestedWorldId = createWorld(uningestedVersion)
        val projectId = createProject(uningestedWorldId)
        val rgId = createResourceGathering(projectId)
        insertGatheringItem(projectId, chest.id, chest.name, required = 1, collected = 0)

        val result = runBlocking {
            GenerateGatheringPlanStep.process(
                GatheringPlanInput(projectId, uningestedWorldId, rgId)
            )
        }

        assertIs<Result.Failure<*>>(result)
        assertTrue(
            (result as Result.Failure).error is app.mcorg.pipeline.failure.AppFailure.DatabaseError.NotFound,
            "Expected NotFound when version has no ingested graph"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure: empty targets (all items fully collected)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `failure - all items fully collected returns ValidationError`() {
        val projectId = createProject(worldId)
        val rgId = createResourceGathering(projectId)
        // required == collected → net = 0 → no targets
        insertGatheringItem(projectId, chest.id, chest.name, required = 4, collected = 4)

        val result = runBlocking {
            GenerateGatheringPlanStep.process(
                GatheringPlanInput(projectId, worldId, rgId)
            )
        }

        assertIs<Result.Failure<*>>(result)
        assertTrue(
            (result as Result.Failure).error is app.mcorg.pipeline.failure.AppFailure.ValidationError,
            "Expected ValidationError when all items are fully collected"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Supplied: item linked to another project becomes a SUPPLIED terminal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `linked project item becomes SUPPLIED terminal in plan`() {
        val producerProjectId = createProject(worldId)
        val consumerProjectId = createProject(worldId)
        val rgId = createResourceGathering(consumerProjectId)

        // chest is the main target (needs planning)
        insertGatheringItem(consumerProjectId, chest.id, chest.name, required = 2, collected = 0)
        // oak_planks is sourced from producerProjectId
        val rgItemId = insertGatheringItem(
            consumerProjectId, oakPlanks.id, oakPlanks.name, required = 10, collected = 0
        )
        // Mark oak_planks as supplied by the producer project
        linkResourceToProject(rgItemId, producerProjectId)

        val result = runBlocking {
            GenerateGatheringPlanStep.process(
                GatheringPlanInput(consumerProjectId, worldId, rgId)
            )
        }

        assertIs<Result.Success<*>>(result)
        val plan = (result as Result.Success).value
        val planksNode = plan.nodes[oakPlanks.id]
        assertNotNull(planksNode, "oak_planks must be in the plan as a SUPPLIED node")
        assertEquals(PlanNodeStatus.SUPPLIED, planksNode.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fixture helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createWorld(v: MinecraftVersion): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "GatheringPlanStep Test World (${v})",
                description = "test",
                version = v
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES ('GatheringPlanStep Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, collected) " +
                    "VALUES (?, 'minecraft:placeholder', 'Placeholder', 1, 1) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun insertGatheringItem(
        projectId: Int,
        itemId: String,
        name: String,
        required: Int,
        collected: Int
    ): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, collected) " +
                    "VALUES (?, ?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setInt(4, required)
                stmt.setInt(5, collected)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun linkResourceToProject(resourceGatheringId: Int, solvedByProjectId: Int) {
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.update(
                    "UPDATE resource_gathering SET source_type = 'project', solved_by_project_id = ? WHERE id = ?"
                ),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, solvedByProjectId)
                    stmt.setInt(2, resourceGatheringId)
                }
            ).process(Unit)
        }
    }
}
