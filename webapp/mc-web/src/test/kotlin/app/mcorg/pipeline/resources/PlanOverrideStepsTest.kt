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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PlanOverrideStepsTest : WithUser() {

    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        val worldId = createWorld()
        projectId = createProject(worldId)
    }

    @Test
    fun `overrides round-trip into engine-ready PlanOverrides`() {
        val pid = createProject(createWorld())

        runBlocking {
            assertIs<Result.Success<*>>(
                UpsertPlanOverrideStep(pid).process(
                    PlanOverride.Source("minecraft:oak_planks", "minecraft:chest:chests/bonus_chest.json")
                )
            )
            assertIs<Result.Success<*>>(
                UpsertPlanOverrideStep(pid).process(
                    PlanOverride.TagMember("#minecraft:planks", "minecraft:oak_planks")
                )
            )
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
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
            UpsertPlanOverrideStep(projectId)
                .process(PlanOverride.Source("minecraft:stick", "minecraft:entity:entities/witch.json"))
            UpsertPlanOverrideStep(projectId)
                .process(PlanOverride.Source("minecraft:stick", "minecraft:crafting_shaped:stick.json"))
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(projectId) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals("minecraft:crafting_shaped:stick.json", loaded.value.sourceByItem["minecraft:stick"])
    }

    // ---- MCO-506: the row is a diff, and re-answering keeps both answers ------------------

    /**
     * The whole point of the column. Without it a row says "the user picked cobbled deepslate"
     * and nothing else - afterwards it is impossible to tell whether that corrected the planner,
     * agreed with it, or answered something the planner had no opinion on.
     */
    @Test
    fun `an override records what the planner would have picked`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.TagMember(
                    "#minecraft:stone_crafting_materials",
                    "minecraft:cobbled_deepslate",
                    plannerPick = "minecraft:cobblestone",
                )
            )
        }

        val rows = readRows(pid, "#minecraft:stone_crafting_materials")
        assertEquals(1, rows.size)
        assertEquals("minecraft:cobbled_deepslate", rows.single().tagMember)
        assertEquals("minecraft:cobblestone", rows.single().plannerPick)
    }

    /**
     * Re-answering used to `ON CONFLICT ... DO UPDATE` over the previous answer, which destroyed
     * the stronger of the two signals: someone changing their mind about a question they have
     * already answered once.
     */
    @Test
    fun `re-answering supersedes the old row instead of destroying it, and the plan reads the live one`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.TagMember("#minecraft:coals", "minecraft:charcoal", plannerPick = "minecraft:coal")
            )
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.TagMember("#minecraft:coals", "minecraft:coal", plannerPick = "minecraft:coal")
            )
        }

        val rows = readRows(pid, "#minecraft:coals")
        assertEquals(2, rows.size, "both answers must survive")
        assertEquals(1, rows.count { it.superseded }, "exactly one is history")
        assertEquals(1, rows.count { !it.superseded }, "exactly one is live")
        assertEquals("minecraft:charcoal", rows.single { it.superseded }.tagMember)
        assertEquals("minecraft:coal", rows.single { !it.superseded }.tagMember)

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(
            mapOf("#minecraft:coals" to "minecraft:coal"),
            loaded.value.tagMember,
            "the plan must read exactly the live answer, once",
        )
    }

    /**
     * Clicking the already-selected option is how the picker reads back the current state, not a
     * change of mind. Counting it would inflate the very signal the history exists to measure.
     */
    @Test
    fun `re-submitting the answer that is already live adds no row and keeps the original planner pick`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.TagMember("#minecraft:planks", "minecraft:oak_planks", plannerPick = "minecraft:oak_planks")
            )
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.TagMember("#minecraft:planks", "minecraft:oak_planks", plannerPick = "minecraft:birch_planks")
            )
        }

        val rows = readRows(pid, "#minecraft:planks")
        assertEquals(1, rows.size)
        assertFalse(rows.single().superseded)
        assertEquals("minecraft:oak_planks", rows.single().plannerPick, "the pick belongs to the moment of the answer")
    }

    @Test
    fun `clearing removes the live answer but keeps the history behind it`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid).process(PlanOverride.TagMember("#minecraft:logs", "minecraft:oak_log"))
            UpsertPlanOverrideStep(pid).process(PlanOverride.TagMember("#minecraft:logs", "minecraft:birch_log"))
            ClearPlanOverrideStep(pid).process("#minecraft:logs")
        }

        val rows = readRows(pid, "#minecraft:logs")
        assertEquals(1, rows.size, "withdrawing today's answer is no reason to forget the earlier one")
        assertTrue(rows.single().superseded)

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(PlanOverrides.NONE, loaded.value)
    }

    // ---- MCO-507: undo removes exactly the rows one action created -----------------------

    @Test
    fun `deleting by id removes exactly those rows and nothing the user answered themselves`() {
        val pid = createProject(createWorld())
        val bulkIds = runBlocking {
            listOf("#minecraft:coals", "#minecraft:soul_fire_base_blocks").map { tag ->
                val r = UpsertPlanOverrideStep(pid)
                    .process(PlanOverride.TagMember(tag, "minecraft:coal", "minecraft:coal"))
                (r as Result.Success).value
            }
        }
        runBlocking {
            UpsertPlanOverrideStep(pid).process(PlanOverride.TagMember("#minecraft:planks", "minecraft:spruce_planks"))
        }

        val removed = runBlocking { DeletePlanOverridesByIdStep(pid).process(bulkIds) }
        assertIs<Result.Success<Int>>(removed)
        assertEquals(2, removed.value)

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(mapOf("#minecraft:planks" to "minecraft:spruce_planks"), loaded.value.tagMember)
    }

    @Test
    fun `deleting by id cannot reach another project's rows`() {
        val mine = createProject(createWorld())
        val theirs = createProject(createWorld())
        val theirRowId = runBlocking {
            val r = UpsertPlanOverrideStep(theirs).process(PlanOverride.TagMember("#minecraft:coals", "minecraft:coal"))
            (r as Result.Success).value
        }

        val removed = runBlocking { DeletePlanOverridesByIdStep(mine).process(listOf(theirRowId)) }
        assertIs<Result.Success<Int>>(removed)
        assertEquals(0, removed.value)

        val loaded = runBlocking { GetPlanOverridesStep.process(theirs) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(mapOf("#minecraft:coals" to "minecraft:coal"), loaded.value.tagMember)
    }

    @Test
    fun `deleting by id with an empty list is a no-op rather than a delete-everything`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid).process(PlanOverride.TagMember("#minecraft:coals", "minecraft:coal"))
        }

        val removed = runBlocking { DeletePlanOverridesByIdStep(pid).process(emptyList()) }
        assertIs<Result.Success<Int>>(removed)
        assertEquals(0, removed.value)

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(mapOf("#minecraft:coals" to "minecraft:coal"), loaded.value.tagMember)
    }

    @Test
    fun `clearing an override removes it`() {
        val pid = createProject(createWorld())
        runBlocking {
            UpsertPlanOverrideStep(pid)
                .process(PlanOverride.Source("minecraft:glass", "minecraft:smelting:glass.json"))
            ClearPlanOverrideStep(pid).process("minecraft:glass")
        }

        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)
        assertEquals(PlanOverrides.NONE, loaded.value)
    }

    @Test
    fun `a saved selection re-derives the identical plan`() {
        val pid = createProject(createWorld())
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
            UpsertPlanOverrideStep(pid).process(
                PlanOverride.Source("minecraft:oak_planks", "minecraft:chest:chests/bonus_chest.json")
            )
        }
        val loaded = runBlocking { GetPlanOverridesStep.process(pid) }
        assertIs<Result.Success<PlanOverrides>>(loaded)

        val rederived = GatheringPlanner.plan(graph, targets, overrides = loaded.value)
        assertEquals(original.nodes, rederived.nodes)
        assertEquals(original.activityList, rederived.activityList)
    }

    /** Every stored row for one question, oldest first - history included. */
    private data class OverrideRow(
        val id: Int,
        val tagMember: String?,
        val plannerPick: String?,
        val superseded: Boolean,
    )

    private fun readRows(projectId: Int, itemId: String): List<OverrideRow> = runBlocking {
        val result = DatabaseSteps.query<Pair<Int, String>, List<OverrideRow>>(
            sql = SafeSQL.select(
                "SELECT id, tag_member, planner_pick, superseded_at FROM resource_gathering_plan_override " +
                    "WHERE project_id = ? AND item_id = ? ORDER BY id"
            ),
            parameterSetter = { stmt, input ->
                stmt.setInt(1, input.first)
                stmt.setString(2, input.second)
            },
            resultMapper = { rs ->
                val rows = mutableListOf<OverrideRow>()
                while (rs.next()) {
                    rows.add(
                        OverrideRow(
                            id = rs.getInt("id"),
                            tagMember = rs.getString("tag_member"),
                            plannerPick = rs.getString("planner_pick"),
                            superseded = rs.getTimestamp("superseded_at") != null,
                        )
                    )
                }
                rows
            }
        ).process(projectId to itemId)
        (result as Result.Success).value
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
}
