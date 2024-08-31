package app.mcorg.presentation.handler

import app.mcorg.domain.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.router.utils.respondHtml
import app.mcorg.presentation.templates.users.addUser
import app.mcorg.presentation.templates.users.users
import app.mcorg.presentation.utils.receiveAddUserRequest
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetUsers() {
    val worldId = getWorldId()
    val currentUser = getUser()
    val worldUsers = permissionsApi.getUsersInWorld(worldId)
    val isAdmin = permissionsApi.hasWorldPermission(currentUser.id, Authority.ADMIN, worldId)
    respondHtml(users(worldId, currentUser, worldUsers, isAdmin))
}

suspend fun ApplicationCall.handleGetAddUser() {
    respondHtml(addUser())
}

suspend fun ApplicationCall.handlePostUser() {
    val worldId = getWorldId()
    val (username) = receiveAddUserRequest()
    val user = usersApi.getUser(username) ?: throw IllegalArgumentException("user does not exist")
    permissionsApi.addWorldPermission(user.id, worldId, Authority.PARTICIPANT)
    respondRedirect("/app/worlds/$worldId/users")
}