package app.mcorg.presentation.htmx.routing

import app.mcorg.domain.User
import io.ktor.server.application.*
import io.ktor.server.response.*
import app.mcorg.presentation.security.getUserFromJwtToken
import io.ktor.http.*

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

suspend fun ApplicationCall.clientRedirect(path: String) {
    response.headers.append("HX-Redirect", path)
    respond(HttpStatusCode.OK)
}

suspend fun ApplicationCall.htmlBadRequest(html: String) {
    isHtml()
    respond(HttpStatusCode.BadRequest, html)
}

suspend fun ApplicationCall.htmlUnauthorized(html: String) {
    isHtml()
    respond(HttpStatusCode.Unauthorized, html)
}

suspend fun ApplicationCall.getUserIdOrRedirect(): Int? {
    val userId = getUserId()

    if (userId != null) {
        return userId
    }

    respondRedirect("/signin")

    return null
}

fun ApplicationCall.getUser(): User? {
    val token = request.cookies["MCORG-USER-TOKEN"] ?: return null

    return getUserFromJwtToken(token)
}

fun ApplicationCall.getUserId(): Int? {
    return getUser()?.id
}

