package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.worldDescriptionForm
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldDescription() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executeParallelPipeline(
        onSuccess = { world ->
            respondHtml(createHTML().div {
                form {
                    worldDescriptionForm(world)
                }
            })
        },
        onFailure = { failure: UpdateWorldDescriptionFailures ->
            when (failure) {
                is UpdateWorldDescriptionFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString { it.toString() }}")
                is UpdateWorldDescriptionFailures.DatabaseError ->
                    respondBadRequest("Unable to update world description: Database error")
            }
        }
    ) {

        val inputValidationRef = pipeline(
            "inputValidation",
            parameters,
            Pipeline { params ->
                ValidateWorldDescriptionInputStep.process(params)
            }
        )

        pipe(
            "updateAndRetrieve",
            inputValidationRef,
            Pipeline.create<UpdateWorldDescriptionFailures, UpdateWorldDescriptionInput>()
                .map { worldId to it }
                .pipe(UpdateWorldDescriptionStep)
                .pipe(GetUpdatedWorldForDescriptionStep)
        )
    }
}
