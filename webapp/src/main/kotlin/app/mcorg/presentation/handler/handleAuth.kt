package app.mcorg.presentation.handler

import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.response.*


suspend fun ApplicationCall.handleGetSignOut() {
    response.cookies.removeToken(getHost() ?: "false")
    respondRedirect("/", permanent = false)
}