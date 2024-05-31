package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.respondFirstContact
import no.mcorg.presentation.htmx.handlers.respondIndex
import no.mcorg.presentation.htmx.templates.pages.firstWorldTeam

fun Application.firstContactRouting() {
    routing {
        get("/") {
            call.respondIndex()
        }
        get("/first-contact") {
            call.respondFirstContact()
        }
        get("/htmx/first-world-team") {
            if (call.request.queryParameters["is-multiplayer"] == "on") {
                call.respondHtml(firstWorldTeam())
            } else {
                call.respondEmpty()
            }
        }
    }
}