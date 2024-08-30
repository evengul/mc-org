package app.mcorg.presentation.handler

import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.router.utils.clientRefresh
import app.mcorg.presentation.router.utils.getUserId
import app.mcorg.presentation.router.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetProfile() {
    // TODO: Add profile page HTML
    respondHtml("Profile page")
}

suspend fun ApplicationCall.handleUploadProfilePhoto() {
    // TODO: Validate image, resize if needed, upload, and return new image html.
    clientRefresh()
}

suspend fun ApplicationCall.handleIsTechnical() {
    usersApi.isTechnical(getUserId())
    clientRefresh()
}

suspend fun ApplicationCall.handleIsNotTechnical() {
    usersApi.isNotTechnical(getUserId())
    clientRefresh()
}