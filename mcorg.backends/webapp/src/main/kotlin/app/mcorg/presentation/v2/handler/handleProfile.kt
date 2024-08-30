package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.v2.configuration.usersApi
import app.mcorg.presentation.v2.router.utils.clientRefresh
import app.mcorg.presentation.v2.router.utils.getUserId
import app.mcorg.presentation.v2.router.utils.respondHtml
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