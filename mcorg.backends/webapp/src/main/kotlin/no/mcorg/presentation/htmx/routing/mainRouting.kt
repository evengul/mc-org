package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*

fun Application.router() {
    authRouting()
    firstContactRouting()
    projectRouting()
    resourcePackRouting()
    taskRouting()
    teamRouting()
    worldRouting()
}

fun ApplicationCall.isHtml() {
    response.headers.append("Content-Type", "text/html")
}

