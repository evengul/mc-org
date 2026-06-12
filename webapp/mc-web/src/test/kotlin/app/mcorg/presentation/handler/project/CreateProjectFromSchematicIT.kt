package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleCreateProjectFromSchematic
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class CreateProjectFromSchematicIT : WithUser() {

    private var worldId: Int = 0
    private lateinit var litematicBytes: ByteArray
    private var expectedItemCount: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        litematicBytes = javaClass.getResourceAsStream("/litematica-test.litematic")!!.readBytes()

        // Seed minecraft_items for the world version with the fixture's materials
        val litematica = (LitematicaReader.readLitematica(litematicBytes) as Result.Success).value
        expectedItemCount = litematica.items.size
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
    fun `valid litematic creates active project with full resource list`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes, name = "Shulker Loader"))
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val redirect = response.headers["HX-Redirect"]
        assertNotNull(redirect)
        assertTrue(redirect.startsWith("/worlds/$worldId/projects/"))

        val projectId = redirect.substringAfterLast("/").toInt()
        assertEquals("ACTIVE" to "RESOURCE_GATHERING", getProjectStateAndStage(projectId))
        assertEquals(expectedItemCount, countResourceRows(projectId))
        assertEquals("Shulker Loader", getProjectName(projectId))
    }

    @Test
    fun `corrupt file returns validation error without creating a project`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            setBody(multipart(fileName = "broken.litematic", bytes = byteArrayOf(1, 2, 3, 4)))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `missing file returns validation error`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            setBody(MultiPartFormDataContent(formData { append("name", "No File") }))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `non-member cannot create from schematic`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("schematic-outsider")
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this, outsider)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `unauthenticated request is redirected`() = testApplication {
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.post("/worlds/$worldId/projects/from-schematic") {
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes))
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------

    private fun multipart(fileName: String, bytes: ByteArray, name: String? = null) =
        MultiPartFormDataContent(formData {
            if (name != null) append("name", name)
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
                route("/projects") {
                    post("/from-schematic") { call.handleCreateProjectFromSchematic() }
                }
            }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "Schematic IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun getProjectStateAndStage(projectId: Int): Pair<String, String> = runBlocking {
        val result = DatabaseSteps.query<Unit, Pair<String, String>>(
            sql = SafeSQL.select("SELECT state, stage FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getString("state") to rs.getString("stage") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getProjectName(projectId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Unit, String>(
            sql = SafeSQL.select("SELECT name FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getString("name") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun countResourceRows(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) AS c FROM resource_gathering WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getInt("c") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun countProjects(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) AS c FROM projects WHERE world_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) },
            resultMapper = { rs -> rs.next(); rs.getInt("c") }
        ).process(Unit)
        (result as Result.Success).value
    }
}
