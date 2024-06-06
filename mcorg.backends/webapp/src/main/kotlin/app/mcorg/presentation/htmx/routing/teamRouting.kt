package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.teamsApi
import app.mcorg.presentation.htmx.handlers.getWorldParam
import app.mcorg.presentation.htmx.handlers.getWorldTeamParams
import app.mcorg.presentation.htmx.handlers.handleCreateTeam
import app.mcorg.presentation.htmx.handlers.respondTeam

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
    }
}