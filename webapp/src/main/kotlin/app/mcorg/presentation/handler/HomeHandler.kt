package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.invitation.GetUserInvitationsStep
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.pipeline.world.GetPermittedWorldsInput
import app.mcorg.pipeline.world.GetPermittedWorldsStep
import app.mcorg.presentation.templated.home.homePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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

        val invitationsPipeline = Pipeline.create<DatabaseFailure, Int>()
            .pipe(GetUserInvitationsStep)

        val worldsPipeline = Pipeline.create<DatabaseFailure, Int>()
            .map { GetPermittedWorldsInput(userId = it) }
            .pipe(GetPermittedWorldsStep)

        val notificationCountPipeline = Pipeline.create<Unit, Int>()
            .pipe(object : Step<Int, Unit, Int> {
                override suspend fun process(input: Int): Result<Unit, Int> {
                    return when (val result = GetUnreadNotificationCountStep.process(input)) {
                        is Result.Success -> Result.success(result.value)
                        is Result.Failure -> Result.success(0)
                    }
                }
            })

        val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

        executeParallelPipeline(
            onSuccess = { (invitations, worlds, unreadCount) ->
                respondHtml(homePage(user, invitations, worlds, supportedVersions, unreadCount))
            },
            onFailure = {
                logger.warn("Failed to load home data for user: ${user.id}. Error: $it")
            }
        ) {
            val invitationsRef = pipeline("invitations", user.id, invitationsPipeline)
            val worldsRef = pipeline("worlds", user.id, worldsPipeline)
            val notificationCountRef = pipeline("notifications", user.id, notificationCountPipeline)
            merge("homeData", invitationsRef, worldsRef, notificationCountRef) { invitations, worlds, unreadCount ->
                Result.success(Triple(invitations, worlds, unreadCount))
            }
        }
    }
}