package app.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.htmx.routing.getUserId
import app.mcorg.presentation.htmx.routing.respondHtml
import app.mcorg.presentation.htmx.templates.pages.firstContact

suspend fun ApplicationCall.respondFirstContact() {
    val userId = getUserId()

    if (userId == null) {
        respondRedirect("/signin")
    } else {
        if (permissionsApi().hasAnyWorldPermission(userId)) {
            respondRedirect("/")
        } else {
            respondHtml(firstContact())
        }
    }
}

suspend fun ApplicationCall.respondIndex() {
    val userId = getUserId()

    if (userId == null || !usersApi().userExists(userId)) {
        signOut()
        respondRedirect("/signin")
    } else if (!permissionsApi().hasAnyWorldPermission(userId)) {
        respondRedirect("/first-contact")
    } else respondRedirect("/worlds")
}