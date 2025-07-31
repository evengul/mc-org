package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.CreateWorldFailures
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.home.worldsView
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCreateWorld() {
    val parameters = this.receiveParameters()
    val user = this.getUser()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                worldsView(it)
            })
        },
        onFailure = {
            respondBadRequest("Unable to create world: ${it.javaClass.simpleName}")
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateWorldInputStep)
            .step(CreateWorldStep(user))
            .step(Step.value(user.id))
            .step(object : Step<Int, CreateWorldFailures.DatabaseError, List<World>> {
                override suspend fun process(input: Int): Result<CreateWorldFailures.DatabaseError, List<World>> {
                    return GetPermittedWorldsStep().process(user.id).mapError { CreateWorldFailures.DatabaseError }
                }
            })
    }
}