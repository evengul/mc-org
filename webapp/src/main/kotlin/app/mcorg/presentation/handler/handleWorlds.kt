package app.mcorg.presentation.handler

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.CreateWorldFailure
import app.mcorg.pipeline.failure.CreateWorldPermissionFailure
import app.mcorg.pipeline.failure.CreateWorldStepFailure
import app.mcorg.pipeline.world.CreateWorldPermissionStep
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.failure.DeleteWorldFailure
import app.mcorg.pipeline.world.DeleteWorldStep
import app.mcorg.pipeline.world.GetAllPermittedWorldsForUserStep
import app.mcorg.pipeline.failure.GetAllWorldsFailure
import app.mcorg.pipeline.failure.GetSelectedWorldIdFailure
import app.mcorg.pipeline.failure.GetWorldNameFailure
import app.mcorg.pipeline.failure.GetWorldSelectionValueFailure
import app.mcorg.pipeline.world.GetSelectedWorldIdStep
import app.mcorg.pipeline.world.GetWorldNameStep
import app.mcorg.pipeline.world.GetWorldSelectionValue
import app.mcorg.pipeline.world.RemoveWorldPermissionsForAllUsersStep
import app.mcorg.pipeline.failure.SelectWorldFailure
import app.mcorg.pipeline.failure.SelectWorldStepFailure
import app.mcorg.pipeline.failure.ValidateAvailableWorldNameFailure
import app.mcorg.pipeline.failure.WorldValidationFailure
import app.mcorg.pipeline.world.SelectWorldStep
import app.mcorg.pipeline.world.UnSelectWorldForAllUsersStep
import app.mcorg.pipeline.world.ValidateAvailableWorldName
import app.mcorg.pipeline.world.ValidateWorldNameLengthStep
import app.mcorg.pipeline.world.ValidateWorldNonEmptyStep
import app.mcorg.presentation.templates.world.worlds
import app.mcorg.presentation.utils.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserId()
    var selectedWorldId: Int? = null

    Pipeline.create<GetAllWorldsFailure, Int>()
        .pipe(GetSelectedWorldIdStep) { selectedWorldId = it }
        .recover { if (it is GetSelectedWorldIdFailure.NoWorldSelected) Result.success(userId) else Result.failure(it) }
        .map { userId }
        .pipe(GetAllPermittedWorldsForUserStep)
        .map { it.ownedWorlds + it.participantWorlds }
        .map { worlds(selectedWorldId, it) }
        .fold(
            input = userId,
            onSuccess = { respondHtml(it) },
            onFailure = { respond(InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handlePostWorld() {
    val user = getUser()

    Pipeline.create<CreateWorldFailure, Parameters>()
        .pipe(GetWorldNameStep)
        .pipe(ValidateWorldNonEmptyStep)
        .pipe(ValidateWorldNameLengthStep)
        .pipe(ValidateAvailableWorldName)
        .pipe(CreateWorldStep(user.username))
        .pipe(CreateWorldPermissionStep(user.id, Authority.OWNER))
        .pipe(SelectWorldStep(user.id))
        .fold(
            input = receiveParameters(),
            onSuccess = { clientRedirect("/app/worlds/$it") },
            onFailure = {
                when(it) {
                    is GetWorldNameFailure.NotPresent -> respondBadRequest("Parameter worldName is required")
                    is WorldValidationFailure.WorldNameEmpty -> respondBadRequest("Parameter worldName is empty")
                    is WorldValidationFailure.WorldNameTooLong -> respondBadRequest("Parameter worldName is too long")
                    is ValidateAvailableWorldNameFailure.AlreadyExists -> respondBadRequest("World with this name already exists")
                    is ValidateAvailableWorldNameFailure.Other -> respond(InternalServerError, "World name validation failed")
                    is CreateWorldStepFailure.Other -> respond(InternalServerError, "World creation failed")
                    is SelectWorldStepFailure.Other -> respond(InternalServerError, "Could not select world after creation")
                    is CreateWorldPermissionFailure.Other -> respond(InternalServerError, "World permission creation failed")
                    is GetWorldSelectionValueFailure.NotFound -> throw IllegalStateException("This step should never be called")
                    is GetWorldSelectionValueFailure.NotInteger -> throw IllegalStateException("This step should never be called")
                }
            }
        )
}

suspend fun ApplicationCall.handleDeleteWorld() {
    val worldId = getWorldId()

    Pipeline.create<DeleteWorldFailure, Unit>()
        .pipe(UnSelectWorldForAllUsersStep(worldId))
        .pipe(RemoveWorldPermissionsForAllUsersStep(worldId))
        .pipe(DeleteWorldStep(worldId))
        .fold(
            input = Unit,
            onSuccess = { respondEmptyHtml() },
            onFailure = { respondBadRequest("World could not be deleted") }
        )
}

suspend fun ApplicationCall.handleSelectWorld() {
    val userId = getUserId()

    Pipeline.create<SelectWorldFailure, Parameters>()
        .pipe(GetWorldSelectionValue)
        .pipe(SelectWorldStep(userId))
        .fold(
            input = parameters,
            onSuccess = { clientRedirect("/app/worlds/${it}/projects") },
            onFailure = {
                when(it) {
                    is GetWorldSelectionValueFailure.NotFound -> respondBadRequest("Parameter worldId is required")
                    is GetWorldSelectionValueFailure.NotInteger -> respondBadRequest("Parameter worldId must be an integer")
                    is SelectWorldStepFailure.Other -> respond(InternalServerError, "World selection failed")
                }
            }
        )
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}