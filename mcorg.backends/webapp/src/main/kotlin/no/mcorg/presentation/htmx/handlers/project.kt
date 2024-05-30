package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
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

suspend fun ApplicationCall.handleCreateProject() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()

    if (userId == null) {
        respondRedirect("/signin")
    } else {
        val parts = receiveMultipart().readAllParts()
        val worldId = parameters["worldId"]?.toInt()
        val teamId = parameters["teamId"]?.toInt()
        val projectName = (parts.find { it.name == "project-name" } as PartData.FormItem?)?.value

        if (worldId == null || teamId == null || projectName == null || projectName.length < 3) {
            respond(HttpStatusCode.BadRequest)
        } else {
            projectsApi().createProject(worldId, teamId, projectName)
            respondRedirect("/worlds/$worldId/teams/$teamId")
        }
    }
}