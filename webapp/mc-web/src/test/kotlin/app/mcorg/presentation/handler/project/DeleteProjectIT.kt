package app.mcorg.presentation.handler.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DeleteProjectIT : WithUser() {

    // ---- success ------------------------------------------------------------

    @Test
    fun `world admin can delete a project and is redirected to the project list`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val projectId = createProject(worldId)

        val response = client.delete("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("/worlds/$worldId/projects", response.headers["HX-Redirect"])
        assertFalse(projectExists(projectId))
    }

    // ---- auth failure ---------------------------------------------------------

    @Test
    fun `world member without admin role cannot delete a project`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val projectId = createProject(worldId)
        val member = createExtraUser("member-cannot-delete")
        addWorldMember(member.id, worldId, Role.MEMBER, "member-${member.id}")

        val response = client.delete("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this, member)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(projectExists(projectId))
    }

    @Test
    fun `non-member cannot delete a project`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val projectId = createProject(worldId)
        val outsider = createExtraUser("outsider-delete-project")

        val response = client.delete("/worlds/$worldId/projects/$projectId") {
            addAuthCookie(this, outsider)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(projectExists(projectId))
    }

    // ---- cascade / inbound references ------------------------------------------

    @Test
    fun `deleting a project nulls out inbound solved_by_project_id references instead of leaving a dangling id`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()

        // solverProject is the "source project" that another project's resource_gathering
        // row points at via solved_by_project_id (e.g. "this farm solves this requirement").
        val solverProject = createProject(worldId, name = "Iron Farm")
        val dependentProject = createProject(worldId, name = "Iron Golem Statue")
        val resourceGatheringId = createResourceGatheringRow(
            projectId = dependentProject,
            solvedByProjectId = solverProject,
        )

        val response = client.delete("/worlds/$worldId/projects/$solverProject") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(projectExists(solverProject))
        // The dependent project itself must survive...
        assertTrue(projectExists(dependentProject))
        // ...but the dangling reference to the deleted project must be cleared.
        assertNull(getSolvedByProjectId(resourceGatheringId))
    }

    @Test
    fun `deleting a project removes project_dependencies rows on both ends`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()

        val upstreamProject = createProject(worldId, name = "Quarry")
        val downstreamProject = createProject(worldId, name = "Build Site")
        createProjectDependency(dependentProjectId = downstreamProject, dependsOnProjectId = upstreamProject)

        val response = client.delete("/worlds/$worldId/projects/$upstreamProject") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(projectExists(upstreamProject))
        assertTrue(projectExists(downstreamProject))
        assertEquals(0, countProjectDependencies(downstreamProject, upstreamProject))
    }

    // ---- routing ----------------------------------------------------------

    private fun io.ktor.server.testing.ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    method(HttpMethod.Delete) {
                        install(WorldAdminPlugin)
                        handle { call.handleDeleteProject() }
                    }
                }
            }
        }
    }

    // ---- fixtures ---------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "DeleteProjectIT World ${System.nanoTime()}",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String = "Delete IT Project"): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 'PENDING', NULL, NULL, NULL, NULL) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGatheringRow(projectId: Int, solvedByProjectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, solved_by_project_id) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', 10, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, solvedByProjectId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createProjectDependency(dependentProjectId: Int, dependsOnProjectId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_dependencies (project_id, depends_on_project_id) VALUES (?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, dependentProjectId)
                stmt.setInt(2, dependsOnProjectId)
            }
        ).process(Unit)
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

    private fun projectExists(projectId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT EXISTS(SELECT 1 FROM projects WHERE id = ?)"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next() && rs.getBoolean(1) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getSolvedByProjectId(resourceGatheringId: Int): Int? = runBlocking {
        val result = DatabaseSteps.query<Unit, Int?>(
            sql = SafeSQL.select("SELECT solved_by_project_id FROM resource_gathering WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, resourceGatheringId) },
            resultMapper = { rs ->
                rs.next()
                val value = rs.getInt("solved_by_project_id")
                if (rs.wasNull()) null else value
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun countProjectDependencies(projectId: Int, dependsOnProjectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select(
                "SELECT COUNT(*) AS cnt FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
            },
            resultMapper = { rs -> rs.next(); rs.getInt("cnt") }
        ).process(Unit)
        (result as Result.Success).value
    }
}
