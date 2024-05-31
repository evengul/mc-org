package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.htmx.handlers.respondFirstContact
import no.mcorg.presentation.htmx.handlers.respondIndex
import no.mcorg.presentation.htmx.templates.pages.firstWorldTeam

fun Application.firstContactRouting() {
    routing {
        getAuthed("/", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.respondIndex()
        }
        getAuthed("/first-contact", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.respondFirstContact()
        }
        getAuthed("/htmx/first-world-team", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            if (call.request.queryParameters["is-multiplayer"] == "on") {
                call.respondHtml(firstWorldTeam())
            } else {
                call.respondEmpty()
            }
        }
    }
}