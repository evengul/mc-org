package app.mcorg.presentation.plugins

import app.mcorg.logging.describeWithoutMessages
import app.mcorg.presentation.templated.error.notFoundPage
import app.mcorg.presentation.templated.error.serverErrorPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary")

fun Application.configureStatusStaticRouter() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Deliberately NOT Ktor's logError(call, cause), which hands the raw throwable to
            // slf4j and so renders getMessage() first. This is the boundary that catches
            // *everything*, including the two libraries documentation/logging.md names as putting
            // payloads in their messages — a pgjdbc exception escaping DatabaseSteps' own catch
            // carries `DETAIL: Key (column)=(value)`, and a kotlinx-serialization failure escaping
            // ApiProvider's carries the entire JSON input.
            //
            // Path, not uri: the query string is out of bounds per the same document.
            logger.error(
                "Unhandled exception at {} {} (call {}): {}",
                call.request.httpMethod.value,
                call.request.path(),
                call.callId ?: "unknown",
                cause.describeWithoutMessages(),
            )
            // Same id the log line carries, so a user quoting it points straight at the entry
            // above (MCO-350).
            call.respondHtml(serverErrorPage(call.callId), HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondHtml(notFoundPage(), HttpStatusCode.NotFound)
        }
    }
    routing {
        staticResources("/static", "static")
    }
}
