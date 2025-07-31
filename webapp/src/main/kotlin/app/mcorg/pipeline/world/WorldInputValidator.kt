package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

data class WorldValidationResult(
    val name: String,
    val description: String,
    val version: MinecraftVersion
)

object WorldInputValidator {
    /**
     * Validates world input parameters and returns either validation failures or the validated data.
     * This is used by both create and update world validation steps.
     */
    suspend fun validateWorldInput(input: Parameters): Result<List<ValidationFailure>, WorldValidationResult> {
        val nameResult = ValidationSteps.required("name", { listOf(it) }).process(input)
        val descriptionResult = ValidationSteps.optional("description").process(input)
        val versionResult = ValidationSteps.validateCustom<List<ValidationFailure>, String?>(
            "version",
            "Invalid Minecraft version",
            errorMapper = { listOf(it) },
            predicate = {
                !it.isNullOrBlank() && runCatching {
                    MinecraftVersion.fromString(it)
                }.isSuccess
            }).process(input["version"]).map { MinecraftVersion.fromString(it!!) }

        val errors = mutableListOf<ValidationFailure>()
        if (nameResult is Result.Failure) {
            errors.addAll(nameResult.error)
        }
        if (versionResult is Result.Failure) {
            errors.addAll(versionResult.error)
        }

        if (errors.isNotEmpty()) {
            return Result.failure(errors.toList())
        }

        return Result.success(
            WorldValidationResult(
                name = nameResult.getOrNull()!!,
                description = descriptionResult.getOrNull() ?: "",
                version = versionResult.getOrNull()!!
            )
        )
    }
}
