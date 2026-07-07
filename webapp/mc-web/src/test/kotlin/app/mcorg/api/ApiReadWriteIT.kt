package app.mcorg.api

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ApiReadWriteIT : WithUser() {

    private fun issueToken(user: TokenProfile = this.user): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(user.id, ApiCrypto.sha256Hex(token), "test", null))
        token
    }

    private fun createWorld(name: String, owner: TokenProfile = user): Int = runBlocking {
        (CreateWorldStep(owner).process(
            CreateWorldInput(name, "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ -> st.setString(1, name); st.setInt(2, worldId) }
        ).process(Unit) as Result.Success).value
    }

    private fun createResource(projectId: Int, itemId: String, name: String, required: Int): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, projectId); st.setString(2, itemId); st.setString(3, name); st.setInt(4, required)
            }
        ).process(Unit) as Result.Success).value
    }

    private fun createTask(projectId: Int, name: String): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO action_task (project_id, name, completed) VALUES (?, ?, false) RETURNING id"
            ),
            parameterSetter = { st, _ -> st.setInt(1, projectId); st.setString(2, name) }
        ).process(Unit) as Result.Success).value
    }

    private suspend fun ApplicationTestBuilder.getJson(path: String, token: String?): HttpResponse =
        client.get(path) { if (token != null) header("Authorization", "Bearer $token") }

    // ── GET /worlds ────────────────────────────────────────────────────────────

    @Test
    fun `worlds lists the token user's worlds`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Worlds")

        val response = getJson("/api/v1/worlds", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val worlds = apiJson.decodeFromString(ListSerializer(WorldDto.serializer()), response.bodyAsText())
        assertTrue(worlds.any { it.id == worldId && it.name == "API IT Worlds" })
    }

    @Test
    fun `worlds rejects a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        assertEquals(HttpStatusCode.Unauthorized, getJson("/api/v1/worlds", null).status)
    }

    @Test
    fun `worlds rejects an invalid token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        assertEquals(HttpStatusCode.Unauthorized, getJson("/api/v1/worlds", "garbage-token").status)
    }

    // ── GET /worlds/:id/projects ─────────────────────────────────────────────────

    @Test
    fun `world projects returns projects with resources and tasks`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Projects")
        val projectId = createProject(worldId, "Iron Farm")
        createResource(projectId, "minecraft:iron_ingot", "Iron Ingot", 64)
        createTask(projectId, "Dig out area")

        val response = getJson("/api/v1/worlds/$worldId/projects", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val projects = apiJson.decodeFromString(ListSerializer(ProjectDto.serializer()), response.bodyAsText())
        val project = projects.single { it.id == projectId }
        assertEquals("Iron Farm", project.name)
        assertEquals("minecraft:iron_ingot", project.resources.single().itemId)
        assertEquals(64, project.resources.single().required)
        assertEquals("Dig out area", project.tasks.single().name)
    }

    @Test
    fun `world projects rejects a non-member with 403`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("API IT Cross User")
        val strangerToken = issueToken(createExtraUser())

        val response = getJson("/api/v1/worlds/$worldId/projects", strangerToken)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `world projects rejects a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("API IT Projects NoAuth")
        assertEquals(HttpStatusCode.Unauthorized, getJson("/api/v1/worlds/$worldId/projects", null).status)
    }

    // ── POST /projects/:id/resources/sync ────────────────────────────────────────

    @Test
    fun `sync sets collected counts clamped to required`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Sync")
        val projectId = createProject(worldId, "Sync Project")
        createResource(projectId, "minecraft:iron_ingot", "Iron Ingot", 64)

        val response = client.post("/api/v1/projects/$projectId/resources/sync") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"resources":[{"item_id":"minecraft:iron_ingot","collected":100}]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = apiJson.decodeFromString(ResourcesResponse.serializer(), response.bodyAsText())
        assertEquals(64, body.resources.single { it.itemId == "minecraft:iron_ingot" }.collected)
    }

    @Test
    fun `sync ignores unknown items and clamps negatives to zero`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Sync Unknown")
        val projectId = createProject(worldId, "Sync Project 2")
        createResource(projectId, "minecraft:stone", "Stone", 10)

        val response = client.post("/api/v1/projects/$projectId/resources/sync") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"resources":[{"item_id":"minecraft:stone","collected":-5},{"item_id":"minecraft:unknown","collected":3}]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = apiJson.decodeFromString(ResourcesResponse.serializer(), response.bodyAsText())
        assertEquals(0, body.resources.single { it.itemId == "minecraft:stone" }.collected)
        assertTrue(body.resources.none { it.itemId == "minecraft:unknown" })
    }

    @Test
    fun `sync rejects a cross-user project with 403`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("API IT Sync Cross")
        val projectId = createProject(worldId, "Owned Project")
        createResource(projectId, "minecraft:stone", "Stone", 10)
        val strangerToken = issueToken(createExtraUser())

        val response = client.post("/api/v1/projects/$projectId/resources/sync") {
            header("Authorization", "Bearer $strangerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"resources":[{"item_id":"minecraft:stone","collected":5}]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `sync rejects a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("API IT Sync NoAuth")
        val projectId = createProject(worldId, "Sync NoAuth Project")
        val response = client.post("/api/v1/projects/$projectId/resources/sync") {
            contentType(ContentType.Application.Json)
            setBody("""{"resources":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── PUT /projects/:id/tasks/:id ──────────────────────────────────────────────

    @Test
    fun `task update sets completion`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Task")
        val projectId = createProject(worldId, "Task Project")
        val taskId = createTask(projectId, "Build hopper line")

        val response = client.put("/api/v1/projects/$projectId/tasks/$taskId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"completed":true}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = apiJson.decodeFromString(TaskDto.serializer(), response.bodyAsText())
        assertEquals(taskId, body.id)
        assertTrue(body.completed)
    }

    @Test
    fun `task update returns 404 when the task is not in the given project`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("API IT Task Mismatch")
        val projectA = createProject(worldId, "Project A")
        val projectB = createProject(worldId, "Project B")
        val taskId = createTask(projectA, "Belongs to A")

        val response = client.put("/api/v1/projects/$projectB/tasks/$taskId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"completed":true}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `task update rejects a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("API IT Task NoAuth")
        val projectId = createProject(worldId, "Task NoAuth Project")
        val taskId = createTask(projectId, "Task")
        val response = client.put("/api/v1/projects/$projectId/tasks/$taskId") {
            contentType(ContentType.Application.Json)
            setBody("""{"completed":true}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
