package app.mcorg.presentation.plugins

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.router.configureAppRouter
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * MCO-421 — the body cap on every schematic upload route, through the **real** router.
 *
 * The handler ITs build their own route trees, so they would stay green if `limitSchematicUploads()`
 * were dropped from a route in `WorldHandler` or `IdeaHandler`. This one installs
 * `configureAppRouter()`, so a route that loses its cap fails here.
 *
 * Two ways a body arrives, and the old plugin only saw the first: with a `Content-Length`, and
 * chunked, with none. A chunked request used to stream any number of parts straight past the cap.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class SchematicUploadLimitIT : WithUser() {

    private var worldId = 0
    private var projectId = 0
    private lateinit var litematicBytes: ByteArray

    @BeforeAll
    fun setup() {
        worldId = runBlocking {
            val result = CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "Upload Limit IT World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4"),
                )
            )
            (result as Result.Success).value
        }
        projectId = runBlocking {
            val result = DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('Upload Limit IT', ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', NULL, NULL, NULL, NULL) RETURNING id"
                ),
                parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
            ).process(Unit)
            (result as Result.Success).value
        }
        litematicBytes = javaClass.getResourceAsStream("/litematica-test.litematic")!!.readBytes()
    }

    fun uploadRoutes(): List<String> = listOf(
        "/worlds/{world}/projects/from-schematic/review",
        "/worlds/{world}/projects/from-schematic",
        "/worlds/{world}/projects/{project}/resources/from-schematic",
        "/ideas/create/litematic",
    )

    private fun path(route: String) = route
        .replace("{world}", worldId.toString())
        .replace("{project}", projectId.toString())

    @ParameterizedTest
    @MethodSource("uploadRoutes")
    fun `a declared body over the cap is refused with 413`(route: String) = testApplication {
        realRouter()

        val response = client.post(path(route)) {
            addAuthCookie(this)
            setBody(ByteArrayContent(ByteArray(MAX_SCHEMATIC_UPLOAD_BYTES.toInt() + 1), multipartType))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status, response.bodyAsText())
    }

    @ParameterizedTest
    @MethodSource("uploadRoutes")
    fun `a chunked file over the cap is refused with 413`(route: String) = testApplication {
        realRouter()

        val response = postChunked(path(route)) {
            filePart("huge.litematic", ByteArray(MAX_SCHEMATIC_UPLOAD_BYTES.toInt() + 1))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status, response.bodyAsText())
    }

    /** The shape MCO-421 names: thousands of small parts, none of them a schematic, no header. */
    @ParameterizedTest
    @MethodSource("uploadRoutes")
    fun `chunked filler parts over the cap are refused with 413`(route: String) = testApplication {
        realRouter()

        val response = postChunked(path(route)) {
            val filler = ByteArray(1024)
            repeat((MAX_SCHEMATIC_UPLOAD_BYTES / filler.size).toInt() + 1) { fieldPart("filler$it", filler) }
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status, response.bodyAsText())
    }

    @Test
    fun `a chunked upload under the cap still goes through`() = testApplication {
        realRouter()

        val response = postChunked("/ideas/create/litematic") { filePart("loader.litematic", litematicBytes) }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertContains(response.bodyAsText(), "item-req")
    }

    @Test
    fun `a refused page upload gets a page, and a refused htmx upload gets a form error`() = testApplication {
        realRouter()
        val oversized = ByteArrayContent(ByteArray(MAX_SCHEMATIC_UPLOAD_BYTES.toInt() + 1), multipartType)

        val page = client.post(path("/worlds/{world}/projects/from-schematic/review")) {
            addAuthCookie(this)
            setBody(oversized)
        }
        val fragment = client.post(path("/worlds/{world}/projects/{project}/resources/from-schematic")) {
            addAuthCookie(this)
            header("HX-Request", "true")
            setBody(oversized)
        }

        assertContains(page.bodyAsText(), "413 — Upload Too Large")
        assertContains(fragment.bodyAsText(), "class=\"form-error\"")
        assertContains(fragment.bodyAsText(), "Schematics must be under 8 MB")
    }

    private fun ApplicationTestBuilder.realRouter() {
        application {
            configureAppRouter()
            configureStatusStaticRouter()
        }
    }

    private suspend fun ApplicationTestBuilder.postChunked(
        path: String,
        parts: suspend MultipartWriter.() -> Unit,
    ): HttpResponse = client.post(path) {
        addAuthCookie(this)
        setBody(object : OutgoingContent.WriteChannelContent() {
            override val contentType = multipartType
            // No length: the client sends the body chunked, without a Content-Length header.
            override val contentLength: Long? = null

            override suspend fun writeTo(channel: ByteWriteChannel) {
                MultipartWriter(channel).apply { parts() }.close()
            }
        })
    }

    private class MultipartWriter(private val channel: ByteWriteChannel) {
        suspend fun filePart(fileName: String, bytes: ByteArray) =
            part("Content-Disposition: form-data; name=\"schematicFile\"; filename=\"$fileName\"\r\nContent-Type: application/octet-stream", bytes)

        suspend fun fieldPart(name: String, bytes: ByteArray) =
            part("Content-Disposition: form-data; name=\"$name\"", bytes)

        private suspend fun part(headers: String, bytes: ByteArray) {
            channel.writeFully("--$BOUNDARY\r\n$headers\r\n\r\n".toByteArray())
            channel.writeFully(bytes)
            channel.writeFully("\r\n".toByteArray())
        }

        suspend fun close() = channel.writeFully("--$BOUNDARY--\r\n".toByteArray())
    }

    private companion object {
        const val BOUNDARY = "mco421boundary"
        val multipartType = ContentType.MultiPart.FormData.withParameter("boundary", BOUNDARY)
    }
}
