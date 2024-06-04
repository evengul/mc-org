package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.htmx.handlers.respondFirstContact
import app.mcorg.presentation.htmx.handlers.respondIndex
import app.mcorg.presentation.htmx.templates.pages.firstWorldTeam

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