package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.worldVersionForm
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldVersion() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executeParallelPipeline(
        onSuccess = { world ->
            respondHtml(createHTML().div {
                form {
                    worldVersionForm(world)
                }
            })
        },
        onFailure = { failure: UpdateWorldVersionFailures ->
            when (failure) {
                is UpdateWorldVersionFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString { it.toString() }}")
                is UpdateWorldVersionFailures.DatabaseError ->
                    respondBadRequest("Unable to update world version: Database error")
            }
        }
    ) {

        val inputValidationRef = pipeline(
            "inputValidation",
            parameters,
            Pipeline { params ->
                ValidateWorldVersionInputStep.process(params)
            }
        )

        pipe(
            "updateAndRetrieve",
            inputValidationRef,
            Pipeline.create<UpdateWorldVersionFailures, UpdateWorldVersionInput>()
                .map { worldId to it }
                .pipe(UpdateWorldVersionStep)
                .pipe(GetUpdatedWorldForVersionStep)
        )
    }
}
