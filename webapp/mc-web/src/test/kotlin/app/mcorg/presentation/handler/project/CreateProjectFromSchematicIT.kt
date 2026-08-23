package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
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
import kotlin.test.assertFalse
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
        // regardless of what the fixture happens to contain. The bulk ids back the MCO-315
        // regression: a list far wider than the old ~466-row cutoff.
        val bulkIds = (1..BULK_ROWS).map { "minecraft:bulk_item_$it" }
        val catalog = (litematica.items.keys + setOf("minecraft:stone", "minecraft:oak_planks") + bulkIds).toList()
        runBlocking {
            DatabaseSteps.batchUpdate<String>(
                sql = SafeSQL.insert(
                    "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
                ),
                parameterSetter = { stmt, itemId ->
                    stmt.setString(1, itemId)
                    stmt.setString(2, itemId.removePrefix("minecraft:").replace('_', ' '))
                }
            ).process(catalog)
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

    /** Several files under the one field name, as a `multiple` file input posts them (MCO-414). */
    private fun multipart(files: List<Pair<String, ByteArray>>, name: String? = null) =
        MultiPartFormDataContent(formData {
            if (name != null) append("name", name)
            files.forEach { (fileName, bytes) ->
                append("schematicFile", bytes, Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"schematicFile\"; filename=\"$fileName\"",
                    )
                    append(HttpHeaders.ContentType, "application/octet-stream")
                })
            }
        })

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
        assertTrue(
            body.contains("""name="materials" value="v1;"""),
            "the whole list round-trips as one field (MCO-315)",
        )
        assertFalse(body.contains("qty["), "and never as one parameter per row again")
        assertEquals(before, countProjects(worldId), "review must not create anything")
    }

    // -------------------------------------------------------------------------
    // Several files as one import (MCO-414)
    // -------------------------------------------------------------------------

    @Test
    fun `two files review as one import and every material is counted`() = testApplication {
        // The bug this replaces: the receive step overwrote its buffer per part, so uploading a
        // build's overworld and nether halves imported only whichever arrived last — a project
        // silently missing a third of its materials.
        setupRoutes()
        val before = countProjects(worldId)

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(
                multipart(
                    files = listOf(
                        "Sorter.litematic" to litematicBytes,
                        "Sorter (nether).litematic" to litematicBytes,
                    ),
                    name = "Split Build",
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.bodyAsText()
        assertTrue(body.contains("These 2 files are being imported as one project"), "the lead names both files")
        assertTrue(body.contains("Sorter (nether)"), "the second file is a section of its own")
        assertEquals(before, countProjects(worldId), "review must not create anything")
    }

    @Test
    fun `the same file twice doubles the quantities rather than replacing them`() = testApplication {
        // Uploading one file as both halves is not a real workflow, but it makes the sum
        // checkable: every amount must be exactly twice the single-file import's.
        setupRoutes()

        val single = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(multipart(fileName = "Sorter.litematic", bytes = litematicBytes))
        }
        val double = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(
                multipart(
                    files = listOf("A.litematic" to litematicBytes, "B.litematic" to litematicBytes),
                )
            )
        }

        val singleRows = materialRowsOf(single.bodyAsText())
        val doubleRows = materialRowsOf(double.bodyAsText())

        assertTrue(singleRows.isNotEmpty(), "expected a parsed material list")
        assertEquals(singleRows.keys, doubleRows.keys, "the same items, from the same file twice")
        singleRows.forEach { (itemId, amount) ->
            assertEquals(amount * 2, doubleRows[itemId], "$itemId should be doubled")
        }
    }

    @Test
    fun `an unreadable file names itself rather than failing anonymously`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/from-schematic/review") {
            addAuthCookie(this)
            setBody(
                multipart(
                    files = listOf(
                        "Sorter.litematic" to litematicBytes,
                        "Sorter (nether).litematic" to byteArrayOf(1, 2, 3, 4),
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(
            response.bodyAsText().contains("Sorter (nether).litematic"),
            "with several files, the user has to be told which one to re-export",
        )
    }

    /**
     * Item id -> total amount, read back out of the review page's single `materials` field.
     *
     * Summed rather than keyed, because the payload carries one row **per section**: an item in
     * three groups is three rows, and the server sums them the same way
     * ([ValidateReviewedMaterialsStep]). Keying by id would keep only the last and read a
     * correctly-doubled import as unchanged.
     */
    private fun materialRowsOf(html: String): Map<String, Int> {
        val payload = Regex("""name="materials" value="([^"]*)"""").find(html)?.groupValues?.get(1)
            ?: return emptyMap()
        val totals = mutableMapOf<String, Int>()
        payload.split(";")
            .drop(2) // version marker and declared row count
            .filter { it.isNotBlank() && !it.startsWith("!") }
            .forEach { row ->
                val id = row.substringBeforeLast('=')
                totals[id] = (totals[id] ?: 0) + row.substringAfterLast('=').toInt()
            }
        return totals
    }

    @Test
    fun `create builds the project from the reviewed list`() = testApplication {
        setupRoutes()

        val client = createClient { followRedirects = false }
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Reviewed Build&" + materials("minecraft:stone" to 64, "minecraft:oak_planks" to 12))
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

        // The list carries every row; oak_planks rides along marked struck and is dropped
        // server-side rather than simply being absent from the body.
        val client = createClient { followRedirects = false }
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "name=Partial Build&" + materials(
                    "minecraft:stone" to 64,
                    "!minecraft:oak_planks" to 12,
                )
            )
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
            setBody("name=Nothing At All&" + materials("!minecraft:stone" to 64))
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
            setBody("name=Smuggled&" + materials("minecraft:not_a_real_item" to 1))
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
            setBody("name=Outsider Build&" + materials("minecraft:stone" to 1))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects(worldId))
    }

    // -------------------------------------------------------------------------
    // MCO-315 — a list wider than the old request-parameter cap
    // -------------------------------------------------------------------------

    @Test
    fun `a list far past the old 466-row cutoff persists every included row`() = testApplication {
        setupRoutes()

        // The bug in one assertion: 600 rows at two parameters each overflowed Ktor's
        // 1000-pair body cap, which stops decoding instead of failing, so the project came
        // out ~135 materials short with nothing said about it.
        val rows = (1..BULK_ROWS).map { "minecraft:bulk_item_$it" to it }
        val struck = rows.filterIndexed { index, _ -> index % 7 == 0 }.map { it.first }.toSet()

        val client = createClient { followRedirects = false }
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "name=Wide Build&" + materials(
                    *rows.map { (id, amount) ->
                        (if (id in struck) "!$id" else id) to amount
                    }.toTypedArray()
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        val persisted = readRequirements(projectId).toMap()
        assertEquals(BULK_ROWS - struck.size, persisted.size, "every included row is persisted")
        rows.forEach { (id, amount) ->
            if (id in struck) {
                assertFalse(id in persisted, "$id was struck and must not be persisted")
            } else {
                assertEquals(amount, persisted[id], "$id must survive the import")
            }
        }
    }

    @Test
    fun `a material list that arrives short is refused rather than partly imported`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        // Exactly the shape a truncating transport produces: the payload still declares 600
        // rows, only 466 of them arrive. Loud failure, no project.
        val full = ReviewedMaterialsCodec.encode(
            (1..BULK_ROWS).map { ReviewedMaterial("minecraft:bulk_item_$it", it, included = true) }
        )
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Cut Short&${ReviewedMaterialsCodec.FIELD}=$truncated")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("466 of $BULK_ROWS"), response.bodyAsText())
        assertEquals(before, countProjects(worldId))
    }

    @Test
    fun `a submission with no material list at all is refused`() = testApplication {
        setupRoutes()
        val before = countProjects(worldId)

        // What a review page rendered before MCO-315 would send. Importing "whatever arrived"
        // is the failure mode this whole change exists to remove.
        val response = client.post("/worlds/$worldId/projects/from-schematic") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Stale Page&qty[minecraft:stone]=64")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
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

    /** Rebuilds a create-request body from the review page's single rendered materials field. */
    private fun formBodyFrom(reviewHtml: String, name: String): String {
        val field = Regex("""name="materials" value="([^"]+)"""").find(reviewHtml)
        assertNotNull(field, "the review page must render the materials field")
        return "name=$name&${ReviewedMaterialsCodec.FIELD}=${field.groupValues[1]}"
    }

    /**
     * A request body carrying the whole list in one field, as the review page now sends it.
     * Prefix an id with `!` to mark the row struck.
     */
    private fun materials(vararg rows: Pair<String, Int>): String {
        val encoded = ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) ->
                ReviewedMaterial(id.removePrefix("!"), amount, included = !id.startsWith("!"))
            }
        )
        return "${ReviewedMaterialsCodec.FIELD}=$encoded"
    }

    private companion object {
        /** Comfortably past the ~466 rows that used to fit in a request body. */
        const val BULK_ROWS = 600
    }
}
