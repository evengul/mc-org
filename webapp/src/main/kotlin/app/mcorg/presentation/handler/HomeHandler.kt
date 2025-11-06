package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
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
import org.slf4j.LoggerFactory

class HomeHandler {
    fun Route.homeRoute() {
        get {
            call.handleGetHome()
        }
    }

    private val logger = LoggerFactory.getLogger(HomeHandler::class.java)

    private suspend fun ApplicationCall.handleGetHome() {
        val user = getUser()

        val invitationsPipeline = Pipeline.create<AppFailure.DatabaseError, Int>()
            .pipe(GetUserInvitationsStep)

        val worldsPipeline = Pipeline.create<AppFailure.DatabaseError, Int>()
            .map { GetPermittedWorldsInput(userId = it) }
            .pipe(GetPermittedWorldsStep)

        val notifications = getUnreadNotificationsOrZero(user.id)

        val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

        executeParallelPipeline(
            onSuccess = { (invitations, worlds) ->
                respondHtml(homePage(user, invitations, worlds, supportedVersions, notifications))
            },
            onFailure = {
                logger.warn("Failed to load home data for user: ${user.id}. Error: $it")
            }
        ) {
            val invitationsRef = pipeline("invitations", user.id, invitationsPipeline)
            val worldsRef = pipeline("worlds", user.id, worldsPipeline)
            merge("homeData", invitationsRef, worldsRef) { invitations, worlds ->
                Result.success(Pair(invitations, worlds))
            }
        }
    }
}