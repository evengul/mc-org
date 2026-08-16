package app.mcorg.presentation.handler

import app.mcorg.presentation.plugins.configureMonitoring
import app.mcorg.presentation.plugins.configureStatusStaticRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class StatusPagesIT {

    @Test
    fun `Unknown route renders the 404 status page`() = testApplication {
        application { configureStatusStaticRouter() }

        val response = createClient { followRedirects = false }
            .get("/this-route-does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("text/html;charset=utf-8", response.headers["Content-Type"])

        val body = response.bodyAsText()
        assertTrue(body.contains("404 — Not Found"), "404 page should contain heading; was: $body")
        assertTrue(body.contains("Back to worlds"), "404 page should contain CTA")
    }

    @Test
    fun `Throwing route renders the 500 status page without leaking the cause`() = testApplication {
        application { configureStatusStaticRouter() }
        routing {
            get("/throws") { throw SecretLeakingException("super-secret-internal-detail") }
        }

        val response = createClient { followRedirects = false }
            .get("/throws")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("text/html;charset=utf-8", response.headers["Content-Type"])

        val body = response.bodyAsText()
        assertTrue(body.contains("500 — Something Broke"), "500 page should contain heading; was: $body")
        assertFalse(
            body.contains("SecretLeakingException"),
            "500 page must not leak the exception class name",
        )
        assertFalse(
            body.contains("super-secret-internal-detail"),
            "500 page must not leak the exception message",
        )
    }

    @Test
    fun `the 500 page shows a reference the user can quote`() = testApplication {
        // MCO-350. The page told the reader "the error has been logged" and gave them nothing to
        // identify it by — so a report arrived as "it broke when I clicked the thing".
        application {
            configureMonitoring()
            configureStatusStaticRouter()
        }
        routing {
            get("/throws") { throw SecretLeakingException("super-secret-internal-detail") }
        }

        val response = createClient { followRedirects = false }.get("/throws")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        // CallId echoes the id it settled on, so the header is the id the log line also carries.
        val callId = response.headers[HttpHeaders.XRequestId]
        assertFalse(callId.isNullOrBlank(), "the response should carry a call id")
        assertTrue(body.contains(callId), "the 500 page should quote the call id $callId; was: $body")

        // Still no leak — the reference is opaque and generated, unlike the cause.
        assertFalse(body.contains("super-secret-internal-detail"), "must not leak the exception message")
    }

    private class SecretLeakingException(message: String) : RuntimeException(message)
}
