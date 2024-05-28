package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.firstContact

suspend fun ApplicationCall.handleFirstContact() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull();

    if (userId == null) {
        respondRedirect("/signin")
    } else {
        if (permissionsApi().hasWorldPermission(userId)) {
            respondRedirect("/")
        } else {
            isHtml()
            respond(firstContact())
        }
    }
}