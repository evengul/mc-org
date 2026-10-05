package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.plan.GatheringPlan
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * MCO-572 — a plan derived as if some unfinished farms were producing, and its cache.
 *
 * Runs the real planner over a small stored graph: ten furnaces need 80 cobblestone, plus 20 sand
 * and 16 oak logs gathered as they are. A cobblestone farm that is not built yet is the farm the
 * scenarios assume.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ScenarioDemandTest : WithUser() {

    private val version = MinecraftVersion.Release(1, 97, 0)

    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val furnace = Item("minecraft:furnace", "Furnace")
    private val sand = Item("minecraft:sand", "Sand")
    private val log = Item("minecraft:oak_log", "Oak Log")

    private var worldId = 0

    @BeforeAll
    fun setup() {
        CacheManager.invalidateAll()
        val serverData = ServerData(
            version = version,
            items = listOf(cobblestone, furnace, sand, log),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/cobblestone.json",
                    producedItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(1)),
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/sand.json",
                    producedItems = listOf(sand to ResourceQuantity.ItemQuantity(1)),
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/oak_log.json",
                    producedItems = listOf(log to ResourceQuantity.ItemQuantity(1)),
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "furnace.json",
                    requiredItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(furnace to ResourceQuantity.ItemQuantity(1)),
                ),
            ),
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })
        worldId = createWorld()
    }

    /** A storage build, and an unbuilt cobblestone farm — the default state is PENDING. */
    private fun storageAndFarm(): Pair<Int, Int> {
        val storage = createProject("Storage")
        gather(storage, furnace, 10)
        gather(storage, sand, 20)
        gather(storage, log, 16)
        val farm = createProject("Cobble farm")
        produce(farm, cobblestone)
        return storage to farm
    }

    private fun plan(projectId: Int, assume: Set<Int> = emptySet()): GatheringPlan {
        val result = runBlocking { GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId, assume)) }
        assertIs<Result.Success<GatheringPlan>>(result)
        return result.value
    }

    private fun GatheringPlan.byHand() = activityList.filter { it.status == PlanNodeStatus.RAW_GATHER }.sumOf { it.quantity }

    @Test
    fun `a plan as if a farm were built takes its output off the hand list, and never touches project_demand`() {
        val (storage, farm) = storageAndFarm()

        val now = plan(storage)
        assertEquals(116, now.byHand(), "80 cobblestone + 20 sand + 16 oak logs")
        val stored = fingerprintOf(storage)
        assertNotNull(stored, "the real plan writes its demand")

        val asIfBuilt = plan(storage, setOf(farm))
        assertEquals(PlanNodeStatus.SUPPLIED, asIfBuilt.nodes[cobblestone.id]?.status)
        assertEquals(36, asIfBuilt.byHand())

        assertEquals(stored, fingerprintOf(storage), "a hypothetical plan must not overwrite the real one")
        assertEquals("RAW_GATHER", demandStatus(storage, cobblestone.id), "the farm is still not built")
    }

    @Test
    fun `a scenario is derived once, and again only when the real demand changes`() {
        val (storage, farm) = storageAndFarm()
        plan(storage) // the real demand every scenario is anchored to
        val productions = mapOf(farm to setOf(cobblestone.id))
        fun totals() = runBlocking {
            ScenarioDemand.handTotals(worldId, mapOf(storage to listOf(setOf(farm))), productions)
        }

        assertEquals(36L, totals()[storage]?.get(setOf(farm)))
        val first = derivedAt(storage)

        assertEquals(36L, totals()[storage]?.get(setOf(farm)))
        assertEquals(first, derivedAt(storage), "a fresh scenario is read, not re-derived")

        // What a supply change or a project-page re-derivation does to the real demand.
        setFingerprint(storage, "something else")
        assertEquals(36L, totals()[storage]?.get(setOf(farm)))
        assertNotEquals(first, derivedAt(storage), "stale with the real demand, re-derived with it")
    }

    @Test
    fun `a project with no real demand yet has no scenario, rather than a zero`() {
        val (storage, farm) = storageAndFarm()

        val totals = runBlocking {
            ScenarioDemand.handTotals(worldId, mapOf(storage to listOf(setOf(farm))), mapOf(farm to setOf(cobblestone.id)))
        }

        assertEquals(null, totals[storage]?.get(setOf(farm)))
    }

    @Test
    fun `scenarios nobody asks for any more are deleted`() {
        val (storage, farm) = storageAndFarm()
        val sandFarm = createProject("Sand farm").also { produce(it, sand) }
        plan(storage)
        val productions = mapOf(farm to setOf(cobblestone.id), sandFarm to setOf(sand.id))

        runBlocking { ScenarioDemand.handTotals(worldId, mapOf(storage to listOf(setOf(farm))), productions) }
        val totals = runBlocking {
            ScenarioDemand.handTotals(worldId, mapOf(storage to listOf(setOf(sandFarm))), productions)
        }

        assertEquals(96L, totals[storage]?.get(setOf(sandFarm)), "116 less the 20 sand")
        assertEquals(1, scenarioCount(storage), "the cobblestone scenario is gone")
    }

    @Test
    fun `what an assumed farm makes is part of the scenario's identity`() {
        val a = ScenarioDemand.scenarioKeyOf(setOf(1, 2), mapOf(1 to setOf("minecraft:cobblestone")))
        val b = ScenarioDemand.scenarioKeyOf(setOf(2, 1), mapOf(1 to setOf("minecraft:cobblestone")))
        val c = ScenarioDemand.scenarioKeyOf(setOf(1, 2), mapOf(1 to setOf("minecraft:cobblestone", "minecraft:stone")))

        assertEquals(a, b, "order of the set does not matter")
        assertNotEquals(a, c, "a farm that makes something new is a different question")
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = "Scenario Demand World", description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
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

    private fun gather(projectId: Int, item: Item, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            }
        ).process(Unit)
    }

    private fun produce(projectId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 0)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
            }
        ).process(Unit)
    }

    private fun fingerprintOf(projectId: Int): String? = runBlocking {
        GetStoredDemandFingerprintStep(projectId).process(Unit).getOrNull()?.value
    }

    private fun setFingerprint(projectId: Int, value: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE project_demand_state SET fingerprint = ? WHERE project_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, value)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun demandStatus(projectId: Int, itemId: String): String? = runBlocking {
        DatabaseSteps.query<Unit, String?>(
            SafeSQL.select("SELECT node_status FROM project_demand WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
            },
            resultMapper = { if (it.next()) it.getString(1) else null }
        ).process(Unit).getOrNull()
    }

    private fun derivedAt(projectId: Int): String? = runBlocking {
        DatabaseSteps.query<Unit, String?>(
            SafeSQL.select("SELECT max(derived_at)::text FROM project_demand_scenario_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { if (it.next()) it.getString(1) else null }
        ).process(Unit).getOrNull()
    }

    private fun scenarioCount(projectId: Int): Int = runBlocking {
        DatabaseSteps.query<Unit, Int>(
            SafeSQL.select("SELECT count(*) FROM project_demand_scenario_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { if (it.next()) it.getInt(1) else 0 }
        ).process(Unit).getOrNull() ?: 0
    }
}
