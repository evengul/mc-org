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
import app.mcorg.pipeline.world.DeleteWorldStepFailure
import app.mcorg.pipeline.world.GetWorldNameFailure
import app.mcorg.pipeline.world.GetWorldNameStep
import app.mcorg.pipeline.world.RemoveWorldPermissionsForAllUsersStep
import app.mcorg.pipeline.world.RemoveWorldPermissionsForAllUsersStepFailure
import app.mcorg.pipeline.world.UnSelectWorldForAllUsersStep
import app.mcorg.pipeline.world.UnSelectWorldForAllUsersStepFailure
import app.mcorg.pipeline.world.ValidateAvailableWorldName
import app.mcorg.pipeline.world.ValidateAvailableWorldNameFailure
import app.mcorg.pipeline.world.ValidateWorldNameLengthStep
import app.mcorg.pipeline.world.ValidateWorldNonEmptyStep
import app.mcorg.pipeline.world.WorldValidationFailure
import app.mcorg.presentation.configuration.WorldCommands
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.mappers.requiredInt
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
        .pipe(CreateWorldPermissionStep)
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
            is CreateWorldPermissionFailure.Other -> respond(InternalServerError, "World permission creation failed")
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
    val worldId = parameters.requiredInt("worldId")
    WorldCommands.selectWorld(userId, worldId).fold(
        { respondBadRequest("World could not be selected") },
        { clientRedirect("/app/worlds/$worldId/projects") }
    )
}

suspend fun ApplicationCall.handleGetWorld() {
    val worldId = getWorldId()
    respondRedirect("/app/worlds/$worldId/projects")
}