package app.mcorg.pipeline.profile

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProfile() {
    val user = getUser()
    val profile = MockUsers.Evegul.profile() // TODO: Replace with real profile data

    val unreadCount = when (val unreadCountResult = GetUnreadNotificationCountStep.process(user.id)) {
        is Result.Success -> unreadCountResult.value
        is Result.Failure -> 0
    }

    respondHtml(
        profilePage(user, profile, unreadCount)
    )
}