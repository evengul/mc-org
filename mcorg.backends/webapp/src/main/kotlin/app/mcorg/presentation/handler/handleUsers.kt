package app.mcorg.presentation.handler

import app.mcorg.domain.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.router.utils.respondHtml
import app.mcorg.presentation.templates.users.addUser
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

suspend fun ApplicationCall.handleGetAddUser() {
    respondHtml(addUser("/app/worlds/${getWorldId()}/users"))
}

suspend fun ApplicationCall.handlePostUser() {
    val worldId = getWorldId()
    val (username) = receiveAddUserRequest()
    val user = usersApi.getUser(username) ?: throw IllegalArgumentException("user does not exist")
    permissionsApi.addWorldPermission(user.id, worldId, Authority.PARTICIPANT)
    respondRedirect("/app/worlds/$worldId/users")
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
        clientRedirect("/app/worlds/$worldId/users")
    }
}