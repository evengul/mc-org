package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.resources.handleAddResourcesFromSchematic
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class AddResourcesFromSchematicIT : WithUser() {

    private var worldId: Int = 0
    private lateinit var litematicBytes: ByteArray
    private lateinit var expectedItemIds: Set<String>

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        litematicBytes = javaClass.getResourceAsStream("/litematica-test.litematic")!!.readBytes()

        val litematica = (LitematicaReader.readLitematica(litematicBytes) as Result.Success).value
        expectedItemIds = litematica.items.keys.toSet()
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES ('1.21.4') ON CONFLICT DO NOTHING"),
                parameterSetter = { _, _ -> }
            ).process(Unit)
        }
        litematica.items.keys.forEach { itemId ->
            runBlocking {
                DatabaseSteps.update<Unit>(
                    sql = SafeSQL.insert(
                        "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
                    ),
                    parameterSetter = { stmt, _ ->
                        stmt.setString(1, itemId)
                        stmt.setString(2, itemId.removePrefix("minecraft:").replace('_', ' '))
                    }
                ).process(Unit)
            }
        }
    }

    @Test
    fun `upload populates an empty project with the schematic's materials`() = testApplication {
        setupRoutes()
        val projectId = createProject()

        val response = client.post("/worlds/$worldId/projects/$projectId/resources/from-schematic") {
            addAuthCookie(this)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes))
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(expectedItemIds, getResourceItemIds(projectId))
    }

    @Test
    fun `upload replaces a project's pre-existing resources`() = testApplication {
        setupRoutes()
        val projectId = createProject()
        addResource(projectId, "minecraft:dirt", "Dirt", 64)
        assertEquals(setOf("minecraft:dirt"), getResourceItemIds(projectId))

        val response = client.post("/worlds/$worldId/projects/$projectId/resources/from-schematic") {
            addAuthCookie(this)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes))
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        // The pre-existing manual resource (dirt, not in the fixture) is gone; only the parsed set remains.
        assertEquals(expectedItemIds, getResourceItemIds(projectId))
        assertTrue("minecraft:dirt" !in expectedItemIds, "fixture unexpectedly contains dirt; pick another sentinel item")
    }

    @Test
    fun `non-admin member cannot replace resources`() = testApplication {
        setupRoutes()
        val projectId = createProject()
        addResource(projectId, "minecraft:dirt", "Dirt", 64)
        val member = createExtraUser("schematic-resources-member")
        addWorldMember(member.id, Role.MEMBER)

        val response = client.post("/worlds/$worldId/projects/$projectId/resources/from-schematic") {
            addAuthCookie(this, member)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(setOf("minecraft:dirt"), getResourceItemIds(projectId))
    }

    @Test
    fun `malformed file surfaces a validation error and leaves resources untouched`() = testApplication {
        setupRoutes()
        val projectId = createProject()
        addResource(projectId, "minecraft:dirt", "Dirt", 64)

        val response = client.post("/worlds/$worldId/projects/$projectId/resources/from-schematic") {
            addAuthCookie(this)
            setBody(multipart(fileName = "broken.litematic", bytes = byteArrayOf(1, 2, 3, 4)))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(setOf("minecraft:dirt"), getResourceItemIds(projectId))
    }

    @Test
    fun `missing file surfaces a validation error`() = testApplication {
        setupRoutes()
        val projectId = createProject()

        val response = client.post("/worlds/$worldId/projects/$projectId/resources/from-schematic") {
            addAuthCookie(this)
            setBody(MultiPartFormDataContent(formData { append("ignored", "value") }))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // -------------------------------------------------------------------------

    private fun multipart(fileName: String, bytes: ByteArray) =
        MultiPartFormDataContent(formData {
            append("schematicFile", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"schematicFile\"; filename=\"$fileName\"")
            })
        })

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    post("/resources/from-schematic") { call.handleAddResourcesFromSchematic() }
                }
            }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "Add Resources Schematic IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('Schematic Resources IT', ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', NULL, NULL, NULL, NULL) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun addResource(projectId: Int, itemId: String, name: String, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO resource_gathering (project_id, name, required, item_id) VALUES (?, ?, ?, ?) RETURNING id"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, name)
                stmt.setInt(3, required)
                stmt.setString(4, itemId)
            }
        ).process(Unit)
    }

    private fun addWorldMember(userId: Int, role: Role) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO world_members (world_id, user_id, display_name, world_role, created_at, updated_at) " +
                        "VALUES (?, ?, 'member', ?, NOW(), NOW())"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, userId)
                stmt.setInt(3, role.level)
            }
        ).process(Unit)
    }

    private fun getResourceItemIds(projectId: Int): Set<String> = runBlocking {
        val result = DatabaseSteps.query<Unit, Set<String>>(
            sql = SafeSQL.select("SELECT item_id FROM resource_gathering WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs ->
                val ids = mutableSetOf<String>()
                while (rs.next()) ids.add(rs.getString("item_id"))
                ids
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
