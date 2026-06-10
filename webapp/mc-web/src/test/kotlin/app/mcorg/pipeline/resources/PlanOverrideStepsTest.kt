package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlanner
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
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

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PlanOverrideStepsTest : WithUser() {

    private var resourceGatheringId: Int = 0

    @BeforeAll
    fun setup() {
        val worldId = createWorld()
        val projectId = createProject(worldId)
        resourceGatheringId = createResourceGathering(projectId)
    }

    @Test
    fun `overrides round-trip into engine-ready PlanOverrides`() {
        val rgId = createResourceGathering(createProject(createWorld()))

        runBlocking {
            assertIs<Result.Success<*>>(
                UpsertPlanOverrideStep(rgId).process(
                    PlanOverride.Source("minecraft:oak_planks", "minecraft:chest:chests/bonus_chest.json")
                )
            )
            assertIs<Result.Success<*>>(
                UpsertPlanOverrideStep(rgId).process(
                    PlanOverride.TagMember("#minecraft:planks", "minecraft:oak_planks")
                )
            )
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(rgId) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(
            PlanOverrides(
                sourceByItem = mapOf("minecraft:oak_planks" to "minecraft:chest:chests/bonus_chest.json"),
                tagMember = mapOf("#minecraft:planks" to "minecraft:oak_planks")
            ),
            loaded.value
        )
    }

    @Test
    fun `upserting the same item replaces the previous choice`() {
        runBlocking {
            UpsertPlanOverrideStep(resourceGatheringId)
                .process(PlanOverride.Source("minecraft:stick", "minecraft:entity:entities/witch.json"))
            UpsertPlanOverrideStep(resourceGatheringId)
                .process(PlanOverride.Source("minecraft:stick", "minecraft:crafting_shaped:stick.json"))
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(resourceGatheringId) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals("minecraft:crafting_shaped:stick.json", loaded.value.sourceByItem["minecraft:stick"])
    }

    @Test
    fun `clearing an override removes it`() {
        val rgId = createResourceGathering(createProject(createWorld()))
        runBlocking {
            UpsertPlanOverrideStep(rgId)
                .process(PlanOverride.Source("minecraft:glass", "minecraft:smelting:glass.json"))
            ClearPlanOverrideStep(rgId).process("minecraft:glass")
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(rgId) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(PlanOverrides.NONE, loaded.value)
    }

    @Test
    fun `a saved selection re-derives the identical plan`() {
        val rgId = createResourceGathering(createProject(createWorld()))
        val graph = woodGraph()
        val targets = listOf(PlanTarget(Item("minecraft:chest", "Chest"), 4))
        val pinned = PlanOverrides(
            sourceByItem = mapOf("minecraft:oak_planks" to "minecraft:chest:chests/bonus_chest.json")
        )

        val original = GatheringPlanner.plan(graph, targets, overrides = pinned)
        assertEquals(
            PlanNodeStatus.RAW_GATHER,
            original.nodes.getValue("minecraft:oak_planks").status
        )

        runBlocking {
            UpsertPlanOverrideStep(rgId).process(
                PlanOverride.Source("minecraft:oak_planks", "minecraft:chest:chests/bonus_chest.json")
            )
        }
        val loaded = runBlocking { GetPlanOverridesStep.process(rgId) }
        assertIs<Result.Success<PlanOverrides>>(loaded)

        val rederived = GatheringPlanner.plan(graph, targets, overrides = loaded.value)
        assertEquals(original.nodes, rederived.nodes)
        assertEquals(original.activityList, rederived.activityList)
    }

    private fun woodGraph(): ItemSourceGraph {
        val log = Item("minecraft:oak_log", "Oak Log")
        val planks = Item("minecraft:oak_planks", "Oak Planks")
        val chest = Item("minecraft:chest", "Chest")
        val builder = ItemSourceGraph.builder()

        val mine = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/oak_log.json")
        builder.addSourceToItemEdge(mine, builder.addItemNode(log), 1)

        val craftPlanks = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS, "oak_planks.json")
        builder.addSourceToItemEdge(craftPlanks, builder.addItemNode(planks), 4)
        builder.addItemToSourceEdge(builder.addItemNode(log), craftPlanks, 1)

        val lootPlanks = builder.addSourceNode(ResourceSource.SourceType.LootTypes.CHEST, "chests/bonus_chest.json")
        builder.addSourceToItemEdge(lootPlanks, builder.addItemNode(planks), 1)

        val craftChest = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "chest.json")
        builder.addSourceToItemEdge(craftChest, builder.addItemNode(chest), 1)
        builder.addItemToSourceEdge(builder.addItemNode(planks), craftChest, 8)

        return builder.build()
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "PlanOverride IT World",
                description = "test",
                version = MinecraftVersion.Release.fromString("1.21.4"),
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES ('PlanOverride Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, collected) " +
                    "VALUES (?, 'minecraft:chest', 'Chest', 4, 0) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        (result as Result.Success).value
    }
}
