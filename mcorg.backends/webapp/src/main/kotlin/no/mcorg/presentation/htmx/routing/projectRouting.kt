package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addProject

fun Application.projectRouting() {
    routing {
        get("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val (_, _, projectId) = call.getWorldTeamProjectParams() ?: return@get

            call.respondProject(projectId)
        }

        post("/worlds/{worldId}/teams/{teamId}/projects") {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@post
            call.handleCreateProject(worldId, teamId)
        }

        delete("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val (_, _, projectId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@delete

            projectsApi().deleteProject(projectId)
            call.respondEmpty()
        }

        get("/htmx/world/{worldId}/teams/{teamId}/projects/add") {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@get

            call.respondHtml(addProject(worldId, teamId))
        }
    }
}