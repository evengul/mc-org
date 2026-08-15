package app.mcorg.presentation.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.*
import java.util.UUID

/**
 * Upper bound on an accepted `X-Request-Id`. Long enough for a UUID or a typical upstream trace id,
 * short enough that nobody can push a paragraph into an indexed log field.
 */
const val MAX_CALL_ID_LENGTH = 64

/**
 * Characters an inbound call id may contain: what UUIDs, ULIDs and common trace ids use, and
 * nothing that could break a log line or a query. Notably excludes whitespace, quotes and control
 * characters.
 */
private val CALL_ID_PATTERN = Regex("^[A-Za-z0-9+/=_-]+$")

/**
 * True when an inbound `X-Request-Id` is safe to adopt as this call's id (MCO-341).
 *
 * The id lands in MDC and is rendered on every log line for the call, so it is caller-controlled
 * text in a field that will be indexed and queried once logs ship. The previous check was
 * `callId.isNotEmpty()`, which accepted newlines (log forging — a caller could inject what looks
 * like a second log line), megabyte strings (cost and cardinality), and anything else at all.
 *
 * Rejected ids are not an error: [CallId] falls through to the `generate` block below, so the call
 * still gets an id, just not the caller's.
 */
internal fun isAcceptableCallId(callId: String): Boolean =
    callId.isNotEmpty() && callId.length <= MAX_CALL_ID_LENGTH && CALL_ID_PATTERN.matches(callId)

/**
 * MDC keys allowed to reach the log encoder.
 *
 * Today's pattern renders only `%X{call-id}`, so anything else put in MDC is silently dropped —
 * `GetServerFilesPipeline` already puts `minecraftVersion` there and it never appears. That
 * changes the moment a JSON encoder is configured for Axiom (MCO-343): a JSON encoder emits the
 * *entire* MDC map. Establishing the allowlist now means that switch cannot quietly start
 * publishing whatever some pipeline happened to stash.
 *
 * Adding a key here is a decision to publish it — check it against documentation/logging.md first.
 */
val ALLOWED_MDC_KEYS: Set<String> = setOf("call-id")

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        callIdMdc("call-id")
        // Ktor's defaultFormat wraps status and method in jansi colour codes. Fine on a TTY,
        // just escape-sequence noise in a structured log platform (MCO-341).
        disableDefaultColors()
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String -> isAcceptableCallId(callId) }
        // Every call gets an id even when the caller supplied none, or supplied one we rejected.
        // MCO-350 wants this same id surfaced to the user on an error page so they can quote it —
        // keep it generated in one place.
        generate { UUID.randomUUID().toString() }
    }
}
