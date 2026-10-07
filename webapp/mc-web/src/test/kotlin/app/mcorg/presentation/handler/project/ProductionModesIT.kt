package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep
import app.mcorg.pipeline.project.commonsteps.GetProjectListItemStep
import app.mcorg.pipeline.project.resources.handleGetProductionsPanel
import app.mcorg.pipeline.project.resources.handleUpsertProjectProduction
import app.mcorg.pipeline.resources.GetWorldFarmSuppliesStep
import app.mcorg.pipeline.resources.WorldFarmSuppliesInput
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Runtime production modes on a project (MCO-413): a farm built from a design with several ways to
 * run it carries all of them, and once it is Done every one of them supplies (MCO-588) — Seam does
 * not track which lever is pulled in game.
 *
 * The farm here is the tree farm that motivated the issue, cut down to two modes: replant it with
 * oak or with cherry and nothing about the build changes, only what it makes. A hand-recorded farm
 * with no modes sits beside it to keep the mode-less shape honest.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProductionModesIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 97, 0)
    private val oakLog = Item("minecraft:oak_log", "Oak Log")
    private val cherryLog = Item("minecraft:cherry_log", "Cherry Log")
    private val stick = Item("minecraft:stick", "Stick")
    private val bamboo = Item("minecraft:bamboo", "Bamboo")
    private val poppy = Item("minecraft:poppy", "Poppy")

    private var worldId: Int = 0
    private var treeFarmId: Int = 0
    private var oakModeId: Int = 0
    private var cherryModeId: Int = 0
    private var bambooFarmId: Int = 0
    private var oakConsumerId: Int = 0
    private var cherryConsumerId: Int = 0
    private var stickConsumerId: Int = 0
    private var unrelatedId: Int = 0

    @BeforeAll
    fun setup() {
        val serverData = ServerData(
            version = version,
            items = listOf(oakLog, cherryLog, stick, bamboo, poppy),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/oak_log.json",
                    producedItems = listOf(oakLog to ResourceQuantity.ItemQuantity(1))
                )
            )
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })

        worldId = createWorld("Production Modes IT World")
        treeFarmId = createProject("Tree Farm")
        bambooFarmId = createProject("Bamboo Farm")
        oakConsumerId = createProject("Oak Cabin")
        cherryConsumerId = createProject("Cherry Pavilion")
        stickConsumerId = createProject("Fence Line")
        unrelatedId = createProject("Flower Garden")

        oakModeId = insertMode(treeFarmId, "Oak Mode", position = 0)
        cherryModeId = insertMode(treeFarmId, "Cherry Mode", position = 1)
        insertProduction(treeFarmId, oakModeId, oakLog, 48_000)
        insertProduction(treeFarmId, oakModeId, stick, 900)
        insertProduction(treeFarmId, cherryModeId, cherryLog, 71_700)
        insertProduction(treeFarmId, cherryModeId, stick, 1_100)
        insertProduction(bambooFarmId, null, bamboo, 12_000)

        insertDemand(oakConsumerId, oakLog)
        insertDemand(cherryConsumerId, cherryLog)
        insertDemand(stickConsumerId, stick)
        insertDemand(unrelatedId, poppy)
    }

    @BeforeEach
    fun reset() {
        setProjectState(treeFarmId, "DONE")
        setProjectState(bambooFarmId, "DONE")
    }

    // ---- supply -----------------------------------------------------------------------------

    @Test
    fun `every mode of a Done farm supplies the world`() {
        val supplied = suppliedItems()

        // Oak and Cherry at once: a plan wanting oak and one wanting cherry are both fed, as they
        // are in game by running one mode for a while and then the other.
        assertTrue(oakLog.id in supplied)
        assertTrue(cherryLog.id in supplied)
        assertTrue(stick.id in supplied)
        assertTrue(bamboo.id in supplied, "a hand-recorded farm with no modes supplies its one list")
    }

    @Test
    fun `a farm that is not Done supplies nothing from any mode`() {
        setProjectState(treeFarmId, "ACTIVE")

        val supplied = suppliedItems()

        assertFalse(oakLog.id in supplied)
        assertFalse(cherryLog.id in supplied)
    }

    @Test
    fun `the project list counts each item the farm makes once, however many modes make it`() {
        val item = runBlocking { GetProjectListItemStep.process(treeFarmId) }

        // Oak Log, Cherry Log and Stick; both modes make sticks, so the two modes hold four rows.
        assertEquals(3, (item as Result.Success).value.producesCount)
    }

    @Test
    fun `an item two modes make is one roadmap edge, not one per mode`() {
        val edges = runBlocking { GetFarmSupplyEdgesStep(worldId).process(Unit) }

        val stickEdges = (edges as Result.Success).value
            .filter { it.consumerId == stickConsumerId && it.producerId == treeFarmId }
        assertEquals(1, stickEdges.size, stickEdges.toString())
    }

    @Test
    fun `a production row cannot point at another project's mode`() {
        val result = runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO project_productions (project_id, mode_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?, ?)"
                ),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, bambooFarmId)
                    stmt.setInt(2, oakModeId)
                    stmt.setString(3, poppy.id)
                    stmt.setString(4, poppy.name)
                    stmt.setInt(5, 1)
                }
            ).process(Unit)
        }

        // Otherwise an edit to the tree farm's Oak Mode could reach the bamboo farm's supply.
        assertIs<Result.Failure<*>>(result)
    }

    // ---- editing a farm with modes ----------------------------------------------------------

    @Test
    fun `a rate edit names its mode and lands in that mode alone`() = testApplication {
        setupRoutes()

        val response = postProduction(treeFarmId, "itemId=${stick.id}&ratePerHour=950&modeId=$cherryModeId")

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(950, rateOf(cherryModeId, stick))
        assertEquals(900, rateOf(oakModeId, stick), "the other mode keeps its own stick rate")
        assertEquals(0, modeLessRowCount(treeFarmId), "a farm with modes never grows a mode-less list beside them")
        setRate(cherryModeId, stick, 1_100)
    }

    @Test
    fun `a write to a farm with modes that names no mode is refused`() = testApplication {
        setupRoutes()

        val response = postProduction(treeFarmId, "itemId=${poppy.id}&ratePerHour=10")

        // There is no running mode to fall back on, and a mode-less row beside the modes would
        // break the one rule no constraint can check.
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(0, modeLessRowCount(treeFarmId))
    }

    @Test
    fun `a mode belonging to another project is refused`() = testApplication {
        setupRoutes()
        val otherFarm = createProject("Someone Else's Farm")
        val foreignMode = insertMode(otherFarm, "Foreign Mode", position = 0)

        val response = postProduction(treeFarmId, "itemId=${poppy.id}&ratePerHour=10&modeId=$foreignMode")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(null, rateOf(foreignMode, poppy))
    }

    @Test
    fun `a farm without modes refuses a write that names one`() = testApplication {
        setupRoutes()

        val response = postProduction(bambooFarmId, "itemId=${poppy.id}&ratePerHour=10&modeId=$oakModeId")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(null, rateOf(oakModeId, poppy))
    }

    @Test
    fun `the panel lists every mode with its own editable rates, and none as running`() = testApplication {
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$treeFarmId/productions/panel") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "Modes · 2")
        assertContains(body, "${oakLog.name} rate per hour in Oak Mode")
        assertContains(body, "${cherryLog.name} rate per hour in Cherry Mode")
        // Each rate edit carries its own mode, so it cannot land in the other.
        assertContains(body, "modeId:&quot;$cherryModeId&quot;")
        // The add form asks which mode an item belongs to.
        assertContains(body, "id=\"production-panel-mode\"")
        assertFalse(body.contains("RUNNING"), "no mode is marked as the running one")
        assertFalse(body.contains("active-mode"), "and there is nothing to switch")
    }

    @Test
    fun `after an add the picker stays on the mode just added to`() = testApplication {
        setupRoutes()

        val body = postProduction(treeFarmId, "itemId=${poppy.id}&ratePerHour=10&modeId=$cherryModeId").bodyAsText()

        // The panel re-renders after every write. A picker back on the first mode would send the
        // next item into Oak Mode without a word.
        assertContains(body, "<option value=\"$cherryModeId\" selected")
        assertFalse(body.contains("<option value=\"$oakModeId\" selected"))
        deleteProduction(cherryModeId, poppy)
    }

    @Test
    fun `the chip names an item two modes make once, and says how many modes the farm has`() = testApplication {
        setupRoutes()

        val body = postProduction(treeFarmId, "itemId=${stick.id}&ratePerHour=900&modeId=$oakModeId").bodyAsText()
        val chip = body.substringAfter("hx-swap-oob")

        // Cherry Log, Oak Log, Stick: three items, not four rows.
        assertContains(chip, "+2 more")
        assertContains(chip, "2 modes")
    }

    // ---- routing — mirrors WorldHandler ---------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/productions") {
                        get("/panel") { call.handleGetProductionsPanel() }
                        post { call.handleUpsertProjectProduction() }
                    }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.postProduction(projectId: Int, body: String) =
        client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

    // ---- fixtures -------------------------------------------------------------------------

    private fun suppliedItems(): Set<String> = runBlocking {
        val result = GetWorldFarmSuppliesStep.process(WorldFarmSuppliesInput(worldId, excludeProjectId = unrelatedId))
        (result as Result.Success).value.map { it.itemId }.toSet()
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun insertMode(projectId: Int, name: String, position: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_production_modes (project_id, name, position) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, name)
                stmt.setInt(3, position)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun insertProduction(projectId: Int, modeId: Int?, item: Item, rate: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, mode_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                if (modeId == null) stmt.setNull(2, java.sql.Types.INTEGER) else stmt.setInt(2, modeId)
                stmt.setString(3, item.id)
                stmt.setString(4, item.name)
                stmt.setInt(5, rate)
            }
        ).process(Unit)
    }

    private fun rateOf(modeId: Int, item: Item): Int? = runBlocking {
        val result = DatabaseSteps.query<Unit, Int?>(
            sql = SafeSQL.select("SELECT rate_per_hour FROM project_productions WHERE mode_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, modeId)
                stmt.setString(2, item.id)
            },
            resultMapper = { rs -> if (rs.next()) rs.getInt("rate_per_hour") else null }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun deleteProduction(modeId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete("DELETE FROM project_productions WHERE mode_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, modeId)
                stmt.setString(2, item.id)
            }
        ).process(Unit)
    }

    private fun setRate(modeId: Int, item: Item, rate: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE project_productions SET rate_per_hour = ? WHERE mode_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, rate)
                stmt.setInt(2, modeId)
                stmt.setString(3, item.id)
            }
        ).process(Unit)
    }

    private fun modeLessRowCount(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT count(*) AS n FROM project_productions WHERE project_id = ? AND mode_id IS NULL"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getInt("n") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun setProjectState(projectId: Int, state: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun insertDemand(projectId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand (project_id, item_id, item_name, quantity, activity_group, node_status) " +
                    "VALUES (?, ?, ?, 64, 'GATHER', 'RESOLVED')"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
            }
        ).process(Unit)
    }
}
