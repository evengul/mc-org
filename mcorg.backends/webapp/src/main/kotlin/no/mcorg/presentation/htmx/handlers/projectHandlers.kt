package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.routing.getUserIdOrRedirect
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.pages.projectPage

suspend fun ApplicationCall.respondProject(projectId: Int) {

    val project = projectsApi().getProject(projectId, includeTasks = true)
        ?: return respondRedirect("/")

    respondHtml(projectPage(project))
}

suspend fun ApplicationCall.handleCreateProject(worldId: Int, teamId: Int) {
    getUserIdOrRedirect() ?: return

    val parts = receiveMultipart().readAllParts()
    val projectName = (parts.find { it.name == "project-name" } as PartData.FormItem?)?.value

    if (projectName == null || projectName.length < 3) {
        respond(HttpStatusCode.BadRequest)
    } else {
        projectsApi().createProject(worldId, teamId, projectName)
        respondRedirect("/worlds/$worldId/teams/$teamId")
    }
}