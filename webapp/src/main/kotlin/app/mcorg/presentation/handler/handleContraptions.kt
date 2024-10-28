package app.mcorg.presentation.handler

import app.mcorg.domain.categorization.createCategories
import app.mcorg.presentation.configuration.*
import app.mcorg.presentation.templates.contraptions.contraptions
import app.mcorg.presentation.templates.contraptions.createContraptionFilter
import app.mcorg.presentation.templates.contraptions.createContraptionListElement
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetContraptions() {
    val userId = getUserId()
    val selectedWorldId = usersApi.getProfile(userId)?.selectedWorld
    val contraptions = contraptionsApi.getContraptions()
    val categories = createCategories(biomeApi, mobApi)
    val items = itemApi.getItems()
    respondHtml(contraptions(selectedWorldId, contraptions, categories, items))
}

suspend fun ApplicationCall.handleGetContraptionsFilter() {
    val categories = createCategories(biomeApi, mobApi)
    val items = itemApi.getItems()
    val selectedSubCategory = receiveContraptionFilterSubCategory()
    val selectedCategory = receiveContraptionFilterCategory(selectedSubCategory)
    respondHtml(createContraptionFilter(categories, items, selectedCategory, selectedSubCategory))
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