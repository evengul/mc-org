package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addWorld

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

        getAuthed("/htmx/worlds/add", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.respondHtml(addWorld())
        }
    }
}