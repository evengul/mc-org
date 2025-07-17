package app.mcorg.presentation.handler

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AddWorldParticipantFailure
import app.mcorg.pipeline.failure.AddWorldParticipantStepFailure
import app.mcorg.pipeline.failure.GetNewParticipantStepFailure
import app.mcorg.pipeline.failure.GetOtherUsersStepFailure
import app.mcorg.pipeline.failure.GetUserIdInputFailure
import app.mcorg.pipeline.failure.GetUsernameInputFailure
import app.mcorg.pipeline.failure.GetWorldParticipantsFailure
import app.mcorg.pipeline.failure.RemoveUserAssignmentsInWorldFailure
import app.mcorg.pipeline.failure.RemoveUserFromWorldFailure
import app.mcorg.pipeline.failure.RemoveWorldParticipantFailure
import app.mcorg.pipeline.failure.UpdateWorldPermissionAuditInfoFailure
import app.mcorg.pipeline.failure.VerifyParticipantAdderIsAdminFailure
import app.mcorg.pipeline.failure.VerifyUserExistsStepFailure
import app.mcorg.pipeline.failure.VerifyUserInWorldFailure
import app.mcorg.pipeline.failure.VerifyUserNotInWorldStepFailure
import app.mcorg.pipeline.permission.*
import app.mcorg.pipeline.permission.RemoveUserAssignmentsInWorldInput
import app.mcorg.pipeline.permission.RemoveUserProjectAssignmentsInWorld
import app.mcorg.pipeline.permission.RemoveUserTaskAssignmentsInWorld
import app.mcorg.presentation.templates.users.createUserListElement
import app.mcorg.presentation.templates.users.users
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

data class GetUsersData(
    val worldId: Int,
    val currentUserId: Int,
    val isAdmin: Boolean = false,
    val users: List<User> = emptyList(),
)

suspend fun ApplicationCall.handleGetUsers() {
    val worldId = getWorldId()
    val currentUser = getUser()

    Pipeline.create<GetWorldParticipantsFailure, Unit>()
        .pipe(Step.value(WorldUser(worldId = worldId, userId = currentUser.id)))
        .pipe(VerifyParticipantAdderIsAdmin)
        .map { true }.recover { if (it is VerifyParticipantAdderIsAdminFailure.NotAdmin) Result.success(false) else Result.failure(it) }
        .map { GetUsersData(worldId, currentUser.id, it) }
        .pipe(GetOtherUsersStep)
        .map { users(worldId, currentUser, it.users, it.isAdmin) }
        .fold(
            input = Unit,
            onSuccess = { respondHtml(it) },
            onFailure = { failure ->
                when (failure) {
                    is VerifyParticipantAdderIsAdminFailure.NotAdmin -> respond(HttpStatusCode.Forbidden, "You are not an admin of this world")
                    is VerifyParticipantAdderIsAdminFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                    is GetOtherUsersStepFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                }
            }
        )
}

suspend fun ApplicationCall.handlePostUser() {
    val user = getUser()
    val worldId = getWorldId()

    var newUser = -1

    Pipeline.create<AddWorldParticipantFailure, Unit>()
        .pipe(Step.value(WorldUser(worldId = worldId, userId = user.id)))
        .pipe(VerifyParticipantAdderIsAdmin)
        .pipe(Step.value(receiveParameters()))
        .pipe(GetUsernameInputStep)
        .pipe(VerifyUsernameExistsStep)
        .map { AddUserInput(worldId, it) }
        .pipe(VerifyUserNotInWorldStep)
        .pipe(AddWorldParticipantStep(user.username))
        .peek { newUser = it.userId }
        .map {  }
        .pipe(UpdateWorldPermissionAuditInfoStep(worldId, user.username))
        .map { newUser }
        .pipe(GetNewParticipantStep)
        .fold(
            input = Unit,
            onSuccess = { respondHtml(createUserListElement(worldId, it, true)) },
            onFailure = {
                hxSwap("innerHTML")
                when (it) {
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
                    is UpdateWorldPermissionAuditInfoFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                }
            }
        )
}

suspend fun ApplicationCall.handleDeleteWorldUser() {
    val worldId = getWorldId()
    val admin = getUser()

    Pipeline.create<RemoveWorldParticipantFailure, Unit>()
        .pipe(Step.value(WorldUser(worldId = worldId, userId = admin.id)))
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
        .pipe(UpdateWorldPermissionAuditInfoStep(worldId, admin.username))
        .fold(
            input = Unit,
            onSuccess = { respondEmptyHtml() },
            onFailure = {
                when(it) {
                    is VerifyParticipantAdderIsAdminFailure.NotAdmin -> respond(HttpStatusCode.Forbidden, "You are not an admin of this world")
                    is VerifyParticipantAdderIsAdminFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                    is VerifyUserInWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                    is GetUserIdInputFailure.NotPresent -> respondBadRequest("Parameter 'userId' is required")
                    is RemoveUserAssignmentsInWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                    is RemoveUserFromWorldFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                    is VerifyUserInWorldFailure.NotPresent -> respondNotFound("User doesn't exist in world")
                    is UpdateWorldPermissionAuditInfoFailure.Other -> respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
                }
            }
        )
}