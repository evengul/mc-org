package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetProjectList
import app.mcorg.pipeline.project.handleGetResumeRows
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetProjectListIT : WithUser() {

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    @Test
    fun `returns 200 with empty state when no projects exist`() = testApplication {
        val emptyWorldId = createWorld("ProjectList IT Empty World")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$emptyWorldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "projects-empty-state")
        assertContains(body, "Plan your own project")
        assertContains(body, "Browse community ideas")
    }

    @Test
    fun `returns 200 with project cards when projects exist`() = testApplication {
        val worldId = createWorld("ProjectList IT Projects World")
        val projectId = createProject(worldId, "Integration Test Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "fl-pending-section")
        assertContains(body, "Integration Test Project")
    }

    @Test
    fun `returns 200 with plan view cards when view=plan`() = testApplication {
        val worldId = createWorld("ProjectList IT Plan World")
        val projectId = createProject(worldId, "Plan View Test Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects?view=plan") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-card-$projectId")
        assertContains(body, "Plan View Test Project")
        // Plan view shows resource definition count, not task progress bar
        assertContains(body, "No resources defined")
    }

    @Test
    fun `returns execute view when view param is absent`() = testApplication {
        val worldId = createWorld("ProjectList IT No Param World")
        val projectId = createProject(worldId, "Execute Default Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "fl-pending-section")
        assertContains(body, "Execute Default Project")
    }

    @Test
    fun `redirects to sign in when not authenticated`() = testApplication {
        val worldId = createWorld("ProjectList IT Auth World")
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `groups projects into field log sections by state`() = testApplication {
        val worldId = createWorld("ProjectList IT Sections World")
        val activeId = createProject(worldId, "Active Section Project", state = "ACTIVE")
        createProject(worldId, "Paused Section Project", state = "PAUSED")
        createProject(worldId, "Done Section Project", state = "DONE")
        createProject(worldId, "Cancelled Section Project", state = "CANCELLED")
        // Newest active becomes the resume hero; activeId stays in the Active list
        createProject(worldId, "Hero Absorber", state = "ACTIVE")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Active · 2")
        assertContains(body, "fl-row-$activeId")
        assertContains(body, "Paused · 1")
        assertContains(body, "fl-paused-section")
        assertContains(body, "fl-done-shelf")
        assertContains(body, "1 done · 1 cancelled")
    }

    @Test
    fun `shows dependency captions and sinks blocked project last`() = testApplication {
        val worldId = createWorld("ProjectList IT Dependency World")
        val producerId = createProject(worldId, "Slime Farm", state = "ACTIVE")
        val consumerId = createProject(worldId, "Sorting System", state = "ACTIVE")
        createResourceGathering(consumerId, "Sticky Piston", solvedByProjectId = producerId)
        // Newest active becomes the resume hero so producer/consumer stay in the list
        createProject(worldId, "Dependency Hero Absorber", state = "ACTIVE")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Feeds → Sorting System · Sticky Piston")
        assertContains(body, "Blocked by ← Slime Farm · Sticky Piston")
        assertContains(body, "badge--blocked")
        // Blocked project renders after the unblocked producer
        val producerIndex = body.indexOf("fl-row-$producerId")
        val consumerIndex = body.indexOf("fl-row-$consumerId")
        assertEquals(true, producerIndex in 0 until consumerIndex)
    }

    @Test
    fun `shows resume hero for newest active project with counter rows`() = testApplication {
        val worldId = createWorld("ProjectList IT Resume World")
        createProject(worldId, "Older Active", state = "ACTIVE")
        val resumeId = createProject(worldId, "Iron Farm", state = "ACTIVE")
        val rgId = createResourceGathering(resumeId, "Hopper")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "fl-resume-hero")
        assertContains(body, "Iron Farm")
        assertContains(body, "resource-row-$rgId")
        assertContains(body, "fl-resume-rows")
        // The resume project is not repeated in the Active list below the hero
        assertEquals(false, body.contains("fl-row-$resumeId"))
    }

    @Test
    fun `omits resume hero when world has no active projects`() = testApplication {
        val worldId = createWorld("ProjectList IT No Active World")
        createProject(worldId, "Only Pending")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(false, response.bodyAsText().contains("fl-resume-hero"))
    }

    @Test
    fun `resume rows fragment sorts by requested order`() = testApplication {
        val worldId = createWorld("ProjectList IT Resume Sort World")
        val resumeId = createProject(worldId, "Sort Hero", state = "ACTIVE")
        createResourceGathering(resumeId, "Zinc Block")
        createResourceGathering(resumeId, "Anvil")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/resume-rows") { call.handleGetResumeRows() }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/resume-rows?sort=az") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "fl-resume-rows")
        val anvilIndex = body.indexOf("Anvil")
        val zincIndex = body.indexOf("Zinc Block")
        assertEquals(true, anvilIndex in 0 until zincIndex)
    }

    private fun createResourceGathering(projectId: Int, itemName: String, solvedByProjectId: Int? = null): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, solved_by_project_id) " +
                        "VALUES (?, 'minecraft:sticky_piston', ?, 64, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemName)
                if (solvedByProjectId != null) stmt.setInt(3, solvedByProjectId) else stmt.setNull(3, java.sql.Types.INTEGER)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String, state: String = "PENDING"): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'IDEA', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
