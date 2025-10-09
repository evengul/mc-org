package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.worldSettingsForm
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorld() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    executeParallelPipeline(
        onSuccess = { world: World ->
            respondHtml(createHTML().div {
                form {
                    worldSettingsForm(world)
                }
            })
        },
        onFailure = { failure: UpdateWorldFailures ->
            when (failure) {
                is UpdateWorldFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString { it.toString() }}")
                is UpdateWorldFailures.InsufficientPermissions ->
                    respondBadRequest("You don't have permission to update this world")
                is UpdateWorldFailures.DatabaseError ->
                    respondBadRequest("Unable to update world: Database error")
            }
        }
    ) {

        val validatePermissionsPipeline = pipeline(
            "validatePermissions",
            worldId,
            Pipeline { params ->
                ValidateUpdateWorldPermissionStep(user).process(params)
            }
        )

        val inputValidationRef = pipeline(
            "inputValidation",
            parameters,
            Pipeline { params ->
                ValidateUpdateWorldInputStep.process(params)
            }
        )

        val validatedInputRef = merge(
            "validatedInput",
            inputValidationRef,
            validatePermissionsPipeline
        ) { input, _ ->
            Result.success(input)
        }

        pipe(
            "updateAndRetrieve",
            validatedInputRef,
            Pipeline.create<UpdateWorldFailures, UpdateWorldInput>()
                .map { worldId to it }
                .pipe(UpdateWorldStep)
                .pipe(GetUpdatedWorldStep)
        )
    }
}