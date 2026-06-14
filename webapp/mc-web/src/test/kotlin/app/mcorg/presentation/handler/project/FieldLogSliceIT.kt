package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetFieldLogRow
import app.mcorg.pipeline.project.handleGetFieldLogSliceItems
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
import kotlin.test.assertFalse

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FieldLogSliceIT : WithUser() {

    private var worldId: Int = 0
    private var producerId: Int = 0
    private var blockedId: Int = 0
    private var freeId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        producerId = createProject("Slime Farm")
        blockedId = createProject("Sorting System")
        freeId = createProject("Storage Hall")
        createResourceGathering(blockedId, "Sticky Piston", solvedByProjectId = producerId)
        createResourceGathering(blockedId, "Smooth Stone")
        createResourceGathering(blockedId, "Redstone Comparator")
        createResourceGathering(freeId, "Quartz Block")
    }

    @Test
    fun `expanding a blocked project returns rich slice`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$blockedId/field-log-row?expanded=true") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "fl-row-$blockedId")
        assertContains(body, "collapse ▲")
        assertContains(body, "Partially blocked")
        assertContains(body, "Sticky Piston waits on Slime Farm")
        assertContains(body, "fl-slice-rows-$blockedId")
        assertContains(body, "Smooth Stone")
        // The blocked item is not gatherable, so it has no counter row
        assertFalse(body.contains("data-item-name=\"Sticky Piston\""))
    }

    @Test
    fun `expanding an unblocked project returns light slice`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$freeId/field-log-row?expanded=true") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Next up ·")
        assertContains(body, "Quartz Block")
        assertContains(body, "open project page →")
        assertFalse(body.contains("Partially blocked"))
    }

    @Test
    fun `collapsing returns plain row without slice`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$blockedId/field-log-row?expanded=false") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "expand ▾")
        assertFalse(body.contains("fl-slice-rows"))
    }

    @Test
    fun `slice items endpoint filters by query`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$blockedId/field-log-slice-items?query=stone") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Smooth Stone")
        assertFalse(body.contains("Redstone Comparator") && !body.contains("stone"))
        assertFalse(body.contains("Quartz Block"))
    }

    @Test
    fun `unauthenticated request is redirected`() = testApplication {
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.get("/worlds/$worldId/projects/$blockedId/field-log-row?expanded=true")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get("/field-log-row") { call.handleGetFieldLogRow() }
                    get("/field-log-slice-items") { call.handleGetFieldLogSliceItems() }
                }
            }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "FieldLogSlice IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, itemName: String, solvedByProjectId: Int? = null): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, solved_by_project_id) " +
                        "VALUES (?, 'minecraft:test_item', ?, 64, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemName)
                if (solvedByProjectId != null) stmt.setInt(3, solvedByProjectId) else stmt.setNull(3, java.sql.Types.INTEGER)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
