package app.mcorg.presentation.handler

import app.mcorg.domain.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.utils.respondEmptyHtml
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templates.users.createUserListElement
import app.mcorg.presentation.templates.users.users
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetUsers() {
    val worldId = getWorldId()
    val currentUser = getUser()
    val worldUsers = permissionsApi.getUsersInWorld(worldId).filter { it.id != currentUser.id }
    val isAdmin = permissionsApi.hasWorldPermission(currentUser.id, Authority.ADMIN, worldId)
    respondHtml(users(worldId, currentUser, worldUsers, isAdmin))
}

suspend fun ApplicationCall.handlePostUser() {
    val worldId = getWorldId()
    val (username) = receiveAddUserRequest()
    val user = usersApi.getUser(username) ?: return badRequest("User does not exist")
    permissionsApi.addWorldPermission(user.id, worldId, Authority.PARTICIPANT)
    val currentUserIsAdmin = permissionsApi.hasWorldPermission(getUserId(), authority = Authority.ADMIN, worldId)
    respondHtml(createUserListElement(worldId, user, currentUserIsAdmin))
}

suspend fun ApplicationCall.handleDeleteWorldUser() {
    val worldId = getWorldId()
    val userId = parameters["userId"]?.toIntOrNull()
    val currentUser = getUser()
    val isAdmin = permissionsApi.hasWorldPermission(currentUser.id, Authority.ADMIN, worldId)
    if (userId == null) {
        throw IllegalArgumentException("User id must be supplied")
    }
    if (!isAdmin) {
        respond(HttpStatusCode.Forbidden)
    } else {
        projectsApi.removeUserAssignments(userId)
        permissionsApi.removeWorldPermission(userId, worldId)
        respondEmptyHtml()
    }
}