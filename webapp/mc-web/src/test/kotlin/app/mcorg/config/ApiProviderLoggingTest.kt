package app.mcorg.config

import app.mcorg.pipeline.Result
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-336 (B1). The OAuth token exchanges all route through [ApiProvider.deserializeJson], so a
 * failure there must not put the response body into the log.
 *
 * These tests capture real logback output rather than inspecting the code, because the leak was
 * never in what we wrote — it was in what `e.message` expands to underneath.
 */
class ApiProviderLoggingTest {

    @Serializable
    data class TokenShaped(val id: Int, val name: String)

    /** A payload shaped like a Microsoft token response, truncated so parsing fails. */
    private val secret = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.SUPER_SECRET_ACCESS_TOKEN"
    private val malformedTokenBody = """{"access_token": "$secret", "token_type": """

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger(FakeApiProvider::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        logger.level = Level.TRACE
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    /** Everything the logger emitted, message + any attached throwable, flattened to one string. */
    private fun capturedText(): String = appender.list.joinToString("\n") { event ->
        buildString {
            append(event.formattedMessage)
            var t = event.throwableProxy
            while (t != null) {
                append('\n').append(t.className).append(": ").append(t.message)
                t = t.cause
            }
        }
    }

    @Test
    fun `the danger is real - kotlinx puts the payload in the exception message`() {
        // Guard test. If kotlinx-serialization ever stops attaching input to the message, or the
        // app disables exceptionsWithDebugInfo, this fails and the elaborate care in
        // logDeserializationFailure can be reconsidered. Until then it documents *why* the
        // exception must never reach a log call.
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val message = runCatching { json.decodeFromString<TokenShaped>(malformedTokenBody) }
            .exceptionOrNull()!!
            .message.orEmpty()

        assertTrue(
            message.contains("JSON input") && message.contains("SUPER_SECRET_ACCESS_TOKEN"),
            "expected kotlinx to leak the payload into the message, got: $message",
        )
    }

    @Test
    fun `a failed token deserialization logs neither the payload nor the JSON input marker`() {
        val provider = FakeApiProvider(TestApiConfig()) { _, _ -> Result.success("") }

        provider.deserializeJson<TokenShaped>(malformedTokenBody, "https://login.microsoftonline.com/token")

        val logged = capturedText()
        assertTrue(logged.isNotEmpty(), "expected the failure to be logged at all")
        assertFalse(logged.contains("SUPER_SECRET_ACCESS_TOKEN"), "token leaked into the log:\n$logged")
        assertFalse(logged.contains("eyJ0eXAi"), "JWT prefix leaked into the log:\n$logged")
        assertFalse(logged.contains("JSON input"), "kotlinx debug payload leaked into the log:\n$logged")
        assertFalse(logged.contains("access_token"), "response field names leaked into the log:\n$logged")
    }

    @Test
    fun `the log line still says what failed and where`() {
        // Redaction is only useful if the line remains actionable.
        val provider = FakeApiProvider(TestApiConfig()) { _, _ -> Result.success("") }

        provider.deserializeJson<TokenShaped>(malformedTokenBody, "https://login.microsoftonline.com/token")

        val logged = capturedText()
        assertTrue(logged.contains("TokenShaped"), "target type missing from:\n$logged")
        assertTrue(logged.contains("login.microsoftonline.com"), "url missing from:\n$logged")
    }

    @Test
    fun `a missing-field failure is also redacted`() {
        // Well-formed JSON with the wrong shape throws MissingFieldException, which does not carry
        // the input — but the field names still describe the payload, so nothing is logged either.
        val provider = FakeApiProvider(TestApiConfig()) { _, _ -> Result.success("") }

        provider.deserializeJson<TokenShaped>("""{"access_token": "$secret"}""", "https://xsts.auth.xboxlive.com")

        val logged = capturedText()
        assertFalse(logged.contains("SUPER_SECRET_ACCESS_TOKEN"), "token leaked into the log:\n$logged")
        assertFalse(logged.contains("access_token"), "field names leaked into the log:\n$logged")
    }

    @Test
    fun `deserializing without a url still logs safely`() {
        // The url parameter is optional; the no-url path must not produce "null" noise or leak.
        val provider = FakeApiProvider(TestApiConfig()) { _, _ -> Result.success("") }

        provider.deserializeJson<TokenShaped>(malformedTokenBody)

        val logged = capturedText()
        assertFalse(logged.contains("SUPER_SECRET_ACCESS_TOKEN"), "token leaked into the log:\n$logged")
        assertFalse(logged.contains("null"), "unset url rendered as 'null' in:\n$logged")
    }
}
