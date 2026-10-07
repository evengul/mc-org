package app.mcorg.presentation.handler.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.resources.commonsteps.SetProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.SetProgressByItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
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

/**
 * A project's own plan inputs drop its stored demand however they are written (MCO-578).
 *
 * MCO-404 left these alone on the grounds that they change on the project page, which re-derives
 * on the spot. Sixteen files write them, and several never re-derive — the mod's sync, Field Log
 * edits, adopting measurements, adding from a schematic, and the `ON DELETE SET NULL` that unlinks
 * a requirement when the project solving it is deleted. Since MCO-572 the roadmap's hand-list
 * numbers are read straight from these rows, so the gap was visible.
 *
 * The rule is a trigger on the three tables, so these tests write the tables the way any door
 * would and check `project_demand_state`, which is what the roadmap's fill-on-read path keys on.
 * One test goes through a real step and the roadmap end to end.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectInputInvalidationIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 92, 1)
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val furnace = Item("minecraft:furnace", "Furnace")

    private var worldId = 0

    @BeforeAll
    fun setup() {
        val serverData = ServerData(
            version = version,
            items = listOf(cobblestone, furnace),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/cobblestone.json",
                    producedItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(1)),
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
        CacheManager.invalidateAll()
        worldId = runBlocking {
            (CreateWorldStep(user).process(CreateWorldInput("Project Input IT World", "test", version)) as Result.Success).value
        }
    }

    @Test
    fun `progress from the mod's sync shows on the next roadmap load`() {
        val projectId = createProject("Smelter")
        gather(projectId, furnace, 10)
        loadRoadmap()
        assertEquals(80, demandQuantity(projectId, cobblestone))

        // The mod's door: no plan is derived after it.
        runBlocking { SetProgressByItemStep.process(SetProgressByItemInput(projectId, furnace.id, 4)) }
        assertFalse(hasState(projectId), "collected counts are a plan input")

        loadRoadmap()
        assertEquals(48, demandQuantity(projectId, cobblestone), "six furnaces left, eight stone each")
    }

    @Test
    fun `a project whose last item is collected loses its demand, once`() {
        // Nothing left to plan used to end the derivation before anything was written, so the
        // roadmap kept drawing the old demand and retried the project on every load.
        val projectId = createProject("Finished Smelter")
        gather(projectId, furnace, 10)
        loadRoadmap()
        assertEquals(80, demandQuantity(projectId, cobblestone))

        runBlocking { SetProgressByItemStep.process(SetProgressByItemInput(projectId, furnace.id, 10)) }
        loadRoadmap()

        assertEquals(-1, demandQuantity(projectId, cobblestone), "nothing is left to gather")
        assertTrue(hasState(projectId), "and that counts as derived, so the next load does nothing")
    }

    @Test
    fun `a project whose every target is ignored loses its demand`() {
        val projectId = createProject("Shelved")
        val rowId = gather(projectId, furnace, 10)
        loadRoadmap()
        assertEquals(80, demandQuantity(projectId, cobblestone))

        sql("UPDATE resource_gathering SET ignored = TRUE WHERE id = ?", rowId)
        loadRoadmap()

        assertEquals(-1, demandQuantity(projectId, cobblestone))
    }

    @Test
    fun `adding, changing and removing a requirement each drop the project's stored demand`() {
        val projectId = createProject("Workshop")
        val other = createProject("Neighbour")
        stampState(other)

        stampState(projectId)
        val rowId = gather(projectId, furnace, 3)
        assertFalse(hasState(projectId), "an added target")

        stampState(projectId)
        sql("UPDATE resource_gathering SET required = 5 WHERE id = ?", rowId)
        assertFalse(hasState(projectId), "a changed amount")

        stampState(projectId)
        sql("UPDATE resource_gathering SET ignored = TRUE WHERE id = ?", rowId)
        assertFalse(hasState(projectId), "an ignored row leaves the derivation")

        stampState(projectId)
        sql("DELETE FROM resource_gathering WHERE id = ?", rowId)
        assertFalse(hasState(projectId), "a removed target")

        assertTrue(hasState(other), "nothing about the neighbour's plan changed")
    }

    @Test
    fun `an update that changes nothing leaves stored demand alone`() {
        val projectId = createProject("Idle")
        val rowId = gather(projectId, furnace, 3)
        stampState(projectId)

        sql("UPDATE resource_gathering SET required = required WHERE id = ?", rowId)

        assertTrue(hasState(projectId))
    }

    @Test
    fun `a plan override drops the project's stored demand`() {
        val projectId = createProject("Pinned")
        stampState(projectId)

        sql(
            "INSERT INTO resource_gathering_plan_override (project_id, item_id, tag_member) " +
                "VALUES (?, '#minecraft:planks', 'minecraft:oak_planks')",
            projectId,
        )
        assertFalse(hasState(projectId))

        stampState(projectId)
        sql("DELETE FROM resource_gathering_plan_override WHERE project_id = ?", projectId)
        assertFalse(hasState(projectId))
    }

    @Test
    fun `deleting the project a requirement was linked to drops the consumer's stored demand`() {
        // The link made furnaces the other project's job; with it gone they are this one's again.
        // ON DELETE SET NULL rewrites the row without any application code running, and the
        // linked project produces nothing, so MCO-404's supply invalidation cannot see it either.
        val consumer = createProject("Consumer")
        val solver = createProject("Furnace Line")
        val rowId = gather(consumer, furnace, 3)
        link(rowId, solver)
        stampState(consumer)

        sql("DELETE FROM projects WHERE id = ?", solver)

        assertFalse(hasState(consumer))
    }

    @Test
    fun `deriving a plan does not invalidate the plan it just stored`() {
        val projectId = createProject("Settled")
        gather(projectId, furnace, 2)

        loadRoadmap()

        assertTrue(hasState(projectId), "the write-through touches none of the trigger's tables")
    }

    // ---- fixtures -----------------------------------------------------------------------

    private fun loadRoadmap() {
        assertIs<Result.Success<*>>(runBlocking { GetWorldRoadMapStep(worldId).process(Unit) })
    }

    private fun createProject(name: String): Int = runBlocking {
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

    private fun gather(projectId: Int, item: Item, required: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            },
        ).process(Unit)
        (result as Result.Success).value
    }

    /** One write with one integer parameter, standing in for whichever door does it. */
    private fun sql(statement: String, id: Int) = runBlocking {
        assertIs<Result.Success<*>>(
            DatabaseSteps.update<Unit>(
                sql = when (statement.substringBefore(' ')) {
                    "INSERT" -> SafeSQL.insert(statement)
                    "DELETE" -> SafeSQL.delete(statement)
                    else -> SafeSQL.update(statement)
                },
                parameterSetter = { stmt, _ -> stmt.setInt(1, id) },
            ).process(Unit)
        )
    }

    private fun link(rowId: Int, solverId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE resource_gathering SET source_type = 'project', solved_by_project_id = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, solverId)
                stmt.setInt(2, rowId)
            },
        ).process(Unit)
    }

    private fun stampState(projectId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand_state (project_id, fingerprint, derived_at) VALUES (?, 'fp-test', now()) " +
                    "ON CONFLICT (project_id) DO UPDATE SET fingerprint = EXCLUDED.fingerprint"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun hasState(projectId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next() }
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
            resultMapper = { rs -> if (rs.next()) rs.getLong("quantity") else -1 }
        ).process(Unit)
        (result as Result.Success).value
    }
}
