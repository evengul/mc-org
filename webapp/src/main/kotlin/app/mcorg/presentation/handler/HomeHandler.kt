package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
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

        executeParallelPipeline(
            onSuccess = { (invitations, worlds) ->
                respondHtml(homePage(user, invitations, worlds, supportedVersions, notifications))
            }
        ) {
            val invitationsRef = singleStep("invitations", user.id, GetUserInvitationsStep)
            val worldsRef = singleStep("worlds", GetPermittedWorldsInput(userId = user.id), GetPermittedWorldsStep)
            merge("homeData", invitationsRef, worldsRef) { invitations, worlds ->
                Result.success(Pair(invitations, worlds))
            }
        }
    }
}