package app.mcorg.presentation.handler

import app.mcorg.presentation.configuration.contraptionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.templates.contraptions.contraptions
import app.mcorg.presentation.templates.contraptions.createContraptionListElement
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetContraptions() {
    val userId = getUserId()
    val selectedWorldId = usersApi.getProfile(userId)?.selectedWorld
    val contraptions = contraptionsApi.getContraptions()
    respondHtml(contraptions(selectedWorldId, contraptions))
}

suspend fun ApplicationCall.handlePostContraption() {
    val (
        name,
        description,
        authors,
        gameType,
        version,
        schematicUrl,
        worldDownloadUrl
    ) = receiveContraption()
    val id = contraptionsApi.createContraption(
        name,
        description,
        archived = false,
        authors,
        gameType,
        version,
        schematicUrl,
        worldDownloadUrl
    )
    val created = contraptionsApi.getContraption(id) ?: throw IllegalStateException("Could not retrieve created contraption")
    respondHtml(createContraptionListElement(created))
}

suspend fun ApplicationCall.handleGetContraption() {
    val contraptionId = getContraptionId()
    val contraption = contraptionsApi.getContraption(contraptionId)
}

suspend fun ApplicationCall.handleDeleteContraption() {
    val contraptionId = getContraptionId()
    contraptionsApi.deleteContraption(contraptionId)
    respondEmptyHtml()
}