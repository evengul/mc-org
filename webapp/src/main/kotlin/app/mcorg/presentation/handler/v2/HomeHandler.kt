package app.mcorg.presentation.handler.v2

import app.mcorg.domain.pipeline.MergeSteps
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetAllWorldsFailure
import app.mcorg.pipeline.failure.GetSelectedWorldIdFailure
import app.mcorg.pipeline.world.GetAllPermittedWorldsForUserStep
import app.mcorg.pipeline.world.GetSelectedWorldIdStep
import app.mcorg.presentation.templates.world.worlds
import app.mcorg.presentation.utils.getUserId
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
        val userId = getUserId()

        executeParallelPipelineDSL(
            onSuccess = { respondHtml(it) },
            onFailure = { respond(InternalServerError, "An unknown error occurred") }
        ) {
            // Pipeline to get selected world ID that returns null when no world is selected
            val selectedWorldPipeline = Pipeline<Int, GetAllWorldsFailure, Int?> { input ->
                when (val result = GetSelectedWorldIdStep.process(input)) {
                    is Result.Success -> Result.success(result.value)
                    is Result.Failure -> when (result.error) {
                        is GetSelectedWorldIdFailure.NoWorldSelected -> Result.success(null)
                        is GetSelectedWorldIdFailure.Other -> result
                    }
                }
            }

            // Pipeline to get all permitted worlds
            val worldsPipeline = Pipeline.create<GetAllWorldsFailure, Int>()
                .pipe(GetAllPermittedWorldsForUserStep)
                .map { it.ownedWorlds + it.participantWorlds }

            val selectedWorldRef = pipeline("selectedWorld", userId, selectedWorldPipeline)
            val worldsRef = pipeline("worlds", userId, worldsPipeline)

            merge(
                "result",
                selectedWorldRef,
                worldsRef,
                MergeSteps.transform { selectedWorldId, worldsList ->
                    worlds(selectedWorldId, worldsList)
                }
            )
        }
    }
}