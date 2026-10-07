package app.mcorg.presentation.plugins

import app.mcorg.logging.describeWithoutMessages
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.AssetBundle
import app.mcorg.presentation.templated.dsl.ScriptBundle
import app.mcorg.presentation.templated.dsl.StylesheetBundle
import app.mcorg.presentation.templated.error.notFoundPage
import app.mcorg.presentation.templated.error.serverErrorPage
import app.mcorg.presentation.templated.error.uploadTooLargePage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML
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
        // Thrown by RequestBodyLimit (limitSchematicUploads) for a declared or a streamed body over
        // the cap. Expected, and a client's doing rather than ours, so neither the catch-all's
        // error line nor its 500.
        exception<PayloadTooLargeException> { call, _ ->
            logger.info(
                "Rejected an upload to {} over the {} byte limit",
                call.request.path(),
                MAX_SCHEMATIC_UPLOAD_BYTES,
            )
            if (call.request.headers["HX-Request"] == "true") {
                // htmx swaps no error response (`noSwap`, Layout.kt), so this arrives out of band
                // onto the resource-upload form's slot, the way its 422s do. It replaces the slot,
                // so it keeps the slot's class and id, or the next message has nowhere to land.
                call.respondHtml(
                    createHTML().p("form-error") {
                        id = "validation-error-schematicFile"
                        hxOutOfBands("true")
                        +UPLOAD_TOO_LARGE_MESSAGE
                    },
                    HttpStatusCode.PayloadTooLarge,
                )
            } else {
                call.respondHtml(uploadTooLargePage(), HttpStatusCode.PayloadTooLarge)
            }
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            // An HTMX request keeps its own 404: the full page swapped into a fragment target puts
            // a whole <html> document inside the alert list (MCO-158). Returning without
            // responding sends the original body unchanged.
            if (call.request.headers["HX-Request"] == "true") return@status
            call.respondHtml(notFoundPage(), HttpStatusCode.NotFound)
        }
    }
    routing {
        assetBundles()
        staticResources("/static", "static")
    }
}

/**
 * `/static/seam.<version>.css` and `.js` — the whole of [StylesheetBundle] / [ScriptBundle],
 * whatever version the URL names. The version is a cache key, not a lookup: a client whose HTML
 * predates a deploy already holds its old file immutably, and `styleguide.html` links
 * `seam.latest.css` because a static file cannot know the hash. Caching is decided in `HTTP.kt`
 * from the same version.
 */
fun Route.assetBundles() {
    get("${AssetBundle.HREF_PREFIX}{version}.css") {
        call.respondText(StylesheetBundle.current().content, ContentType.Text.CSS)
    }
    get("${AssetBundle.HREF_PREFIX}{version}.js") {
        call.respondText(ScriptBundle.current().content, ContentType.Text.JavaScript)
    }
}
