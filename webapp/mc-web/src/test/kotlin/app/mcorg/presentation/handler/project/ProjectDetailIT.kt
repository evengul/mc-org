package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.resources.handleSetCollectedValue
import app.mcorg.pipeline.resources.handleUpdateRequirementProgress
import app.mcorg.pipeline.task.handleCompleteActionTask
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.ActionTaskParamPlugin
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
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

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectDetailIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var resourceGatheringId: Int = 0
    private var taskId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
        resourceGatheringId = createResourceGathering(projectId, required = 10, collected = 2)
        taskId = createTask(projectId, "Test Task")
    }

    // -------------------------------------------------------------------------
    // Test 1: page load returns execute view by default
    // -------------------------------------------------------------------------

    @Test
    fun `returns 200 with execute view on page load`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "resource-list-container")
        assertContains(body, "task-section")
    }

    // -------------------------------------------------------------------------
    // Test 2: page load shows plan stub when preference is plan
    // -------------------------------------------------------------------------

    @Test
    fun `returns 200 with plan view when view preference is plan`() = testApplication {
        setViewPreference(user.id, projectId, "plan")

        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "plan-resource-table")

        // Reset for other tests
        setViewPreference(user.id, projectId, "execute")
    }

    // -------------------------------------------------------------------------
    // Test 3: edit-done increments collected value
    // -------------------------------------------------------------------------

    @Test
    fun `edit-done increments collected value`() = testApplication {
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/edit-done"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=1")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "resource-row-$resourceGatheringId")
    }

    // -------------------------------------------------------------------------
    // Test 4: edit-done with amount=0 returns 400
    // -------------------------------------------------------------------------

    @Test
    fun `edit-done with amount=0 returns validation error`() = testApplication {
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/edit-done"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=0")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // -------------------------------------------------------------------------
    // Test 5: edit-done decrement is clamped to 0
    // -------------------------------------------------------------------------

    @Test
    fun `edit-done decrement is clamped to 0`() = testApplication {
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/edit-done"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=-100")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "resource-row-$resourceGatheringId")
        assertEquals(0, getCollected(resourceGatheringId))
    }

    // -------------------------------------------------------------------------
    // Test 6: collected PUT sets absolute value clamped to required
    // -------------------------------------------------------------------------

    @Test
    fun `collected PUT sets absolute value clamped to required`() = testApplication {
        setupRoutes()

        val response = client.put(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/collected"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("value=999")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "resource-row-$resourceGatheringId")
        assertEquals(10, getCollected(resourceGatheringId))
    }

    // -------------------------------------------------------------------------
    // Test 7: detail-content returns execute view and persists preference
    // -------------------------------------------------------------------------

    @Test
    fun `detail-content returns execute view and persists preference`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?view=execute"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "resource-list-container")
        assertEquals("execute", getViewPreference(user.id, projectId))
    }

    // -------------------------------------------------------------------------
    // Test 8: detail-content returns plan stub
    // -------------------------------------------------------------------------

    @Test
    fun `detail-content returns plan view content`() = testApplication {
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/detail-content?view=plan"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "plan-resource-table")
    }

    // -------------------------------------------------------------------------
    // Test 9: unauthenticated page load redirects to sign-in
    // -------------------------------------------------------------------------

    @Test
    fun `page load redirects to sign in when unauthenticated`() = testApplication {
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.get("/worlds/$worldId/projects/$projectId")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Routing helper
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
                        install(ResourceGatheringIdParamPlugin)
                        patch("/edit-done") { call.handleUpdateRequirementProgress() }
                        put("/collected") { call.handleSetCollectedValue() }
                    }
                    route("/tasks/{taskId}") {
                        install(ActionTaskParamPlugin)
                        patch("/complete") { call.handleCompleteActionTask() }
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
                name = "ProjectDetail IT World",
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
                        "VALUES ('Detail IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, required: Int, collected: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, collected) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, required)
                stmt.setInt(3, collected)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createTask(projectId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO action_task (project_id, name, completed) VALUES (?, ?, FALSE) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, name)
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

    private fun getCollected(id: Int): Int = runBlocking {
        DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT collected FROM resource_gathering WHERE id = ?"),
            parameterSetter = { stmt, inp -> stmt.setInt(1, inp) },
            resultMapper = { rs -> if (rs.next()) rs.getInt("collected") else 0 }
        ).process(id).getOrNull() ?: 0
    }

    private fun getViewPreference(userId: Int, projectId: Int): String? = runBlocking {
        DatabaseSteps.query<Pair<Int, Int>, String?>(
            sql = SafeSQL.select(
                "SELECT view_preference FROM user_project_view_preference WHERE user_id = ? AND project_id = ?"
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.first)
                stmt.setInt(2, inp.second)
            },
            resultMapper = { rs -> if (rs.next()) rs.getString("view_preference") else null }
        ).process(Pair(userId, projectId)).getOrNull()
    }
}
