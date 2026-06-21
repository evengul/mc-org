package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.handleGetDrillChain
import app.mcorg.pipeline.resources.handleUpdatePlanProgress
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
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

/**
 * Integration tests for GET /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}.
 *
 * The Testcontainers DB has no ingested Minecraft graph, so a real perTarget plan cannot
 * be derived. These tests cover:
 * - Route exists and auth is enforced
 * - Graceful fallback when the plan cannot be derived (no resources → ValidationError)
 * - Graceful fallback when the item is not a target (plan unavailable → same fallback)
 *
 * Rich plan rendering (with candidate counts, chip/forced labels, depth) is covered
 * by the DrillViewTest unit test.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PlanChainIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @Test
    fun `GET chain without auth returns redirect`() = testApplication {
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Graceful fallback — no Minecraft graph in Testcontainers DB
    // -------------------------------------------------------------------------

    @Test
    fun `GET chain responds 200 with fallback fragment when plan cannot be derived`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Fragment always replaces #project-content
        assertContains(body, "project-content")
        // Back-to-plan button is always rendered
        assertContains(body, "Back to plan")
        // Fallback callout is rendered (plan derivation failed — no graph)
        assertContains(body, "callout")
    }

    @Test
    fun `GET chain with an item that has no resources defined returns graceful fragment`() = testApplication {
        // Empty project: no resource_gathering rows → ValidationError from GenerateGatheringPlanStep
        val emptyProjectId = createProject(worldId)
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$emptyProjectId/plan/chain/minecraft:oak_log"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-content")
        assertContains(body, "Back to plan")
        assertContains(body, "callout")
    }

    @Test
    fun `GET chain with URL-encoded tag id responds 200 with graceful fallback`() = testApplication {
        setupRoutes()

        // Tag ids have a '#' prefix — must be percent-encoded in the URL
        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/%23minecraft:planks"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-content")
    }

    @Test
    fun `GET chain back-to-plan link points to list lens`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Back button targets the list lens fragment
        assertContains(body, "detail-content?lens=list")
    }

    // -------------------------------------------------------------------------
    // Routing helpers
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get("/plan/chain/{itemId}") { call.handleGetDrillChain() }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture helpers
    // -------------------------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "PlanChain IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('PC IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }
}
