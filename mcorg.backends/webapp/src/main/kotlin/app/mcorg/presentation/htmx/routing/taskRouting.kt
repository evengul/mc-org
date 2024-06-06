package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.htmx.handlers.*

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

        postAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/material-list", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (worldId, teamId, projectId) = call.getWorldTeamProjectParams(failOnMissingValue = true) ?: return@postAuthed

            call.handleUploadMaterialList(worldId, teamId, projectId)
        }
    }
}