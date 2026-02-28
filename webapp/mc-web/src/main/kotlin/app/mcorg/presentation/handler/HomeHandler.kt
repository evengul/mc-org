package app.mcorg.presentation.handler

import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.presentation.templated.home.homePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.routing.*

class HomeHandler {
    fun Route.homeRoute() {
        get {
            call.handleGetHome()
        }
    }

    private suspend fun ApplicationCall.handleGetHome() {
        val user = getUser()

        val notifications = getUnreadNotificationsOrZero(user.id)

        val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

        handlePipeline(
            onSuccess = { (invitations, worlds) ->
                respondHtml(homePage(user, invitations, worlds, supportedVersions, notifications))
            }
        ) {
            parallel(
                { GetUserInvitationsStep.run(user.id) },
                { GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = user.id)) }
            )
        }
    }
}
