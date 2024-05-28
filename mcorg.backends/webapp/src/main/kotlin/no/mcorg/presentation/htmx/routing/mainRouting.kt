package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.createWorld
import no.mcorg.presentation.htmx.handlers.handleIndex
import no.mcorg.presentation.htmx.handlers.handleSignin
import no.mcorg.presentation.htmx.templates.pages.firstContact

fun Application.mainRouting() {
    routing {
        get("/") {
            call.handleIndex()
        }
        get("/first-contact") {
            call.response.headers.append("Content-Type", "text/html")
            call.respond(firstContact())
        }
        get("/signin") {
            call.handleSignin()
        }
        post("/first-contact") {
            call.createWorld()
        }
    }
}