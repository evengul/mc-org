package app.mcorg.presentation.handler

import app.mcorg.domain.cqrs.commands.world.CreateWorldCommand
import app.mcorg.presentation.configuration.WorldCommands
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.requiredInt
import app.mcorg.presentation.mappers.world.createWorldInputMapper
import app.mcorg.presentation.templates.world.worlds
import app.mcorg.presentation.utils.*
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

    WorldCommands.createWorld(userId, worldName).fold(
        {
            when (it) {
                is CreateWorldCommand.WorldNameAlreadyExistsFailure -> respondBadRequest("World with this name already exists")
            }
        },
        { respondRedirect("/app/worlds/${it.worldId}") }
    )
}

suspend fun ApplicationCall.handleDeleteWorld() {
    val worldId = getWorldId()

    WorldCommands.deleteWorld(worldId).fold(
        { respondBadRequest("World could not be deleted") },
        { respondEmptyHtml() }
    )
}

suspend fun ApplicationCall.handleSelectWorld() {
    val userId = getUserId()
    val worldId = parameters.requiredInt("worldId")
    WorldCommands.selectWorld(userId, worldId).fold(
        { respondBadRequest("World could not be selected") },
        { clientRedirect("/app/worlds/$worldId/projects") }
    )
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}