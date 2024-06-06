package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.htmx.handlers.*

fun Application.projectRouting() {
    routing {
        getAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (_, _, projectId) = call.getWorldTeamProjectParams() ?: return@getAuthed

            call.respondProject(projectId)
        }

        postAuthed("/worlds/{worldId}/teams/{teamId}/projects", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@postAuthed
            call.handleCreateProject(worldId, teamId)
        }

        deleteAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (_, _, projectId) = call.getWorldTeamProjectTaskParams(failOnMissingValue = true) ?: return@deleteAuthed

            projectsApi().deleteProject(projectId)
            call.respondEmpty()
        }
    }
}