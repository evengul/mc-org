package app.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.htmx.handlers.*

fun Application.worldRouting() {
    routing {
        postAuthed("/worlds", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.handleCreateWorld()
        }

        getAuthed("/worlds", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.handleGetWorlds()
        }

        deleteAuthed("/worlds/{worldId}", permissionLevel = PermissionLevel.WORLD, authority = Authority.OWNER) {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@deleteAuthed

            call.handleDeleteWorld(worldId)
        }

        getAuthed("/worlds/{worldId}", permissionLevel = PermissionLevel.WORLD, authority = Authority.PARTICIPANT) {
            val worldId = call.getWorldParam() ?: return@getAuthed

            call.respondWorld(worldId)
        }
    }
}