package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.createWorld
import no.mcorg.presentation.htmx.handlers.handleFirstContact
import no.mcorg.presentation.htmx.handlers.handleIndex
import no.mcorg.presentation.htmx.templates.pages.firstWorldTeam

fun Application.firstContactRouting() {
    routing {
        get("/") {
            call.handleIndex()
        }
        get("/first-contact") {
            call.handleFirstContact()
        }
        get("/first-world-team") {
            call.isHtml()

            if (call.request.queryParameters["is-multiplayer"] == "on") {
                call.respond(firstWorldTeam())
            } else {
                call.respond("")
            }
        }
    }
}