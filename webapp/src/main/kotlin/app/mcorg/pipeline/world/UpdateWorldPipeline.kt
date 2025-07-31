package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.worldSettingsForm
import app.mcorg.presentation.utils.getUser
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
        // Pipeline 1: Retrieve the world ID from URL parameters
        val worldIdRef = pipeline(
            "worldId",
            this@handleUpdateWorld.parameters,
            Pipeline { params ->
                getWorldIdStep.process(params).mapError { failure ->
                    when (failure) {
                        is HandleGetWorldFailure.WorldIdRequired ->
                            UpdateWorldFailures.ValidationError(listOf(ValidationFailure.MissingParameter("worldId")))
                        is HandleGetWorldFailure.InvalidWorldId ->
                            UpdateWorldFailures.ValidationError(listOf(ValidationFailure.InvalidFormat("worldId")))
                        else ->
                            UpdateWorldFailures.DatabaseError(DatabaseFailure.NotFound)
                    }
                }
            }
        )

        // Pipeline 2b: Validate form input (independent)
        val inputValidationRef = pipeline(
            "inputValidation",
            parameters,
            Pipeline { params ->
                ValidateUpdateWorldInputStep.process(params)
            }
        )

        // Merge Pipeline 1 and 2b to check permissions and combine with input validation
        val validatedDataRef = merge("validatedData", worldIdRef, inputValidationRef) { worldId: Int, validatedInput: UpdateWorldInput ->
            // First check permissions with the worldId
            ValidateUpdateWorldPermissionStep(user).process(worldId)
                .map {
                    // If permission check passes, return both pieces of data for the update
                    Pair(worldId, validatedInput)
                }
        }

        // Pipeline 3: Perform the update and retrieve updated world

        pipe(
            "updateAndRetrieve",
            validatedDataRef,
            Pipeline.create<UpdateWorldFailures, Pair<Int, UpdateWorldInput>>()
                .pipe(UpdateWorldStep)
                .pipe(GetUpdatedWorldStep)
        )
    }
}