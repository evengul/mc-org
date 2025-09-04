package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

object ValidateWorldVersionInputStep : Step<Parameters, UpdateWorldVersionFailures, UpdateWorldVersionInput> {
    override suspend fun process(input: Parameters): Result<UpdateWorldVersionFailures, UpdateWorldVersionInput> {
        val versionValidation = ValidationSteps.required(
            "version",
            { UpdateWorldVersionFailures.ValidationError(listOf(it)) }
        ).process(input)

        // Additional validation for valid MinecraftVersion format
        val formatValidation = ValidationSteps.validateCustom<UpdateWorldVersionFailures.ValidationError, String?>(
            "version",
            "Invalid Minecraft version format",
            errorMapper = { UpdateWorldVersionFailures.ValidationError(listOf(it)) },
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
            Result.failure(UpdateWorldVersionFailures.ValidationError(errors))
        } else {
            val versionString = versionValidation.getOrNull()!!.trim()
            val minecraftVersion = MinecraftVersion.fromString(versionString)
            Result.success(UpdateWorldVersionInput(minecraftVersion))
        }
    }
}
