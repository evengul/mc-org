package app.mcorg.presentation.handler

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.mapValue
import app.mcorg.domain.pipeline.pipeWithContext
import app.mcorg.domain.pipeline.withContext
import app.mcorg.pipeline.world.CreateWorldFailure
import app.mcorg.pipeline.world.CreateWorldPermissionFailure
import app.mcorg.pipeline.world.CreateWorldPermissionStep
import app.mcorg.pipeline.world.CreateWorldPermissionStepInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.CreateWorldStepFailure
import app.mcorg.pipeline.world.DeleteWorldFailure
import app.mcorg.pipeline.world.DeleteWorldStep
import app.mcorg.pipeline.world.GetWorldNameFailure
import app.mcorg.pipeline.world.GetWorldNameStep
import app.mcorg.pipeline.world.GetWorldSelectionValue
import app.mcorg.pipeline.world.GetWorldSelectionValueFailure
import app.mcorg.pipeline.world.RemoveWorldPermissionsForAllUsersStep
import app.mcorg.pipeline.world.SelectWorldAfterCreation
import app.mcorg.pipeline.world.SelectWorldFailure
import app.mcorg.pipeline.world.SelectWorldStep
import app.mcorg.pipeline.world.SelectWorldStepFailure
import app.mcorg.pipeline.world.UnSelectWorldForAllUsersStep
import app.mcorg.pipeline.world.ValidateAvailableWorldName
import app.mcorg.pipeline.world.ValidateAvailableWorldNameFailure
import app.mcorg.pipeline.world.ValidateWorldNameLengthStep
import app.mcorg.pipeline.world.ValidateWorldNonEmptyStep
import app.mcorg.pipeline.world.WorldValidationFailure
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.templates.world.worlds
import app.mcorg.presentation.utils.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserId()
    val selectedWorld = usersApi.getProfile(userId)?.selectedWorld
    val permissions = permissionsApi.getPermissions(userId)
    val worlds = permissions.ownedWorlds + permissions.participantWorlds
    respondHtml(worlds(selectedWorld, worlds))
}

suspend fun ApplicationCall.handlePostWorld() {
    val userId = getUserId()

    val result = Pipeline.create<CreateWorldFailure, Parameters>()
        .pipe(GetWorldNameStep)
        .pipe(ValidateWorldNonEmptyStep)
        .pipe(ValidateWorldNameLengthStep)
        .pipe(ValidateAvailableWorldName)
        .pipe(CreateWorldStep)
        .withContext(userId)
        .mapValue { CreateWorldPermissionStepInput(it, Authority.OWNER) }
        .pipeWithContext(CreateWorldPermissionStep)
        .pipeWithContext(SelectWorldAfterCreation)
        .execute(receiveParameters())

    when (result) {
        is Result.Success -> clientRedirect("/app/worlds/${result.value}")
        is Result.Failure -> when(result.error) {
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
}

suspend fun ApplicationCall.handleDeleteWorld() {
    val worldId = getWorldId()

    val result = Pipeline.create<DeleteWorldFailure, Unit>()
        .withContext(worldId)
        .pipeWithContext(UnSelectWorldForAllUsersStep)
        .pipeWithContext(RemoveWorldPermissionsForAllUsersStep)
        .pipeWithContext(DeleteWorldStep)
        .execute(Unit)

    when (result) {
        is Result.Success -> respondEmptyHtml()
        is Result.Failure -> respondBadRequest("World could not be deleted")
    }
}

suspend fun ApplicationCall.handleSelectWorld() {
    val userId = getUserId()

    val result = Pipeline.create<SelectWorldFailure, Parameters>()
        .pipe(GetWorldSelectionValue)
        .withContext(userId)
        .pipe(SelectWorldStep)
        .execute(parameters)

    when (result) {
        is Result.Success -> clientRedirect("/app/worlds/${result.value}/projects")
        is Result.Failure -> when (result.error) {
            is GetWorldSelectionValueFailure.NotFound -> respondBadRequest("Parameter worldId is required")
            is GetWorldSelectionValueFailure.NotInteger -> respondBadRequest("Parameter worldId must be an integer")
            is SelectWorldStepFailure.Other -> respond(InternalServerError, "World selection failed")
        }
    }
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}