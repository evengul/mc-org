package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.permissionsApi

suspend fun ApplicationCall.handleIndex() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull() ?:
        return respondRedirect("/signin")

    response.headers.append("Content-Type", "text/html")

    if (!permissionsApi().hasWorldPermission(userId)) {
        respondRedirect("/first-contact")
    }

    respondRedirect("/worlds")
}