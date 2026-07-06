package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.resources.handleCreateResourceGatheringItem
import app.mcorg.pipeline.resources.handleDeleteResourceGatheringItem
import app.mcorg.pipeline.resources.handleToggleResourceGatheringIgnored
import app.mcorg.pipeline.resources.handleUpdateResourceRequiredAmount
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
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
class PlanViewIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var resourceGatheringId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
        resourceGatheringId = createResourceGathering(projectId, required = 10)
        setViewPreference(user.id, projectId, "plan")
    }

    // -------------------------------------------------------------------------
    // Test 1: GET project detail with plan view preference renders plan content
    // -------------------------------------------------------------------------

    @Test
    fun `returns plan view content when view preference is plan`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "plan-resource-table")
        assertContains(body, "plan-row-$resourceGatheringId")
    }

    // -------------------------------------------------------------------------
    // Test 2: GET detail-content?view=plan returns plan view
    // -------------------------------------------------------------------------

    @Test
    fun `detail-content returns plan view content`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId/detail-content?view=plan") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "plan-resource-table")
    }

    // -------------------------------------------------------------------------
    // Test 3: PATCH required — success updates row
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH required with valid value returns updated row`() = testApplication {
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/required"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("required=25")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "plan-row-$resourceGatheringId")
        assertContains(body, "25")
    }

    // -------------------------------------------------------------------------
    // Test 4: PATCH required — value=0 returns 422
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH required with value=0 returns 422`() = testApplication {
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/required"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("required=0")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // -------------------------------------------------------------------------
    // Test 5: PATCH required — unauthenticated returns redirect
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH required without auth returns redirect`() = testApplication {
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/required"
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("required=5")
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Test 6: PATCH ignore — toggles the ignored flag and re-renders the resources area
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH ignore moves the target into the ignored section`() = testApplication {
        val rgId = createResourceGathering(projectId, required = 15)
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/ignore"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "id=\"plan-resources-area\"")
        // Ignored row appears in the ignored section, not the active table.
        assertContains(body, "plan-ignored-row-$rgId")
        assertContains(body, "Ignored (1)")
        assertFalse(body.contains("plan-row-$rgId\""))
    }

    @Test
    fun `PATCH ignore twice restores the target to the active table`() = testApplication {
        val rgId = createResourceGathering(projectId, required = 12)
        setupRoutes()

        client.patch("/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/ignore") {
            addAuthCookie(this)
        }
        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/ignore"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Back in the active table, no longer in the ignored section.
        assertContains(body, "plan-row-$rgId")
        assertFalse(body.contains("plan-ignored-row-$rgId"))
    }

    @Test
    fun `PATCH ignore without auth returns redirect`() = testApplication {
        val rgId = createResourceGathering(projectId, required = 8)
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/ignore"
        ) {}

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Test 7: non-member (authenticated, but not a world_members row) is rejected
    // by WorldParticipantPlugin — MCO-247 IDOR closure on the resource-mutation family.
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH ignore by a non-member of the world returns 403`() = testApplication {
        val rgId = createResourceGathering(projectId, required = 9)
        setupRoutes()

        val nonMember = createExtraUser()
        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/ignore"
        ) {
            addAuthCookie(this, nonMember)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `PATCH required by a non-member of the world returns 403`() = testApplication {
        setupRoutes()

        val nonMember = createExtraUser()
        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/required"
        ) {
            addAuthCookie(this, nonMember)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("required=5")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // -------------------------------------------------------------------------
    // Routing setup
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
                    route("/resources/gathering/{resourceGatheringId}") {
                        install(WorldParticipantPlugin)
                        install(ResourceGatheringIdParamPlugin)
                        patch("/required") { call.handleUpdateResourceRequiredAmount() }
                        patch("/ignore") { call.handleToggleResourceGatheringIgnored() }
                    }
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
                name = "PlanView IT World",
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
                        "VALUES ('PlanView IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
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

    private fun setViewPreference(userId: Int, projectId: Int, preference: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO user_project_view_preference (user_id, project_id, view_preference, updated_at) " +
                        "VALUES (?, ?, ?, now()) " +
                        "ON CONFLICT (user_id, project_id) DO UPDATE SET view_preference = ?, updated_at = now()"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, projectId)
                stmt.setString(3, preference)
                stmt.setString(4, preference)
            }
        ).process(Unit)
    }
}
