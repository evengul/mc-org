package app.mcorg.presentation.handler

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("app.mcorg.presentation.Readiness")

/**
 * Reports whether this instance can reach its dependencies (MCO-349).
 *
 * Distinct from `/test/ping`, which only proves the JVM is serving HTTP. Before this, `/test/ping`
 * was the *only* health surface and it returned `OK` unconditionally — so a machine with a dead
 * Neon connection or a half-applied migration reported healthy while every page 500ed.
 *
 * Deliberately **not** wired to Fly's periodic check: a check that touches the database on any
 * short interval keeps Neon's compute from ever suspending. Call this on deploy, from an alert, or
 * by hand when diagnosing.
 *
 * Goes through [DatabaseSteps] rather than raw JDBC so it exercises the same pool, the same
 * timeouts and the same failure mapping the application uses — a probe down a private path can
 * pass while the real one is broken.
 *
 * Returns `text/plain` rather than the usual HTML fragment: the callers are machines, and the HTML
 * rule exists for HTMX responses. Not JSON either, so it stays outside that rule's spirit too.
 */
suspend fun ApplicationCall.handleReadinessProbe() {
    val result = DatabaseSteps.query<Unit, Int>(
        sql = SafeSQL.select("SELECT 1"),
        resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 },
    ).process(Unit)

    when {
        result is Result.Success && result.value == 1 -> respondText("READY", status = HttpStatusCode.OK)
        else -> {
            // The failure type only; DatabaseSteps has already logged the SQLState, and its
            // message can carry row values (documentation/logging.md).
            val failure = (result as? Result.Failure)?.error
            logger.error("Readiness probe failed: the database is not reachable ({})", failure)
            respondText("NOT READY", status = HttpStatusCode.ServiceUnavailable)
        }
    }
}
