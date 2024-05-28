package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.handlers.createWorld
import no.mcorg.presentation.htmx.handlers.handleFirstContact
import no.mcorg.presentation.htmx.handlers.handleIndex
import no.mcorg.presentation.htmx.handlers.handleSignin
import no.mcorg.presentation.htmx.templates.pages.worldsPage

fun Application.mainRouting() {
    routing {
        get("/") {
            call.handleIndex()
        }
        get("/first-contact") {
            call.handleFirstContact()
        }
        get("/signin") {
            call.handleSignin()
        }
        post("/first-contact") {
            call.createWorld()
        }

        get("/worlds") {
            call.isHtml()
            call.respond(worldsPage(permissionsApi().getWorldPermissions(1).permissions[PermissionLevel.WORLD]!!.map { it.first }))
        }
    }
}

fun ApplicationCall.isHtml() {
    response.headers.append("Content-Type", "text/html")
}

