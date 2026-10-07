package app.mcorg.presentation.utils

import io.ktor.server.application.*
import io.ktor.server.request.*
import java.net.URI

fun ApplicationCall.getCurrentUrl() = request.headers["HX-Current-URL"]

fun ApplicationCall.isHtmxRequest() = request.headers["HX-Request"] == "true"

/**
 * The path and query of the page the user is looking at.
 *
 * For an HTMX request that is not the request's own URI, which names a fragment endpoint (often
 * POST-only) that cannot be navigated back to. htmx sends the page's address as `HX-Current-URL`.
 * Anything unparseable falls back to the request URI; callers still pass the result through
 * `safeRedirectPath` before redirecting to it.
 *
 * An `hx-push-url` click is the one case where the request URI was the better answer: it is the
 * page the user asked for, and this returns the one they were leaving. Nothing in the request
 * says a push is coming, and the page they were on is always one they can load again.
 */
fun ApplicationCall.pageUri(): String {
    if (!isHtmxRequest()) return request.uri
    val current = getCurrentUrl()?.let { runCatching { URI(it) }.getOrNull() } ?: return request.uri
    val path = current.rawPath?.takeIf { it.startsWith("/") } ?: return request.uri
    return current.rawQuery?.let { "$path?$it" } ?: path
}
