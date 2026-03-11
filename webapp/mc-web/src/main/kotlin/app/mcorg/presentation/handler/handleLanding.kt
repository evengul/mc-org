package app.mcorg.presentation.handler

import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    val activeWorldId = getUser().activeWorldId
    if (activeWorldId != null) {
        respondRedirect(Link.Worlds.world(activeWorldId).projects().to)
    } else {
        respondRedirect(Link.Worlds.to)
    }
}
