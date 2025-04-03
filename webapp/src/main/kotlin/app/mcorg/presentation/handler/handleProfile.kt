package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result.Success
import app.mcorg.domain.pipeline.extractValue
import app.mcorg.domain.pipeline.pipeWithContext
import app.mcorg.domain.pipeline.withContext
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.permission.RemoveUserPermissionsStep
import app.mcorg.pipeline.profile.GetProfileStep
import app.mcorg.pipeline.profile.IsTechnicalPlayerStep
import app.mcorg.pipeline.project.RemoveUserAssignmentsStep
import app.mcorg.pipeline.user.DeleteUserStep
import app.mcorg.presentation.utils.getUserId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templates.profile.createIsTechnicalCheckBox
import app.mcorg.presentation.templates.profile.profile
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondNotFound
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProfile() {
    val result = Pipeline.create<DatabaseFailure, Int>()
        .pipe(GetProfileStep)
        .map { profile(it) }
        .execute(getUserId())

    if (result is Success) {
        respondHtml(result.value)
    } else {
        when (result.errorOrNull()!!) {
            is DatabaseFailure.NotFound -> respondNotFound()
            else -> respond(HttpStatusCode.InternalServerError)
        }
    }
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
    val userId = getUserId()

    val result = Pipeline.create<DatabaseFailure, Unit>()
        .withContext(userId)
        .pipeWithContext(RemoveUserAssignmentsStep)
        .pipeWithContext(RemoveUserPermissionsStep)
        .pipeWithContext(DeleteUserStep)
        .execute(Unit)

    if (result.isFailure) {
        respond(HttpStatusCode.InternalServerError)
    } else {
        response.cookies.removeToken(getHost() ?: "localhost")
        clientRedirect("/auth/sign-in")
    }
}