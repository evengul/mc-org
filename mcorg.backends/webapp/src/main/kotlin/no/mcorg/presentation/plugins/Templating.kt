package no.mcorg.presentation.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.clients.getWorlds
import no.mcorg.presentation.templates.pages.firstWorldCreated
import no.mcorg.presentation.templates.pages.firstWorldTeam
import no.mcorg.presentation.templates.pages.landingPage

fun Application.configureTemplating() {
    routing {
        get("/") {
            call.response.header("Content-Type", "text/html")

            val worlds = getWorlds()

            call.respond(landingPage(worlds))
        }
        post("/") {
            call.response.header("Content-Type", "text/html")
            call.respond(firstWorldCreated())
        }
        get("/first-world-team") {
            call.response.header("Content-Type", "text/html")

            if (call.request.queryParameters["is-multiplayer"] == "on") {
                call.respond(firstWorldTeam())
            } else {
                call.respond("")
            }
        }
    }
}
