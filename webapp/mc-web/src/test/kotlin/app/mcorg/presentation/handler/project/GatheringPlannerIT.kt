package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.project.handleGetProject
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
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
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
import kotlin.test.assertFalse

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GatheringPlannerIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var resourceGatheringId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
        resourceGatheringId = createResourceGathering(projectId, required = 10)
    }

    // -------------------------------------------------------------------------
    // Lens navigation
    // -------------------------------------------------------------------------

    @Test
    fun `GET project detail always shows unified planner with lens pills`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Unified surface: definition table always rendered
        assertContains(body, "plan-resource-table")
        // Lens tabs rendered (tabStrip with lens query name)
        assertContains(body, "lens=list")
        assertContains(body, "lens=next")
        assertContains(body, "lens=sessions")
        // No old toggle
        assertFalse(body.contains("toggle__btn"))
    }

    @Test
    fun `GET detail-content with lens=list returns list lens`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?lens=list"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "plan-resource-table")
    }

    @Test
    fun `GET detail-content with lens=next returns coming-soon stub`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?lens=next"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "coming soon")
    }

    @Test
    fun `GET detail-content with lens=sessions returns coming-soon stub`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?lens=sessions"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "coming soon")
    }

    @Test
    fun `GET detail-content with old view=plan param maps to list lens`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?view=plan"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "plan-resource-table")
    }

    @Test
    fun `GET detail-content with old view=execute param maps to list lens`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?view=execute"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "plan-resource-table")
    }

    // -------------------------------------------------------------------------
    // No-plan fallback: ValidationError (all collected / no resources)
    // -------------------------------------------------------------------------

    @Test
    fun `GET detail-content renders empty state when no resources are defined`() = testApplication {
        val emptyProjectId = createProject(worldId)
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$emptyProjectId/detail-content?lens=list"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // Page renders OK — definition controls + empty plan state
        assertContains(response.bodyAsText(), "plan-resource-table")
    }

    // -------------------------------------------------------------------------
    // Plan progress endpoint (PATCH /plan/progress)
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH plan progress increments collected and returns updated row`() = testApplication {
        setupRoutesWithProgress()

        val itemId = "minecraft:iron_ingot"
        val required = 64L

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$itemId&amount=1&required=$required")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Updated row is returned
        assertContains(body, "plan-activity-minecraft-iron_ingot")
        // The row shows the new collected value (not 0)
        assertContains(body, "1 / $required")
        // Persisted to DB
        assertEquals(1, getProgressCollected(projectId, itemId))
        // Note: OOB overall-progress is only emitted when plan derivation succeeds.
        // In the Testcontainers environment there is no Minecraft graph, so it is omitted — no assertion here.
    }

    @Test
    fun `PATCH plan progress decrement is clamped to 0`() = testApplication {
        setupRoutesWithProgress()

        val itemId = "minecraft:gold_ingot"
        val required = 10L

        // First set some progress
        seedItemProgress(projectId, itemId, 3)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$itemId&amount=-100&required=$required")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, getProgressCollected(projectId, itemId))
    }

    @Test
    fun `PATCH plan progress clamped at required ceiling`() = testApplication {
        setupRoutesWithProgress()

        val itemId = "minecraft:diamond"
        val required = 5L

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$itemId&amount=9999&required=$required")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(5, getProgressCollected(projectId, itemId))
    }

    @Test
    fun `PATCH plan progress with amount=0 returns 422`() = testApplication {
        setupRoutesWithProgress()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:iron_ingot&amount=0&required=64")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PATCH plan progress with required=0 returns 422`() = testApplication {
        setupRoutesWithProgress()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:iron_ingot&amount=1&required=0")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PATCH plan progress with missing itemId returns 400`() = testApplication {
        setupRoutesWithProgress()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=1&required=64")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH plan progress without auth returns redirect`() = testApplication {
        setupRoutesWithProgress()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:iron_ingot&amount=1&required=64")
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Fix 1 — derived activity progress persists and row reflects it
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH plan progress for a derived item (no resource_gathering row) persists and row shows current`() = testApplication {
        // A derived item is an engine-intermediate that has NO resource_gathering row.
        // Progress must still persist and be returned in the updated row.
        setupRoutesWithProgress()

        val derivedItemId = "minecraft:oak_planks"
        val required = 100L

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/plan/progress"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$derivedItemId&amount=7&required=$required")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Row for derived item is returned
        assertContains(body, "plan-activity-minecraft-oak_planks")
        // Row shows persisted value (7), not 0
        assertContains(body, "7 / $required")
        // Persisted in DB (no resource_gathering row needed)
        assertEquals(7, getProgressCollected(projectId, derivedItemId))
    }

    @Test
    fun `PATCH plan progress row current is cumulative across multiple increments for derived item`() = testApplication {
        setupRoutesWithProgress()

        val derivedItemId = "minecraft:oak_log"
        val required = 50L

        // First increment
        client.patch("/worlds/$worldId/projects/$projectId/plan/progress") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$derivedItemId&amount=10&required=$required")
        }

        // Second increment
        val response = client.patch("/worlds/$worldId/projects/$projectId/plan/progress") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$derivedItemId&amount=15&required=$required")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Row shows accumulated total (25), not just the last delta (15)
        assertContains(body, "25 / $required")
        assertEquals(25, getProgressCollected(projectId, derivedItemId))
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
                    get { call.handleGetProject() }
                    get("/detail-content") { call.handleGetDetailContent() }
                }
            }
        }
    }

    private fun ApplicationTestBuilder.setupRoutesWithProgress() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    patch("/plan/progress") { call.handleUpdatePlanProgress() }
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
                name = "GatheringPlanner IT World",
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
                        "VALUES ('GP IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, required: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, required)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun seedItemProgress(projectId: Int, itemId: String, collected: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (project_id, item_id) DO UPDATE
                    SET collected = ?, updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setInt(3, collected)
                stmt.setInt(4, collected)
            }
        ).process(Unit)
    }

    private fun getProgressCollected(projectId: Int, itemId: String): Int = runBlocking {
        DatabaseSteps.query<Pair<Int, String>, Int>(
            sql = SafeSQL.select(
                "SELECT COALESCE(collected, 0) FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.first)
                stmt.setString(2, inp.second)
            },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).process(Pair(projectId, itemId)).getOrNull() ?: 0
    }
}
