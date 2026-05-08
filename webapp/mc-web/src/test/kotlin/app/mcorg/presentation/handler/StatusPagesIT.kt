package app.mcorg.presentation.handler

import app.mcorg.presentation.plugins.configureStatusStaticRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
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

    private class SecretLeakingException(message: String) : RuntimeException(message)
}
