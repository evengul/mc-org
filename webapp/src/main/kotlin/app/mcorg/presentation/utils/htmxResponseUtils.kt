package app.mcorg.presentation.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.clientRedirect(path: String) {
    response.headers.append("HX-Redirect", path)
    respond(HttpStatusCode.OK)
}

suspend fun ApplicationCall.badRequest(errorHtml: String = "An error occurred",
                                       target: String = "#error-message",
                                       swap: String = "innerHTML") {
    response.headers.append("HX-ReTarget", target)
    response.headers.append("HX-ReSwap", swap)
    respondHtml(errorHtml, HttpStatusCode.BadRequest)
}

fun ApplicationCall.hxTarget(value: String) {
    response.headers.append("HX-Retarget", value)
}

fun ApplicationCall.hxSwap(value: String) {
    response.headers.append("HX-Reswap", value)
}