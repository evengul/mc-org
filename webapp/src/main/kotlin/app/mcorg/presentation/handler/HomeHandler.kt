package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.invitation.GetUserInvitationsStep
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
            .pipe(GetPermittedWorldsStep)

        executeParallelPipeline(
            onSuccess = {
                respondHtml(homePage(user, it.first, it.second))
            },
            onFailure = {
                logger.warn("Failed to load worlds for user: ${user.id}. Error: $it")
            }
        ) {
            val invitationsRef = pipeline("invitations", user.id, invitationsPipeline)
            val worldsRef = pipeline("worlds", user.id, worldsPipeline)
            merge("invitationsAndWorlds", invitationsRef, worldsRef) { invitations, worlds ->
                Result.success(invitations to worlds)
            }
        }
    }
}