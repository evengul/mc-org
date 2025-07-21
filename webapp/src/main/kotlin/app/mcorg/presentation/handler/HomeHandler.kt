package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.world.GetPermittedWorldsStep
import app.mcorg.pipeline.world.GetPermittedWorldsError
import app.mcorg.presentation.templated.home.homePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class HomeHandler {
    fun Route.homeRoute() {
        get {
            call.handleGetHome()
        }
    }

    private suspend fun ApplicationCall.handleGetHome() {
        val user = getUser()

        executeParallelPipelineDSL(
            onSuccess = {
                respondHtml(homePage(user, it))
            },
            onFailure = {
                when (it) {
                    is GetPermittedWorldsError.DatabaseError -> {
                        respond(InternalServerError, "Unable to load worlds due to database error")
                    }
                }
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