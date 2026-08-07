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
import app.mcorg.pipeline.project.resources.handleDeleteProjectProduction
import app.mcorg.pipeline.project.resources.handleGetProductionsPanel
import app.mcorg.pipeline.project.resources.handleUpsertProjectProduction
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ProjectProductionItemParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Endpoint tests for the production editor (MCO-297):
 * - GET /productions/panel renders the editor
 * - POST upserts (add, then same item again updates the rate — no duplicate row)
 * - Validation: unknown item 422, missing item 400, blank rate defaults to 0
 * - DELETE removes; deleting another project's production id is 404 (param plugin)
 * - Unauthenticated requests redirect
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectProductionIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 99, 0)
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val poppy = Item("minecraft:poppy", "Poppy")

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var siblingProjectId: Int = 0

    @BeforeAll
    fun setup() {
        val serverData = ServerData(
            version = version,
            items = listOf(ironIngot, poppy),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/iron_ore.json",
                    producedItems = listOf(ironIngot to ResourceQuantity.ItemQuantity(1))
                )
            )
        )
        val stored = runBlocking { StoreMinecraftDataStep.process(serverData) }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld("ProjectProduction IT World")
        projectId = createProject(worldId, "Iron Farm")
        siblingProjectId = createProject(worldId, "Sibling Project")
    }

    // -------------------------------------------------------------------------
    // GET /productions/panel
    // -------------------------------------------------------------------------

    @Test
    fun `GET panel returns editor HTML for world admin`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/productions/panel"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Produces")
        assertContains(body, "Add produced item")
    }

    @Test
    fun `GET panel unauthenticated returns redirect`() = testApplication {
        setupRoutes()
        val unauth = createClient { followRedirects = false }
        val response = unauth.get("/worlds/$worldId/projects/$projectId/productions/panel")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // POST /productions (upsert)
    // -------------------------------------------------------------------------

    @Test
    fun `POST adds a production and re-posting the same item updates the rate without duplicating`() = testApplication {
        setupRoutes()

        val first = client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:iron_ingot&ratePerHour=400")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertContains(first.bodyAsText(), "Iron Ingot")
        assertEquals(listOf("minecraft:iron_ingot" to 400), readProductions(projectId))

        val second = client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:iron_ingot&ratePerHour=800")
        }
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(listOf("minecraft:iron_ingot" to 800), readProductions(projectId), "upsert must not duplicate")

        cleanupProductions(projectId)
    }

    @Test
    fun `POST with blank rate defaults to 0`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:poppy&ratePerHour=")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("minecraft:poppy" to 0), readProductions(projectId))

        cleanupProductions(projectId)
    }

    @Test
    fun `POST with unknown item returns 422`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:not_a_real_item&ratePerHour=10")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(emptyList(), readProductions(projectId))
    }

    @Test
    fun `POST with missing item returns 400`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$projectId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("ratePerHour=10")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // DELETE /productions/{productionId}
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE removes the production`() = testApplication {
        setupRoutes()
        val productionId = insertProduction(projectId, ironIngot.id, ironIngot.name, 400)

        val response = client.delete(
            "/worlds/$worldId/projects/$projectId/productions/$productionId"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "No produced items declared")
        assertEquals(emptyList(), readProductions(projectId))
    }

    @Test
    fun `DELETE with another project's production id returns 404`() = testApplication {
        setupRoutes()
        val foreignProductionId = insertProduction(siblingProjectId, poppy.id, poppy.name, 10)

        val response = client.delete(
            "/worlds/$worldId/projects/$projectId/productions/$foreignProductionId"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(listOf(poppy.id to 10), readProductions(siblingProjectId), "foreign row must survive")

        cleanupProductions(siblingProjectId)
    }

    // -------------------------------------------------------------------------
    // Routing — mirrors WorldHandler's /productions block
    // -------------------------------------------------------------------------

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
                        route("/{productionId}") {
                            install(ProjectProductionItemParamPlugin)
                            delete { call.handleDeleteProjectProduction() }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture + read helpers
    // -------------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
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

    private fun insertProduction(projectId: Int, itemId: String, name: String, rate: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setInt(4, rate)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun readProductions(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, rate_per_hour FROM project_productions WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("rate_per_hour"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun cleanupProductions(projectId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM project_productions WHERE project_id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(projectId)
    }
}
