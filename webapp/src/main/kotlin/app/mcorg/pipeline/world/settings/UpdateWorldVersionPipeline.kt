package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldVersion() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executeParallelPipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-version-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "World Version Updated",
                )
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
        )
    }
}
