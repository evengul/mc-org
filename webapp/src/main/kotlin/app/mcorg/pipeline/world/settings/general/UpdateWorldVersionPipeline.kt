package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldVersion() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-version-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "World Version Updated",
                )
            })
        },
    ) {
        value(parameters)
            .step(ValidateWorldVersionInputStep)
            .step(UpdateWorldVersionStep(worldId))
    }
}

object ValidateWorldVersionInputStep : Step<Parameters, AppFailure.ValidationError, MinecraftVersion> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, MinecraftVersion> {
        val versionValidation = ValidationSteps.required(
            "version"
        ) { AppFailure.ValidationError(listOf(it)) }.process(input)

        // Additional validation for valid MinecraftVersion format
        val formatValidation = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "version",
            "Invalid Minecraft version format",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { versionString ->
                versionString != null && runCatching {
                    MinecraftVersion.fromString(versionString)
                }.isSuccess
            }
        ).process(input["version"]?.trim())

        val errors = mutableListOf<ValidationFailure>()

        if (versionValidation is Result.Failure) {
            errors.addAll(versionValidation.error.errors)
        }

        if (formatValidation is Result.Failure) {
            errors.addAll(formatValidation.error.errors)
        }

        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            val versionString = versionValidation.getOrNull()!!.trim()
            val minecraftVersion = MinecraftVersion.fromString(versionString)
            Result.success(minecraftVersion)
        }
    }
}

data class UpdateWorldVersionStep(val worldId: Int) : Step<MinecraftVersion, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: MinecraftVersion): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<MinecraftVersion>(
            SafeSQL.update("""
                UPDATE world 
                SET version = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """),
            parameterSetter = { statement, version ->
                statement.setString(1, version.toString())
                statement.setInt(2, worldId)
            }
        ).process(input).map { worldId }
    }
}
