package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.extractValue
import app.mcorg.domain.pipeline.pipeWithContext
import app.mcorg.domain.pipeline.withContext
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.profile.IsTechnicalPlayerStep
import app.mcorg.presentation.configuration.ProfileCommands
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templates.profile.createIsTechnicalCheckBox
import app.mcorg.presentation.templates.profile.profile
import app.mcorg.presentation.utils.getUserFromCookie
import app.mcorg.presentation.utils.removeTokenAndSignOut
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProfile() {
    val userId = getUserId()
    val profile = usersApi.getProfile(userId) ?: throw IllegalArgumentException("User does not exist")
    respondHtml(profile(profile))
}

suspend fun ApplicationCall.handleUploadProfilePhoto() {
    respond(HttpStatusCode.Forbidden)
}

suspend fun ApplicationCall.handleIsTechnical() {
    val userId = getUserId()
    val isTechnical = receiveText() == "technicalPlayer=on"

    val result = Pipeline.create<DatabaseFailure, Boolean>()
        .withContext(userId)
        .pipeWithContext(IsTechnicalPlayerStep)
        .extractValue()
        .execute(isTechnical)

    if (result.isFailure) {
        respond(HttpStatusCode.InternalServerError)
    } else {
        respondHtml(createIsTechnicalCheckBox(isTechnical))

    }
}

suspend fun ApplicationCall.handleDeleteUser() {
    val userId = getUserFromCookie()?.id ?: return removeTokenAndSignOut()

    ProfileCommands.deleteProfile(userId)

    removeTokenAndSignOut()
}