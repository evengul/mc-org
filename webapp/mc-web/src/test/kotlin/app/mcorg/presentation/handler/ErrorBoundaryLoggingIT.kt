package app.mcorg.presentation.handler

import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.plugins.configureMonitoring
import app.mcorg.test.postgres.DatabaseTestExtension
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-350 — the error boundary used to be silent.
 *
 * `defaultHandleError` handles every `AppFailure` from every `handlePipeline` call and never
 * touched a logger. Failures constructed without an exception left no trace at all, while the
 * user was told "the error has been logged".
 *
 * These assert against captured logback output and rendered HTML rather than reading the code,
 * because "it logs" and "the line says something useful and safe" are different claims.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class ErrorBoundaryLoggingIT {

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary") as Logger
        appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        logger.level = Level.TRACE
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    private fun events() = appender.list

    @Test
    fun `a failure with no exception still produces exactly one log line`() = testApplication {
        // The shape that used to vanish entirely: a failure built from a data object, carrying no
        // throwable for anything upstream to have logged.
        application { configureMonitoring() }
        routing {
            get("/boom") { call.defaultHandleError(AppFailure.DatabaseError.ConnectionError) }
        }

        client.get("/boom")

        assertEquals(1, events().size, "expected exactly one line, got: ${events().map { it.formattedMessage }}")
        val line = events().single()
        assertEquals(Level.ERROR, line.level)
        assertTrue("ConnectionError" in line.formattedMessage, "should name the failure: ${line.formattedMessage}")
        assertTrue("GET" in line.formattedMessage, "should name the method: ${line.formattedMessage}")
        assertTrue("/boom" in line.formattedMessage, "should name the path: ${line.formattedMessage}")
    }

    @Test
    fun `the log line carries the call id in MDC`() = testApplication {
        application { configureMonitoring() }
        routing {
            get("/boom") { call.defaultHandleError(AppFailure.DatabaseError.ConnectionError) }
        }

        client.get("/boom")

        // %X{call-id} rendered as an empty gap on every production line before the generate {}
        // block landed. Without it the id on the user's error page matches nothing.
        val callId = events().single().mdcPropertyMap["call-id"]
        assertFalse(callId.isNullOrBlank(), "call-id should be present in MDC, was: $callId")
    }

    @Test
    fun `the query string never reaches the log line`() = testApplication {
        application { configureMonitoring() }
        routing {
            get("/boom") { call.defaultHandleError(AppFailure.DatabaseError.ConnectionError) }
        }

        client.get("/boom?token=super-secret-value")

        // request.path(), not request.uri — the distinction documentation/logging.md exists for.
        val text = events().joinToString("\n") { it.formattedMessage }
        assertFalse("super-secret-value" in text, "the query string must not be logged: $text")
        assertFalse("token=" in text, "the query string must not be logged: $text")
    }

    @Test
    fun `ordinary validation failures do not reach the error log`() = testApplication {
        application { configureMonitoring() }
        routing {
            get("/invalid") {
                call.defaultHandleError(AppFailure.customValidationError("name", "Name is required"))
            }
        }

        client.get("/invalid")

        // A user mistyping a form is not an error. Logging it would bury the ones that are.
        assertTrue(events().isEmpty(), "validation failures should be silent, got: ${events().map { it.formattedMessage }}")
    }

    @Test
    fun `a missing resource is logged as info and reads as not found`() = testApplication {
        application { configureMonitoring() }
        routing {
            get("/gone") { call.defaultHandleError(AppFailure.DatabaseError.NotFound) }
        }

        val response = client.get("/gone")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(Level.INFO, events().single().level, "a deleted world is not a crash")

        // The copy used to say "an unexpected error occurred" on a 404.
        val body = response.bodyAsText()
        assertTrue("Not found" in body, "should read as not found; was: $body")
        assertFalse("unexpected error" in body, "a 404 should not claim something broke; was: $body")
    }

    @Test
    fun `the user is shown a reference they can quote`() = testApplication {
        application { configureMonitoring() }
        routing {
            get("/boom") { call.defaultHandleError(AppFailure.DatabaseError.ConnectionError) }
        }

        val body = client.get("/boom").bodyAsText()
        val loggedCallId = events().single().mdcPropertyMap["call-id"]!!

        // The id on the page and the id on the log line have to be the same one, or quoting it
        // achieves nothing.
        assertTrue(
            loggedCallId in body,
            "the rendered error should carry the same call id as the log line ($loggedCallId); was: $body",
        )
    }
}
