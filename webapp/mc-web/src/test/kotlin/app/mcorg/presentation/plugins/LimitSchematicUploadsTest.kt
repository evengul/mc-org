package app.mcorg.presentation.plugins

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MCO-345's ordering guarantee: a declared body over the cap is refused before the handler runs,
 * not when the handler first reads the body. The world routes check `Role.ADMIN` inside their
 * pipelines, so a check that waited for the read would let an unauthorized member reach them.
 */
class LimitSchematicUploadsTest {

    @Test
    fun `a declared body over the cap never reaches the handler`() = testApplication {
        var handlerRan = false
        application {
            configureStatusStaticRouter()
            routing {
                route("/upload") {
                    limitSchematicUploads()
                    // Never reads the body: only a check ahead of the handler can refuse this.
                    post {
                        handlerRan = true
                        call.respondText("handled")
                    }
                }
            }
        }

        val response = client.post("/upload") {
            setBody(ByteArrayContent(ByteArray(MAX_SCHEMATIC_UPLOAD_BYTES.toInt() + 1), ContentType.Application.OctetStream))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertFalse(handlerRan)
    }
}
