package app.mcorg.presentation.handler

import app.mcorg.domain.cqrs.commands.user.AddUserCommand
import app.mcorg.domain.cqrs.commands.user.RemoveUserCommand
import app.mcorg.domain.model.permissions.Authority
import app.mcorg.presentation.configuration.UserCommands
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.requiredInt
import app.mcorg.presentation.mappers.user.addUserInputMapper
import app.mcorg.presentation.templates.users.createUserListElement
import app.mcorg.presentation.templates.users.users
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*

suspend fun ApplicationCall.handleGetUsers() {
    val worldId = getWorldId()
    val currentUser = getUser()
    val worldUsers = permissionsApi.getUsersInWorld(worldId).filter { it.id != currentUser.id }
    val isAdmin = permissionsApi.hasWorldPermission(currentUser.id, Authority.ADMIN, worldId)
    respondHtml(users(worldId, currentUser, worldUsers, isAdmin))
}

suspend fun ApplicationCall.handlePostUser() {
    val worldId = getWorldId()
    val (username) = InputMappers.addUserInputMapper(receiveParameters())

    UserCommands.addToWorld(worldId, username).fold(
        {
            when (it) {
                is AddUserCommand.UserDoesNotExistFailure -> respondBadRequest("User does not exist")
                is AddUserCommand.UserAlreadyExistFailure -> respondBadRequest("User already exists")
            }
        },
        {
            val user = usersApi.getUser(username)!!
            val currentUserIsAdmin =
                permissionsApi.hasWorldPermission(getUserId(), authority = Authority.ADMIN, worldId)
            respondHtml(createUserListElement(worldId, user, currentUserIsAdmin))
        }
    )
}

suspend fun ApplicationCall.handleDeleteWorldUser() {
    val worldId = getWorldId()
    val userId = parameters.requiredInt("userId")
    UserCommands.removeFromWorld(worldId, userId).fold({
        when (it) {
            is RemoveUserCommand.UserDoesNotExistFailure -> respondBadRequest("User does not exist")
            is RemoveUserCommand.UserDoesNotBelongToWorldFailure -> respondBadRequest("User is not in world")
        }
    }, {
        respondEmptyHtml()
    })
}