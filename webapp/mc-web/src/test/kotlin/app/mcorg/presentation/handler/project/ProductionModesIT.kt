package app.mcorg.presentation.handler.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.commonsteps.GetProjectListItemStep
import app.mcorg.pipeline.project.resources.handleGetProductionsPanel
import app.mcorg.pipeline.project.resources.handleSwitchProductionMode
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
 * run it carries all of them, one active, and only the active one supplies.
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
        insertDemand(unrelatedId, poppy)
    }

    @BeforeEach
    fun reset() {
        setActiveMode(treeFarmId, oakModeId)
        setProjectState(treeFarmId, "DONE")
        setProjectState(bambooFarmId, "DONE")
        listOf(oakConsumerId, cherryConsumerId, unrelatedId).forEach(::stampFingerprint)
    }

    // ---- supply -----------------------------------------------------------------------------

    @Test
    fun `only the active mode supplies the world`() {
        val supplied = suppliedItems()

        assertTrue(oakLog.id in supplied, "Oak Mode is active")
        assertFalse(cherryLog.id in supplied, "Cherry Mode is not, so cherry logs are not on offer")
        assertTrue(bamboo.id in supplied, "a hand-recorded farm with no modes supplies its one list")
    }

    @Test
    fun `the project list counts what the farm makes now, not every mode's rows`() {
        val item = runBlocking { GetProjectListItemStep.process(treeFarmId) }

        // Oak Mode makes two items; the two modes together hold four rows.
        assertEquals(2, (item as Result.Success).value.producesCount)
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

        // Otherwise the tree farm's switch would quietly turn the bamboo farm's row on and off.
        assertIs<Result.Failure<*>>(result)
    }

    // ---- switching --------------------------------------------------------------------------

    @Test
    fun `switching the active mode changes what the farm supplies, with nothing re-typed`() = testApplication {
        setupRoutes()

        val response = switchTo(cherryModeId)

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val supplied = suppliedItems()
        assertTrue(cherryLog.id in supplied)
        assertFalse(oakLog.id in supplied)
        assertEquals(71_700, rateOf(cherryModeId, cherryLog), "the mode's own rate, as the design recorded it")
    }

    @Test
    fun `a switch invalidates the plans that gathered what the old mode made and what the new one makes`() = testApplication {
        setupRoutes()

        switchTo(cherryModeId)

        assertFalse(hasFingerprint(oakConsumerId), "oak logs stopped being supplied")
        assertFalse(hasFingerprint(cherryConsumerId), "cherry logs started being supplied")
        assertTrue(hasFingerprint(unrelatedId), "poppies have nothing to do with the tree farm")
    }

    @Test
    fun `switching a farm that is not running invalidates nothing`() = testApplication {
        setupRoutes()
        setProjectState(treeFarmId, "ACTIVE")

        val response = switchTo(cherryModeId)

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(hasFingerprint(oakConsumerId), "a farm that is not DONE supplies nothing either way")
        assertTrue(hasFingerprint(cherryConsumerId))
    }

    @Test
    fun `a mode belonging to another project is refused`() = testApplication {
        setupRoutes()
        val otherFarm = createProject("Someone Else's Farm")
        val foreignMode = insertMode(otherFarm, "Foreign Mode", position = 0)

        val response = switchTo(foreignMode)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(oakModeId, activeModeOf(treeFarmId), "and the active mode did not move")
    }

    @Test
    fun `a world member who is not an admin can switch`() = testApplication {
        setupRoutes()
        val member = createExtraUser()
        addWorldMember(member.id, worldId, Role.MEMBER, "member-${member.id}")

        val response = client.post("/worlds/$worldId/projects/$treeFarmId/productions/active-mode") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("modeId=$cherryModeId")
        }

        // Inside a project, members do what admins do; admin is kept for the world-level things.
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(cherryModeId, activeModeOf(treeFarmId))
        assertContains(response.bodyAsText(), "mode-ledger")
    }

    @Test
    fun `someone outside the world cannot switch`() = testApplication {
        setupRoutes()
        val stranger = createExtraUser()

        val response = client.post("/worlds/$worldId/projects/$treeFarmId/productions/active-mode") {
            addAuthCookie(this, stranger)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("modeId=$cherryModeId")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(oakModeId, activeModeOf(treeFarmId))
    }

    // ---- editing a farm with modes ----------------------------------------------------------

    @Test
    fun `a rate edit on a farm with modes lands in the active mode`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$treeFarmId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=${stick.id}&ratePerHour=950")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(950, rateOf(oakModeId, stick))
        assertEquals(1_100, rateOf(cherryModeId, stick), "the other mode keeps its own stick rate")
        assertEquals(0, modeLessRowCount(treeFarmId), "a farm with modes never grows a mode-less list beside them")
        setRate(oakModeId, stick, 900)
    }

    @Test
    fun `the panel lists every mode with what it makes, and edits only the running one`() = testApplication {
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$treeFarmId/productions/panel") {
            addAuthCookie(this)
        }.bodyAsText()

        // Frame 1a: the ledger says what each mode makes, so a player can see Cherry is possible
        // before switching; the rate rows below belong to the running mode alone.
        assertContains(body, "Modes · 2")
        assertContains(body, "RUNNING")
        // Cherry's row is the switch: it posts Cherry's id and reads "Cherry … Switch".
        assertContains(body, "{&quot;modeId&quot;:&quot;$cherryModeId&quot;}")
        assertContains(body, "mode-ledger__action\">Switch")
        assertContains(body, "Cherry Log 71,700 · Stick 1,100 /hr")
        assertContains(body, "Makes in Oak Mode")
        assertContains(body, "${oakLog.name} rate per hour")
        assertFalse(body.contains("${cherryLog.name} rate per hour"), "a mode not running has no rate rows to edit")
    }

    @Test
    fun `switching a running farm says what changed for other plans and offers the way back`() = testApplication {
        setupRoutes()

        val body = switchTo(cherryModeId).bodyAsText()

        assertContains(body, "Now running Cherry Mode.")
        assertContains(body, "count Cherry Log and Stick from this farm; Oak Log no longer comes from it.")
        assertContains(body, "Switch back to Oak Mode")
        // The chip re-renders out of band with the running mode as its tag.
        assertContains(body, "hx-swap-oob")
        assertContains(body, "badge--neutral\">Cherry Mode")
    }

    @Test
    fun `switching a farm that is not running changes nobody's plan, so says nothing`() = testApplication {
        setupRoutes()
        setProjectState(treeFarmId, "ACTIVE")

        val body = switchTo(cherryModeId).bodyAsText()

        assertFalse(body.contains("Now running"), "a farm that is not Done supplies nothing either way")
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
                        post("/active-mode") { call.handleSwitchProductionMode() }
                    }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.switchTo(modeId: Int) =
        client.post("/worlds/$worldId/projects/$treeFarmId/productions/active-mode") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("modeId=$modeId")
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

    private fun setActiveMode(projectId: Int, modeId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE project_production_modes SET active = FALSE WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE project_production_modes SET active = TRUE WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, modeId) }
        ).process(Unit)
    }

    private fun activeModeOf(projectId: Int): Int? = runBlocking {
        val result = DatabaseSteps.query<Unit, Int?>(
            sql = SafeSQL.select("SELECT id FROM project_production_modes WHERE project_id = ? AND active"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> if (rs.next()) rs.getInt("id") else null }
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

    private fun stampFingerprint(projectId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand_state (project_id, fingerprint, derived_at) VALUES (?, 'fp-test', now()) " +
                    "ON CONFLICT (project_id) DO UPDATE SET fingerprint = EXCLUDED.fingerprint"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun hasFingerprint(projectId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM project_demand_state WHERE project_id = ? AND fingerprint IS NOT NULL"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next() }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role, displayName: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, worldId)
                stmt.setString(3, displayName)
                stmt.setInt(4, role.level)
            }
        ).process(Unit)
        CacheManager.onMemberAdded(userId, worldId)
        CacheManager.worldMemberRole.asMap().keys
            .filter { it.startsWith("$userId:$worldId:") }
            .forEach { CacheManager.worldMemberRole.invalidate(it) }
    }
}
