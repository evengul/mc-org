package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.router() {
    authRouting()
    firstContactRouting()
    projectRouting()
    resourcePackRouting()
    taskRouting()
    teamRouting()
    worldRouting()
}

suspend fun ApplicationCall.respondEmpty() {
    isHtml()
    respond("")
}

suspend fun ApplicationCall.respondHtml(html: String) {
    isHtml()
    respond(html)
}

private fun ApplicationCall.isHtml() {
    response.headers.append("Content-Type", "text/html")
}

suspend fun ApplicationCall.getUserIdOrRedirect(): Int? {
    val userId = getUserId()

    if (userId != null) {
        return userId
    }

    respondRedirect("/signin")

    return null
}

fun ApplicationCall.getUserId(): Int? {
    return request.cookies["MCORG-USER-ID"]?.toIntOrNull()
}

