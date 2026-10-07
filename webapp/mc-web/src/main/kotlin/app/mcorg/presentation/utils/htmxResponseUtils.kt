package app.mcorg.presentation.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.clientRedirect(path: String) {
    response.headers.append("HX-Redirect", path)
    respond(HttpStatusCode.OK)
}

/**
 * Redirect that survives an ordinary link click.
 *
 * [clientRedirect] answers `200` with an empty body and an `HX-Redirect` header, which only a
 * running HTMX request knows how to follow. Reached by a plain `<a href>` it renders a blank page —
 * exactly what a first-time user got from "New Idea" before MCO-310. Use this wherever the caller
 * might be either.
 */
suspend fun ApplicationCall.redirectClientOrBrowser(path: String) {
    if (isHtmxRequest()) {
        clientRedirect(path)
    } else {
        respondRedirect(path)
    }
}

/**
 * Retarget a *successful* response. htmx swaps no error response (`noSwap`, `Layout.kt`), and that
 * overrides these headers, so an error is routed with `respondRefusal` / `respondInPlace` /
 * a validation failure instead (`ErrorHandler.kt`).
 */
fun ApplicationCall.hxTarget(value: String) {
    response.headers.append("HX-Retarget", value)
}

fun ApplicationCall.hxSwap(value: String) {
    response.headers.append("HX-Reswap", value)
}