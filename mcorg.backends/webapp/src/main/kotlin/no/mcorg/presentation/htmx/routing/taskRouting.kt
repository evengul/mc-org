package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addCountableTask
import no.mcorg.presentation.htmx.templates.pages.addDoableTask

fun Application.taskRouting() {
    routing {
        post("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks") {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams(failOnMissingValue = true) ?: return@post

            call.handleCreateTask(worldId, teamId, projectId)
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/complete") {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@put

            call.handleCompleteTask(worldId, teamId, projectId, taskId)
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/incomplete") {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@put

            call.handleIncompleteTask(worldId, teamId, projectId, taskId)
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/update-countable") {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@put

            call.handleUpdateCountableTask(worldId, teamId, projectId, taskId)
        }

        delete("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}") {
            val (_, _, _, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@delete

            projectsApi().removeTask(taskId)

            call.respondEmpty()
        }

        get("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-countable") {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams() ?: return@get

            call.respondHtml(addCountableTask(worldId, teamId, projectId))
        }

        get("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-doable") {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams() ?: return@get
            call.respondHtml(addDoableTask(worldId, teamId, projectId))
        }
    }
}