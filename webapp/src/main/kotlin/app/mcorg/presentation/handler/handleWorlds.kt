package app.mcorg.presentation.handler

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.configuration.worldsApi
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.world.createWorldInputMapper
import app.mcorg.presentation.utils.respondEmptyHtml
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templates.world.worlds
import app.mcorg.presentation.utils.clientRedirect
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserId()
    val selectedWorld = usersApi.getProfile(userId)?.selectedWorld
    val permissions = permissionsApi.getPermissions(userId)
    val worlds = permissions.ownedWorlds + permissions.participantWorlds
    respondHtml(worlds(selectedWorld, worlds))
}

suspend fun ApplicationCall.handlePostWorld() {
    val userId = getUserId()
    val (worldName) = InputMappers.createWorldInputMapper(receiveParameters())

    val id = worldsApi.createWorld(worldName)
    permissionsApi.addWorldPermission(userId, id, Authority.OWNER)
    usersApi.selectWorld(userId, id)

    respondRedirect("/")
}

suspend fun ApplicationCall.handleDeleteWorld() {
    val worldId = getWorldId()
    usersApi.unSelectWorldForAll(worldId)
    permissionsApi.removeWorldPermissionForAll(worldId)
    worldsApi.deleteWorld(worldId)
    respondEmptyHtml()
}

suspend fun ApplicationCall.handleSelectWorld() {
    val userId = getUserId()
    val worldId = parameters["worldId"]?.toIntOrNull()
    if (worldId != null) {
        worldsApi.getWorld(worldId)?.let {
            usersApi.selectWorld(userId, worldId)
            clientRedirect("/app/worlds/$worldId")
        }
    }
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}