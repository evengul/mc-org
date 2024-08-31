package app.mcorg.presentation.handler

import app.mcorg.domain.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.configuration.worldsApi
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.receiveCreateWorldRequest
import app.mcorg.presentation.router.utils.respondHtml
import app.mcorg.presentation.templates.world.addWorld
import app.mcorg.presentation.templates.world.worlds
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserId()
    val selectedWorld = usersApi.getProfile(userId)?.selectedWorld
    val permissions = permissionsApi.getPermissions(userId)
    val worlds = permissions.ownedWorlds + permissions.participantWorlds
    respondHtml(worlds(selectedWorld, worlds))
}

suspend fun ApplicationCall.handleGetAddWorld() {
    respondHtml(addWorld())
}

suspend fun ApplicationCall.handlePostWorld() {
    val userId = getUserId()
    val (worldName) = receiveCreateWorldRequest()

    val id = worldsApi.createWorld(worldName)
    permissionsApi.addWorldPermission(userId, id, Authority.OWNER)
    usersApi.selectWorld(userId, id)

    respondRedirect("/")
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}