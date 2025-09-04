package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

object ValidateWorldNameInputStep : Step<Parameters, UpdateWorldNameFailures, UpdateWorldNameInput> {
    override suspend fun process(input: Parameters): Result<UpdateWorldNameFailures, UpdateWorldNameInput> {
        val nameValidation = ValidationSteps.required(
            "name",
            { UpdateWorldNameFailures.ValidationError(listOf(it)) }
        ).process(input)

        // Additional validation for name length and uniqueness constraints
        val lengthValidation = ValidationSteps.validateCustom<UpdateWorldNameFailures.ValidationError, String?>(
            "name",
            "World name must be between 3 and 100 characters",
            errorMapper = { UpdateWorldNameFailures.ValidationError(listOf(it)) },
            predicate = { it != null && it.length in 3..100 }
        ).process(input["name"])

        val errors = mutableListOf<ValidationFailure>()

        if (nameValidation is Result.Failure) {
            errors.addAll(nameValidation.error.errors)
        }

        if (lengthValidation is Result.Failure) {
            errors.addAll(lengthValidation.error.errors)
        }

        return if (errors.isNotEmpty()) {
            Result.failure(UpdateWorldNameFailures.ValidationError(errors))
        } else {
            Result.success(UpdateWorldNameInput(nameValidation.getOrNull()!!.trim()))
        }
    }
}
