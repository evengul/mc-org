package app.mcorg.presentation.utils

import io.ktor.server.application.*
import io.ktor.server.request.*
import java.net.URI

fun ApplicationCall.getCurrentUrl() = request.headers["HX-Current-URL"]

fun ApplicationCall.isHtmxRequest() = request.headers["HX-Request"] == "true"

/**
 * The path of the page the user is looking at.
 *
 * For an HTMX request that is not the request's own path, which names a fragment endpoint (often
 * POST-only) that cannot be navigated back to. htmx sends the page's address as `HX-Current-URL`.
 * Anything unparseable falls back to the request path; callers still pass the result through
 * `safeRedirectPath` before redirecting to it.
 */
fun ApplicationCall.pagePath(): String {
    if (!isHtmxRequest()) return request.path()
    val currentPath = getCurrentUrl()?.let { runCatching { URI(it).rawPath }.getOrNull() }
    return currentPath?.takeIf { it.startsWith("/") } ?: request.path()
}
