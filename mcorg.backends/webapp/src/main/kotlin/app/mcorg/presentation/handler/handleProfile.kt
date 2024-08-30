package app.mcorg.presentation.handler

import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.router.utils.respondHtml
import app.mcorg.presentation.templates.profile.profile
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProfile() {
    val userId = getUserId()
    val profile = usersApi.getProfile(userId) ?: throw IllegalArgumentException("User does not exist")
    respondHtml(profile(profile))
}

suspend fun ApplicationCall.handleUploadProfilePhoto() {
    respondRedirect("/app/profile")
}

suspend fun ApplicationCall.handleIsTechnical() {
    usersApi.isTechnical(getUserId())
    respondRedirect("/app/profile")
}

suspend fun ApplicationCall.handleIsNotTechnical() {
    usersApi.isNotTechnical(getUserId())
    respondRedirect("/app/profile")
}