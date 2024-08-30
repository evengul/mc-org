package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.v2.router.utils.clientRefresh
import app.mcorg.presentation.v2.router.utils.respondHtml
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