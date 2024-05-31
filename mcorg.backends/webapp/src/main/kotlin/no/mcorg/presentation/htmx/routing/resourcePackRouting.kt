package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addResourcePack
import no.mcorg.presentation.htmx.templates.pages.addResourceToPack

fun Application.resourcePackRouting() {
    routing {
        getAuthed("/resourcepacks", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.respondResourcePacks()
        }

        postAuthed("/resourcepacks", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.handleCreateResourcePack()
        }

        deleteAuthed("/resourcepacks/{packId}", permissionLevel = PermissionLevel.PACK, authority = Authority.OWNER) {
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@deleteAuthed

            packsApi().deletePack(packId)
            call.respondEmpty()
        }

        getAuthed("/resourcepacks/{packid}", permissionLevel = PermissionLevel.PACK, authority = Authority.PARTICIPANT) {
            val packId = call.getResourcePackParam() ?: return@getAuthed
            call.respondResourcePack(packId)
        }

        postAuthed("/resourcepacks/{packId}", permissionLevel = PermissionLevel.PACK, authority = Authority.ADMIN) {
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@postAuthed

            call.handleAddResourceToPack(packId)
        }

        deleteAuthed("/resourcepacks/{packId}/resources/{resourceId}", permissionLevel = PermissionLevel.PACK, authority = Authority.ADMIN) {
            val (_, resourceId) = call.getResourcePackResourceParams(failOnMissingValue = true) ?: return@deleteAuthed
            packsApi().removeResource(resourceId)
            call.respondEmpty()
        }

        postAuthed("/worlds/{worldId}/resourcepacks", permissionLevel = PermissionLevel.WORLD, authority = Authority.ADMIN) {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@postAuthed
            call.handleSharePackWithWorld(worldId)
        }

        deleteAuthed("/worlds/{worldId}/resourcepacks/{packId}", permissionLevel = PermissionLevel.WORLD, authority = Authority.ADMIN) {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@deleteAuthed
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@deleteAuthed
            call.handleUnSharePackWithWorld(packId, worldId)
        }

        postAuthed("/worlds/{worldId}/teams/{teamId}/resourcepacks", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@postAuthed
            call.handleSharePackWithTeam(worldId, teamId)
        }

        deleteAuthed("/worlds/{worldId}/teams/{teamId}/resourcepacks/{packId}", permissionLevel = PermissionLevel.TEAM, authority = Authority.ADMIN) {
            val (_, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@deleteAuthed
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@deleteAuthed
            call.handleUnSharePackWithTeam(teamId, packId)
        }

        getAuthed("/htmx/resourcepacks/add", permissionLevel = PermissionLevel.AUTHENTICATED, authority = Authority.PARTICIPANT) {
            call.respondHtml(addResourcePack())
        }

        getAuthed("/htmx/resourcepacks/{packId}/resources/add", permissionLevel = PermissionLevel.PACK, authority = Authority.ADMIN) {
            val resourcePackId = call.getResourcePackParam() ?: return@getAuthed
            call.respondHtml(addResourceToPack(resourcePackId))
        }
    }
}