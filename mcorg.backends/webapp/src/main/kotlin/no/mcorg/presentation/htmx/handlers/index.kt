package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.usersApi

suspend fun ApplicationCall.handleIndex() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()

    if (userId == null || !usersApi().userExists(userId)) {
        response.cookies.append("MCORG-USER-ID", "", httpOnly = true)
        respondRedirect("/signin")
    } else if (!permissionsApi().hasWorldPermission(userId)) {
        respondRedirect("/first-contact")
    } else respondRedirect("/worlds")
}