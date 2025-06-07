package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.permission.RemoveUserPermissionsStep
import app.mcorg.pipeline.profile.GetProfileStep
import app.mcorg.pipeline.profile.IsTechnicalPlayerStep
import app.mcorg.pipeline.profile.UpdatePlayerAuditingInfoStep
import app.mcorg.pipeline.project.RemoveUserAssignmentsStep
import app.mcorg.pipeline.user.DeleteUserStep
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templates.profile.createIsTechnicalCheckBox
import app.mcorg.presentation.templates.profile.profile
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondNotFound
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProfile() {
    Pipeline.create<DatabaseFailure, Int>()
        .pipe(GetProfileStep)
        .map { profile(it) }
        .fold(
            input = getUserId(),
            onSuccess = { respondHtml(it) },
            onFailure = { failure ->
                when (failure) {
                    is DatabaseFailure.NotFound -> respondNotFound()
                    else -> respond(HttpStatusCode.InternalServerError)
                }
            }
        )
}

suspend fun ApplicationCall.handleUploadProfilePhoto() {
    respond(HttpStatusCode.Forbidden)
}

suspend fun ApplicationCall.handleIsTechnical() {
    val currentUser = getUser()
    val isTechnical = receiveText() == "technicalPlayer=on"

    Pipeline.create<DatabaseFailure, Boolean>()
        .pipe(IsTechnicalPlayerStep(currentUser.id))
        .map { currentUser.id }
        .pipe(UpdatePlayerAuditingInfoStep(currentUser.username))
        .fold(
            input = isTechnical,
            onFailure = { respond(HttpStatusCode.InternalServerError) },
            onSuccess = { respondHtml(createIsTechnicalCheckBox(isTechnical)) }
        )
}

suspend fun ApplicationCall.handleDeleteUser() {
    val userId = getUserId()

    Pipeline.create<DatabaseFailure, Unit>()
        .pipe(RemoveUserAssignmentsStep(userId))
        .pipe(RemoveUserPermissionsStep(userId))
        .pipe(DeleteUserStep(userId))
        .fold(
            input = Unit,
            onFailure = { respond(HttpStatusCode.InternalServerError) },
            onSuccess = {
                response.cookies.removeToken(getHost() ?: "localhost")
                clientRedirect("/auth/sign-in")
            }
        )
}