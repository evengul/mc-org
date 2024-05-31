package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.handleCreateProject
import no.mcorg.presentation.htmx.handlers.handleProject
import no.mcorg.presentation.htmx.templates.pages.addProject

fun Application.projectRouting() {
    routing {
        get("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val worldId = call.parameters["worldId"]?.toIntOrNull() ?: return@get call.respondRedirect("/worlds")
            val teamId = call.parameters["teamId"]?.toIntOrNull() ?: return@get call.respondRedirect("/worlds/$worldId")
            val projectId = call.parameters["projectId"]?.toIntOrNull() ?: return@get call.respondRedirect("/worlds/$worldId/teams/$teamId")

            call.handleProject(projectId)
        }

        post("/worlds/{worldId}/teams/{teamId}/projects") {
            call.handleCreateProject()
        }

        delete("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val worldId = call.parameters["worldId"]?.toInt()
            val teamId = call.parameters["teamId"]?.toInt()
            val projectId = call.parameters["projectId"]?.toInt()

            if (worldId == null || teamId == null || projectId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                projectsApi().deleteProject(projectId)
                call.isHtml()
                call.respond("")
            }
        }

        get("/htmx/world/{worldId}/teams/{teamId}/projects/add") {
            val worldId = call.parameters["worldId"]?.toInt()
            val teamId = call.parameters["teamId"]?.toInt()
            if (worldId == null || teamId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.isHtml()
                call.respond(addProject(worldId, teamId))
            }
        }
    }
}