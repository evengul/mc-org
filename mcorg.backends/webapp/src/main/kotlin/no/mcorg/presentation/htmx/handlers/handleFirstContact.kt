package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.usersApi
import no.mcorg.presentation.htmx.routing.getUserId
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.pages.firstContact

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