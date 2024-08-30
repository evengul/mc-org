package app.mcorg.presentation.handler

import app.mcorg.presentation.router.utils.clientRefresh
import app.mcorg.presentation.router.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetUsers() {
    respondHtml("World users page")
}

suspend fun ApplicationCall.handleGetAddUser() {
    respondHtml("Add user page")
}

suspend fun ApplicationCall.handlePostUser() {
    // TODO: Read form and add the user
    clientRefresh()
}