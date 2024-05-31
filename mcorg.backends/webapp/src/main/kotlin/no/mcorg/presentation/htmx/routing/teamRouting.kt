package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.htmx.handlers.getWorldParam
import no.mcorg.presentation.htmx.handlers.getWorldTeamParams
import no.mcorg.presentation.htmx.handlers.handleCreateTeam
import no.mcorg.presentation.htmx.handlers.respondTeam
import no.mcorg.presentation.htmx.templates.pages.addTeam

fun Application.teamRouting() {
    routing {
        getAuthed("/worlds/{worldId}/teams/{teamId}", permissionLevel = PermissionLevel.TEAM, authority = Authority.PARTICIPANT) {
            val (worldId, teamId) = call.getWorldTeamParams() ?: return@getAuthed

            call.respondTeam(worldId, teamId)
        }

        postAuthed("/worlds/{worldId}/teams", permissionLevel = PermissionLevel.WORLD, authority = Authority.ADMIN) {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@postAuthed
            call.handleCreateTeam(worldId)
        }

        deleteAuthed("/worlds/{worldId}/teams/{teamId}", permissionLevel = PermissionLevel.TEAM, authority = Authority.OWNER) {
            val (_, teamId) = call.getWorldTeamParams() ?: return@deleteAuthed

            teamsApi().deleteTeam(teamId)
            call.respondEmpty()
        }

        getAuthed("/htmx/worlds/{worldId}/teams/add", permissionLevel = PermissionLevel.WORLD, authority = Authority.ADMIN) {
            val worldId = call.getWorldParam() ?: return@getAuthed

            call.respondHtml(addTeam(worldId))
        }
    }
}