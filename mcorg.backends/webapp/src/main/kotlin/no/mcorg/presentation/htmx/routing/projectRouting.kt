package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addProject

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

        getAuthed("/htmx/world/{worldId}/teams/{teamId}/projects/add", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@getAuthed

            call.respondHtml(addProject(worldId, teamId))
        }
    }
}