package app.mcorg.pipeline.profile

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProfile() {
    val user = getUser()

    executePipeline(
        onSuccess = { unreadNotifications -> respondHtml(profilePage(user, unreadNotifications))},
        onFailure = { respondHtml("An error occurred") }
    ) {
        step(Step.value(user.id))
            .step(GetUnreadNotificationCountStep)
    }
}