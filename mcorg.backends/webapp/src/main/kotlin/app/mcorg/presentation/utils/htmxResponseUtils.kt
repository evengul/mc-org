package app.mcorg.presentation.router.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.clientRedirect(path: String) {
    response.headers.append("HX-Redirect", path)
    respond(HttpStatusCode.OK)
}

suspend fun ApplicationCall.clientRefresh() {
    response.headers.append("HX-Refresh", "true")
    respond(HttpStatusCode.OK)
}