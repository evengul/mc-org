package app.mcorg.pipeline.profile

import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProfile() {
    val user = MockUsers.Evegul.tokenProfile()
    val profile = MockUsers.Evegul.profile()

    respondHtml(
        profilePage(user, profile)
    )
}