package app.mcorg.presentation.handler

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.permission.*
import app.mcorg.pipeline.permission.RemoveUserAssignmentsInWorldInput
import app.mcorg.pipeline.permission.RemoveUserProjectAssignmentsInWorld
import app.mcorg.pipeline.permission.RemoveUserTaskAssignmentsInWorld
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.templates.users.createUserListElement
import app.mcorg.presentation.templates.users.users
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetUsers() {
    val worldId = getWorldId()
    val currentUser = getUser()
    val worldUsers = permissionsApi.getUsersInWorld(worldId).filter { it.id != currentUser.id }
    val isAdmin = permissionsApi.hasWorldPermission(currentUser.id, Authority.ADMIN, worldId)
    respondHtml(users(worldId, currentUser, worldUsers, isAdmin))
}

suspend fun ApplicationCall.handlePostUser() {
    val userId = getUserId()
    val worldId = getWorldId()

    val result = Pipeline.create<AddWorldParticipantFailure, Unit>()
        .pipe(Step.value(VerifyUserIsAdminInWorldStep(worldId, userId)))
        .pipe(VerifyParticipantAdderIsAdmin)
        .pipe(Step.value(receiveParameters()))
        .pipe(GetUsernameInputStep)
        .pipe(VerifyUsernameExistsStep)
        .map { AddUserInput(worldId, it) }
        .pipe(VerifyUserNotInWorldStep)
        .pipe(AddWorldParticipantStep)
        .map { it.userId }
        .pipe(GetNewParticipantStep)
        .execute(Unit)

    when(result) {
        is Result.Success -> respondHtml(createUserListElement(worldId, result.value, true))
        is Result.Failure -> {
            hxSwap("innerHTML")
            when (result.error) {
                is AddWorldParticipantStepFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                is GetUsernameInputFailure.NotPresent -> respondBadRequest("Parameter 'newUser' is required")
                is VerifyParticipantAdderIsAdminFailure.NotAdmin -> respond(HttpStatusCode.Forbidden, "You are not an admin of this world")
                is VerifyParticipantAdderIsAdminFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                is VerifyUserExistsStepFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                is VerifyUserExistsStepFailure.UserDoesNotExist -> respond(HttpStatusCode.NotFound, "User doesn't exist. They might have to sign in before you can add them.")
                is VerifyUserNotInWorldStepFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                is VerifyUserNotInWorldStepFailure.UserAlreadyExists -> respond(HttpStatusCode.Conflict, "User is already added to this world.")
                is GetNewParticipantStepFailure.NotFound -> respond(HttpStatusCode.NotFound, "User was not found after adding them to the world.")
                is GetNewParticipantStepFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            }
        }
    }
}

suspend fun ApplicationCall.handleDeleteWorldUser() {
    val worldId = getWorldId()
    val adminId = getUserId()

    val result = Pipeline.create<RemoveWorldParticipantFailure, Unit>()
        .pipe(Step.value(VerifyUserIsAdminInWorldStep(worldId, adminId)))
        .pipe(VerifyParticipantAdderIsAdmin)
        .pipe(Step.value(parameters))
        .pipe(GetUserIdInputStep)
        .map { VerifyUserInWorldInput(worldId = worldId, userId = it) }
        .pipe(VerifyUserInWorld)
        .map { RemoveUserAssignmentsInWorldInput(worldId = it.worldId, userId = it.userId) }
        .pipe(RemoveUserTaskAssignmentsInWorld)
        .pipe(RemoveUserProjectAssignmentsInWorld)
        .map { RemoveUserFromWorldInput(userId = it.userId, worldId = it.worldId) }
        .pipe(RemoveUserFromWorldStep)
        .execute(Unit)

    when (result) {
        is Result.Success -> respondEmptyHtml()
        is Result.Failure -> when(result.error) {
            is VerifyParticipantAdderIsAdminFailure.NotAdmin -> respond(HttpStatusCode.Forbidden, "You are not an admin of this world")
            is VerifyParticipantAdderIsAdminFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            is VerifyUserInWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            is GetUserIdInputFailure.NotPresent -> respondBadRequest("Parameter 'userId' is required")
            is RemoveUserAssignmentsInWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            is RemoveUserFromWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            is VerifyUserInWorldFailure.NotPresent -> respondNotFound("User doesn't exist in world")
        }
    }
}