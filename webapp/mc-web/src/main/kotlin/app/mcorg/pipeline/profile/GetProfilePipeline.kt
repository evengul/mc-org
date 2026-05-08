package app.mcorg.pipeline.profile

import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetProfile() {
    val user = getUser()
    respondHtml(profilePage(user))
}
