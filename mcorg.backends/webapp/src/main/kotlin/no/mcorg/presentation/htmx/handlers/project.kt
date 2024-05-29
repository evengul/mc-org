package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.projectPage

suspend fun ApplicationCall.handleProject(projectId: Int) {

    val project = projectsApi().getProject(projectId)
        ?: return respondRedirect("/")

    isHtml()
    respond(projectPage(project))
}