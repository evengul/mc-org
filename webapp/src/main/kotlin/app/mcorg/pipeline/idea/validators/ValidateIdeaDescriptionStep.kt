package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaDescriptionStep : Step<Parameters, ValidationFailure, String> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, String> {
        val description = input["description"]?.trim()

        if (description.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("description"))
        }

        if (description.length !in 20..5000) {
            return Result.Failure(ValidationFailure.InvalidLength("description", 20, 5000))
        }

        return Result.Success(description)
    }
}