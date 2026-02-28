package app.mcorg.pipeline.profile

import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetProfile() {
    val user = getUser()
    val notifications = getUnreadNotificationsOrZero(user.id)

    respondHtml(profilePage(user, notifications))
}