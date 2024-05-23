package no.skyteruta.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.skyteruta.templates.pages.firstWorldCreated
import no.skyteruta.templates.pages.firstWorldTeam
import no.skyteruta.templates.pages.landingPage

fun Application.configureTemplating() {
    routing {
        get("/") {
            call.response.header("Content-Type", "text/html")
            call.respond(landingPage())
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
