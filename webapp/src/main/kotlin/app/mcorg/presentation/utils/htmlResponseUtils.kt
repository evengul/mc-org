package app.mcorg.presentation.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.respondEmptyHtml() = respondHtml("")

suspend fun ApplicationCall.respondHtml(html: String, statusCode: HttpStatusCode = HttpStatusCode.OK) {
    isHtml()
    respond(status = statusCode, message = html)
}

private fun ApplicationCall.isHtml() {
    response.headers.append("Content-Type", "text/html;charset=utf-8")
}