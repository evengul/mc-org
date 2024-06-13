package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.teamsApi
import app.mcorg.presentation.htmx.handlers.*
import io.ktor.http.*
import io.ktor.server.response.*

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
            val (_, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@deleteAuthed

            teamsApi().deleteTeam(teamId)
            call.respondEmpty()
        }

        postAuthed("/worlds/{worldId}/teams/{teamId}/users", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@postAuthed

            call.handleAddTeamUser(worldId, teamId)

            call.clientRefresh()
        }

        deleteAuthed("/worlds/{worldId}/teams/{teamId}/users/{userId}", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@deleteAuthed
            val userId = call.parameters["userId"]?.toIntOrNull() ?: return@deleteAuthed call.respond(HttpStatusCode.BadRequest)

            permissionsApi().removeTeamPermission(userId, teamId)

            if (!permissionsApi().hasTeamPermissionInWorld(userId, worldId)) {
                permissionsApi().removeWorldPermission(userId, worldId)
            }

            call.clientRefresh()
        }
    }
}