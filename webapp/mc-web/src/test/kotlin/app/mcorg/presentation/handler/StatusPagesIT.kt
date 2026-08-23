package app.mcorg.presentation.handler

import app.mcorg.presentation.plugins.configureMonitoring
import app.mcorg.presentation.plugins.configureStatusStaticRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import org.slf4j.LoggerFactory
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

    @Test
    fun `the uncaught-exception log line carries no exception message`() = testApplication {
        // The gap MCO-350 left. It hardened defaultHandleError — the boundary whose failures carry
        // no exception — and left this one, which catches every uncaught Throwable and used to
        // pass it to Ktor's logError(). slf4j renders a stack trace starting with getMessage(), so
        // per documentation/logging.md that leaks as much as interpolating the message: a pgjdbc
        // exception escaping DatabaseSteps carries `DETAIL: Key (column)=(value)`, and a
        // kotlinx-serialization failure escaping ApiProvider carries the whole JSON input.
        //
        // The sibling test above asserts the rendered *page* is clean and always did. Nothing
        // looked at the log, which is where the secret actually went.
        val logger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            application {
                configureMonitoring()
                configureStatusStaticRouter()
            }
            routing {
                get("/throws") { throw SecretLeakingException("super-secret-internal-detail") }
            }

            createClient { followRedirects = false }.get("/throws")

            assertEquals(1, appender.list.size, "the boundary should log exactly one line")
            val rendered = appender.list.single().let { event ->
                // Everything slf4j would print: the formatted message plus any attached throwable.
                event.formattedMessage + (event.throwableProxy?.let { "\n$it" } ?: "")
            }

            assertFalse(
                rendered.contains("super-secret-internal-detail"),
                "the log line must not carry the exception message; was: $rendered",
            )
            // Still useful: the type and the route survive, which is what you debug from.
            assertTrue(rendered.contains("SecretLeakingException"), "should name the exception type; was: $rendered")
            assertTrue(rendered.contains("/throws"), "should name the request path; was: $rendered")
        } finally {
            logger.detachAppender(appender)
        }
    }

    private class SecretLeakingException(message: String) : RuntimeException(message)
}
