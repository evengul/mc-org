package app.mcorg.pipeline.idea

import app.mcorg.pipeline.idea.commonsteps.GetAllIdeasStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.ideasPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetIdeas() {
    val user = this.getUser()

    val unreadNotifications = getUnreadNotificationsOrZero(user.id)

    executePipeline(
        onSuccess = {
            respondHtml(ideasPage(
                user = user,
                ideas = it,
                unreadNotifications = unreadNotifications
            ))
        }
    ) {
        step(GetAllIdeasStep)
    }
}