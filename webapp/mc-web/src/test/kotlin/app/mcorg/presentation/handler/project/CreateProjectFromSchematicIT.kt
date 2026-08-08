package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleCreateProjectFromSchematic
import app.mcorg.pipeline.project.handleReviewSchematic
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
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
        // The reviewed-create tests post their own rows, so the catalog needs these two
        // regardless of what the fixture happens to contain.
        (litematica.items.keys + setOf("minecraft:stone", "minecraft:oak_planks")).forEach { itemId ->
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
    @Disabled("Rotted while never running in CI (parsed-items vs merged-rows count drift) — repair tracked in MCO-301")
    fun `valid litematic creates active project with full resource list`() = testApplication {
        setupRoutes()

        // Two steps since MCO-303: review parses the file, create takes the reviewed list.
        val review = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes, name = "Shulker Loader"))
        }
        assertEquals(HttpStatusCode.OK, review.status, review.bodyAsText())

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(formBodyFrom(review.bodyAsText(), name = "Shulker Loader"))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val redirect = response.headers["Location"]
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

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(multipart(fileName = "broken.litematic", bytes = byteArrayOf(1, 2, 3, 4)))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `missing file returns validation error`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
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

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
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
        val response = unauthClient.post("/worlds/$worldId/projects/from-schematic/review") {
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
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    post("/from-schematic/review") { call.handleReviewSchematic() }
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

    // -------------------------------------------------------------------------
    // Review step (MCO-303): parse → review → create
    // -------------------------------------------------------------------------

    @Test
    fun `review renders the material list without creating anything`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(multipart(fileName = "loader.litematic", bytes = litematicBytes, name = "Shulker Loader"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Review this import"), "expected the review page")
        assertTrue(body.contains("import-review-table"))
        assertTrue(body.contains("Shulker Loader"), "the provided name is carried into the form")
        assertTrue(body.contains("qty["), "rows round-trip as form fields")
        assertEquals(before, countProjects(worldId), "review must not create anything")
    }

    @Test
    fun `create builds the project from the reviewed list`() = testApplication {
        setupRoutes()

        val client = createClient { followRedirects = false }
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Reviewed Build&qty[minecraft:stone]=64&qty[minecraft:oak_planks]=12")
        }

        // A plain form submit gets a real redirect, not an HX-Redirect header.
        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()
        assertEquals("Reviewed Build", getProjectName(projectId))
        assertEquals("ACTIVE" to "RESOURCE_GATHERING", getProjectStateAndStage(projectId))
        assertEquals(
            listOf("minecraft:oak_planks" to 12, "minecraft:stone" to 64),
            readRequirements(projectId),
        )
    }

    @Test
    fun `excluded rows never reach the project`() = testApplication {
        setupRoutes()

        // The review page submits only the checked rows; oak_planks was unchecked, so it
        // is simply absent from the body.
        val client = createClient { followRedirects = false }
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Partial Build&qty[minecraft:stone]=64")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status)
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()
        assertEquals(listOf("minecraft:stone" to 64), readRequirements(projectId))
    }

    @Test
    fun `excluding everything is refused`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Nothing At All")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `an item outside the world's catalog is refused`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Smuggled&qty[minecraft:not_a_real_item]=1")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `a non-member cannot create from a reviewed list either`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("reviewed-create-outsider")
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Outsider Build&qty[minecraft:stone]=1")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects(worldId))
    }

    private fun readRequirements(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, required FROM resource_gathering WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("required"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    /** Rebuilds a create-request body from the review page's rendered checkboxes. */
    private fun formBodyFrom(reviewHtml: String, name: String): String {
        val rows = Regex("""name="qty\[([^"]+)]" value="(\d+)"""")
            .findAll(reviewHtml)
            .map { "qty[${it.groupValues[1]}]=${it.groupValues[2]}" }
            .toList()
        return (listOf("name=$name") + rows).joinToString("&")
    }
}
