package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.settings.general.UpdatePreferredWoodSpeciesStep
import app.mcorg.pipeline.world.settings.general.UpdateWorldVersionStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A derivation in flight does not overwrite an invalidation that landed while it ran (MCO-584).
 *
 * A derivation reads its inputs, plans for ~0.7 s, then stores the plan with the fingerprint of
 * what it read. Invalidations used to delete the state row; one landing in that window was undone
 * by the store, and the roadmap then served the old inputs' plan as current.
 *
 * Each test splits a derivation at [GenerateGatheringPlanStep.derive] / [GenerateGatheringPlanStep.store]
 * and lands the invalidation in between, which is the window without the timing. The assertion is
 * the roadmap's own question, [GetWorldDemandCoverageStep]: is the project waiting on a derivation.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DemandDerivationRaceIT : WithUser() {

    /** Furnaces are made of cobblestone. */
    private val before = MinecraftVersion.Release(1, 91, 0)

    /** Furnaces are made of blackstone. */
    private val after = MinecraftVersion.Release(1, 91, 1)

    private val furnace = Item("minecraft:furnace", "Furnace")
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val blackstone = Item("minecraft:blackstone", "Blackstone")

    @BeforeAll
    fun setup() {
        store(before, furnaceFrom = cobblestone)
        store(after, furnaceFrom = blackstone)
        CacheManager.invalidateAll()
    }

    // ---- the window, closed ----------------------------------------------------------------

    @Test
    fun `a derivation nobody interrupts is stored as current`() {
        val (worldId, projectId) = smelter("Uninterrupted World")

        storeDerivation(derive(worldId, projectId))

        assertFalse(projectId in uncovered(worldId))
        assertNotNull(fingerprint(projectId))
        assertTrue(cobblestone.id in demandItems(projectId))
    }

    @Test
    fun `an own-input change during a first derivation leaves the project uncovered`() {
        val (worldId, projectId) = smelter("Own Input First World")
        val derivation = derive(worldId, projectId)

        setRequired(projectId, 4)
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId), "the plan of required = 1 must not be stored as current")
        assertTrue(demandItems(projectId).isEmpty(), "nothing of the overtaken plan is written")

        // And the next derivation, from the new inputs, is stored as usual.
        storeDerivation(derive(worldId, projectId))
        assertFalse(projectId in uncovered(worldId))
        assertEquals(32L, demandQuantity(projectId, cobblestone))
    }

    @Test
    fun `an own-input change during a re-derivation of a current plan leaves the project uncovered`() {
        val (worldId, projectId) = smelter("Own Input Current World")
        storeDerivation(derive(worldId, projectId))
        assertFalse(projectId in uncovered(worldId))
        // The project page derives on every visit, current plan or not.
        val derivation = derive(worldId, projectId)

        setRequired(projectId, 4)
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId))
        assertEquals(8L, demandQuantity(projectId, cobblestone), "the previous rows stay readable until re-derived")
    }

    @Test
    fun `a version switch during a fill-loop derivation leaves the project uncovered`() {
        val (worldId, projectId) = smelter("Version Race World")
        val derivation = derive(worldId, projectId)

        assertIs<Result.Success<*>>(runBlocking { UpdateWorldVersionStep(worldId).process(after) })
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId), "a 1.91.0 plan must not be stored as current for a 1.91.1 world")

        storeDerivation(derive(worldId, projectId))
        assertTrue(blackstone.id in demandItems(projectId))
    }

    @Test
    fun `a wood species change during a fill-loop derivation leaves the project uncovered`() {
        val (worldId, projectId) = smelter("Wood Race World")
        storeDerivation(derive(worldId, projectId))
        val derivation = derive(worldId, projectId)

        assertIs<Result.Success<*>>(runBlocking { UpdatePreferredWoodSpeciesStep(worldId).process("spruce") })
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId))
    }

    @Test
    fun `a farm reaching DONE during a first derivation leaves the project uncovered`() {
        val (worldId, projectId) = smelter("Supply Race World")
        val farmId = createProject(worldId, "Cobble Generator")
        produce(farmId, cobblestone)
        // The fill loop's first derivation of the smelter: it has no demand rows yet, so the
        // farm's item join cannot find it.
        val derivation = derive(worldId, projectId)

        setState(farmId, "DONE")
        assertIs<Result.Success<*>>(runBlocking { InvalidateDemandSuppliedByStep(worldId, farmId).process(Unit) })
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId), "a plan that mines cobblestone next to a running generator")
        assertFalse(farmId in uncovered(worldId), "the farm has nothing to gather and is not waiting on anything")
    }

    // ---- across transactions ----------------------------------------------------------------

    /**
     * The case a timestamp would miss: the input change is made — and stamped — before the
     * derivation reads, but committed after. The derivation cannot see the uncommitted change, so
     * its plan is of the old inputs, and a stamp older than its read would have let it through.
     */
    @Test
    fun `an invalidation uncommitted while the derivation reads is not overtaken`() {
        val (worldId, projectId) = smelter("Uncommitted Read World")

        val derivation = Database.getConnection().use { conn ->
            conn.autoCommit = false
            setRequired(conn, projectId, 4)
            val derived = derive(worldId, projectId)
            conn.commit()
            derived
        }
        storeDerivation(derivation)

        assertTrue(projectId in uncovered(worldId))
    }

    /**
     * The save waits for an invalidation that holds the state row rather than writing past it.
     * The trigger's stub insert is uncommitted when the save runs; the save's insert must block on
     * it, then see the bumped generation and write nothing.
     */
    @Test
    fun `a save waits for an invalidation still in flight and then writes nothing`() = runBlocking {
        val (worldId, projectId) = smelter("In Flight Save World")
        val derivation = derive(worldId, projectId)

        Database.getConnection().use { conn ->
            conn.autoCommit = false
            setRequired(conn, projectId, 4)

            val save = async(Dispatchers.IO) { storeDerivation(derivation) }
            withTimeout(10_000) {
                while (!blockedOnLock(conn)) delay(20)
            }
            assertFalse(save.isCompleted, "the save is waiting on the invalidation's row")

            conn.commit()
            save.await()
        }

        assertTrue(projectId in uncovered(worldId))
        assertTrue(demandItems(projectId).isEmpty())
    }

    /**
     * The trigger fires on rows a project delete cascades to, after the project is gone. Its
     * insert must find no project rather than fail the foreign key — and with it the delete.
     */
    @Test
    fun `deleting a project with gathering rows still works`() {
        val (_, projectId) = smelter("Delete World")
        runBlocking {
            assertIs<Result.Success<*>>(
                DatabaseSteps.update<Unit>(
                    sql = SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
                    parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
                ).process(Unit)
            )
        }
        assertNull(fingerprint(projectId))
    }

    // ---- the seam ---------------------------------------------------------------------------

    private fun derive(worldId: Int, projectId: Int): Derivation = runBlocking {
        val result = GenerateGatheringPlanStep.derive(GatheringPlanInput(projectId = projectId, worldId = worldId))
        val derivation = assertIs<Result.Success<Derivation>>(result, "derivation: $result").value
        assertNotNull(derivation.write, "a real derivation carries a write")
        derivation
    }

    private fun storeDerivation(derivation: Derivation) = runBlocking {
        GenerateGatheringPlanStep.store(derivation.write!!)
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private fun store(version: MinecraftVersion.Release, furnaceFrom: Item) {
        val serverData = ServerData(
            version = version,
            items = listOf(furnace, cobblestone, blackstone),
            sources = listOf(
                block("blocks/cobblestone.json", cobblestone),
                block("blocks/blackstone.json", blackstone),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "furnace.json",
                    requiredItems = listOf(furnaceFrom to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(furnace to ResourceQuantity.ItemQuantity(1)),
                ),
            ),
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })
    }

    private fun block(file: String, item: Item) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.BLOCK,
        filename = file,
        producedItems = listOf(item to ResourceQuantity.ItemQuantity(1)),
    )

    /** A world on [before] with one project that needs one furnace. */
    private fun smelter(worldName: String): Pair<Int, Int> {
        val worldId = runBlocking {
            val result = CreateWorldStep(user).process(
                CreateWorldInput(name = worldName, description = "test", version = before)
            )
            (result as Result.Success).value
        }
        val projectId = createProject(worldId, "Smelter")
        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, 1)"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, projectId)
                    stmt.setString(2, furnace.id)
                    stmt.setString(3, furnace.name)
                },
            ).process(Unit)
        }
        return worldId to projectId
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun setRequired(projectId: Int, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE resource_gathering SET required = ? WHERE project_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, required)
                stmt.setInt(2, projectId)
            },
        ).process(Unit)
    }

    /** The same change on a caller's open transaction, so the test decides when it commits. */
    private fun setRequired(conn: Connection, projectId: Int, required: Int) {
        conn.prepareStatement("UPDATE resource_gathering SET required = ? WHERE project_id = ?").use { stmt ->
            stmt.setInt(1, required)
            stmt.setInt(2, projectId)
            stmt.executeUpdate()
        }
    }

    /** Whether any backend is waiting for a lock held by [conn]'s transaction. */
    private fun blockedOnLock(conn: Connection): Boolean = runBlocking {
        val holder = conn.prepareStatement("SELECT pg_backend_pid()").use { stmt ->
            stmt.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))) AS blocked"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, holder) },
            resultMapper = { rs -> rs.next(); rs.getBoolean("blocked") },
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun produce(projectId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 600)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
            }
        ).process(Unit)
    }

    private fun setState(projectId: Int, state: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun fingerprint(projectId: Int): DemandFingerprint? = runBlocking {
        (GetStoredDemandFingerprintStep(projectId).process(Unit) as Result.Success).value
    }

    private fun demandItems(projectId: Int): Set<String> = runBlocking {
        val result = DatabaseSteps.query<Unit, Set<String>>(
            sql = SafeSQL.select("SELECT item_id FROM project_demand WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> buildSet { while (rs.next()) add(rs.getString("item_id")) } }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun demandQuantity(projectId: Int, item: Item): Long = runBlocking {
        val result = DatabaseSteps.query<Unit, Long>(
            sql = SafeSQL.select("SELECT quantity FROM project_demand WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
            },
            resultMapper = { rs -> if (rs.next()) rs.getLong("quantity") else 0L }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun uncovered(worldId: Int): List<Int> = runBlocking {
        (GetWorldDemandCoverageStep(worldId).process(Unit) as Result.Success).value
    }
}
