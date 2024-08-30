package app.mcorg.presentation.v2.handler

import app.mcorg.domain.Authority
import app.mcorg.presentation.v2.configuration.permissionsApi
import app.mcorg.presentation.v2.configuration.usersApi
import app.mcorg.presentation.v2.configuration.worldsApi
import app.mcorg.presentation.v2.router.utils.getUserId
import app.mcorg.presentation.v2.router.utils.getWorldId
import app.mcorg.presentation.v2.router.utils.receiveCreateWorldRequest
import app.mcorg.presentation.v2.router.utils.respondHtml
import app.mcorg.presentation.v2.templates.world.addWorld
import app.mcorg.presentation.v2.templates.world.worlds
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorlds() {

    respondHtml(worlds(usersApi.getProfile(getUserId())?.selectedWorld), )
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

suspend fun ApplicationCall.handleShowWorld() {
    val worldId = getWorldId()
    respondRedirect("/worlds/$worldId/projects")
}