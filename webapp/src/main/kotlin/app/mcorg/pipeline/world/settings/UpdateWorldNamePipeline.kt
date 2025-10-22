package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.button
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldName() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executeParallelPipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-name-updated-success-alert",
                    type = app.mcorg.presentation.templated.layout.alert.AlertType.SUCCESS,
                    title = "World Name Updated",
                    autoClose = true
                )
            } + createHTML().button {
                hxOutOfBands("innerHTML:#button-back")
                + "Back to $it"
            })
        },
        onFailure = { failure: UpdateWorldNameFailures ->
            when (failure) {
                is UpdateWorldNameFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString { it.toString() }}")
                is UpdateWorldNameFailures.DatabaseError ->
                    respondBadRequest("Unable to update world name: Database error")
                is UpdateWorldNameFailures.NameAlreadyExists ->
                    respondBadRequest("A world with this name already exists")
            }
        }
    ) {

        val inputValidationRef = pipeline(
            "inputValidation",
            parameters,
            Pipeline { params ->
                ValidateWorldNameInputStep.process(params)
            }
        )

        pipe(
            "updateAndRetrieve",
            inputValidationRef,
            Pipeline.create<UpdateWorldNameFailures, UpdateWorldNameInput>()
                .map { worldId to it }
                .pipe(UpdateWorldNameStep)
        )
    }
}
