package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.world.GetPermittedWorldsStep
import app.mcorg.presentation.mockdata.MockInvitations
import app.mcorg.presentation.mockdata.MockWorlds
import app.mcorg.presentation.templated.home.homePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
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

        respondHtml(homePage(user, MockInvitations.getPending(), MockWorlds.getList()))

        executeParallelPipelineDSL(
            onSuccess = {
                logger.info("Successfully loaded worlds for user: ${user.id}. Ignoring and using mocks for now.")
            },
            onFailure = {
                logger.warn("Failed to load worlds for user: ${user.id}. Using mock data instead. Error: $it")
            }
        ) {
            pipeline(
                id = "worlds",
                input = user.id,
                pipeline = Pipeline { input -> GetPermittedWorldsStep().process(input) }
            )
        }
    }
}