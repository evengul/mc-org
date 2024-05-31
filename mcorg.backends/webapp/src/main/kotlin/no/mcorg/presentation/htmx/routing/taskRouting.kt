package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addCountableTask
import no.mcorg.presentation.htmx.templates.pages.addDoableTask

fun Application.taskRouting() {
    routing {
        postAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams(failOnMissingValue = true) ?: return@postAuthed

            call.handleCreateTask(worldId, teamId, projectId)
        }

        putAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/complete", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@putAuthed

            call.handleCompleteTask(worldId, teamId, projectId, taskId)
        }

        putAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/incomplete", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@putAuthed

            call.handleIncompleteTask(worldId, teamId, projectId, taskId)
        }

        putAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/update-countable", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@putAuthed

            call.handleUpdateCountableTask(worldId, teamId, projectId, taskId)
        }

        deleteAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (_, _, _, taskId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@deleteAuthed

            projectsApi().removeTask(taskId)

            call.respondEmpty()
        }

        getAuthed("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-countable", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams() ?: return@getAuthed

            call.respondHtml(addCountableTask(worldId, teamId, projectId))
        }

        getAuthed("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-doable", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams() ?: return@getAuthed
            call.respondHtml(addDoableTask(worldId, teamId, projectId))
        }
    }
}