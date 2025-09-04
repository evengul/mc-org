package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

object ValidateWorldDescriptionInputStep : Step<Parameters, UpdateWorldDescriptionFailures, UpdateWorldDescriptionInput> {
    override suspend fun process(input: Parameters): Result<UpdateWorldDescriptionFailures, UpdateWorldDescriptionInput> {
        val descriptionValidation = ValidationSteps.optional("description")
            .process(input)

        // Additional validation for description length
        val lengthValidation = ValidationSteps.validateCustom<UpdateWorldDescriptionFailures.ValidationError, String?>(
            "description",
            "World description must be 1000 characters or less",
            errorMapper = { UpdateWorldDescriptionFailures.ValidationError(listOf(it)) },
            predicate = { it == null || it.length <= 1000 }
        ).process(input["description"])

        val errors = mutableListOf<ValidationFailure>()

        if (lengthValidation is Result.Failure) {
            errors.addAll(lengthValidation.error.errors)
        }

        return if (errors.isNotEmpty()) {
            Result.failure(UpdateWorldDescriptionFailures.ValidationError(errors))
        } else {
            val description = descriptionValidation.getOrNull() ?: ""
            Result.success(UpdateWorldDescriptionInput(description.trim()))
        }
    }
}
