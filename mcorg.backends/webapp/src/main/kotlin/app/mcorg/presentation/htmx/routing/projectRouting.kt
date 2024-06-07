package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.htmx.handlers.*
import io.ktor.server.request.*

fun Application.projectRouting() {
    routing {
        getAuthed("/worlds/{worldId}/teams/{teamId}/projects/{projectId}", permissionLevel = PermissionLevel.PROJECT, authority = Authority.PARTICIPANT) {
            val (_, _, projectId) = call.getWorldTeamProjectParams() ?: return@getAuthed

            val tab = call.request.queryParameters["tab"]?.takeIf { it == "countable-tasks" || it == "doable-tasks"  } ?:
                return@getAuthed call.respondRedirect(call.request.uri + "?tab=doable-tasks", permanent = true)

            call.respondProject(projectId, tab)
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