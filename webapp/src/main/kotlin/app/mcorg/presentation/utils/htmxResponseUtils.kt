package app.mcorg.presentation.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.clientRedirect(path: String) {
    response.headers.append("HX-Redirect", path)
    respond(HttpStatusCode.OK)
}

@Suppress("unused")
suspend fun ApplicationCall.clientRefresh() {
    response.headers.append("HX-Refresh", "true")
    respond(HttpStatusCode.OK)
}

fun ApplicationCall.hxTarget(value: String) {
    response.headers.append("HX-Retarget", value)
}

fun ApplicationCall.hxSwap(value: String) {
    response.headers.append("HX-Reswap", value)
}