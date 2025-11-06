package app.mcorg.pipeline.idea

import app.mcorg.pipeline.idea.commonsteps.GetAllIdeasStep
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.ideasPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetIdeas() {
    val user = this.getUser()

    val unreadNotifications = getUnreadNotificationsOrZero(user.id)

    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    executePipeline(
        onSuccess = {
            respondHtml(ideasPage(
                user = user,
                ideas = it,
                supportedVersions = supportedVersions,
                unreadNotifications = unreadNotifications
            ))
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to get ideas")
        }
    ) {
        step(GetAllIdeasStep)
    }
}