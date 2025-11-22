package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaNameStep : Step<Parameters, ValidationFailure, String> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, String> {
        val name = input["name"]?.trim()

        if (name.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("name"))
        }

        if (name.length !in 3..100) {
            return Result.Failure(ValidationFailure.InvalidLength("name", 3, 100))
        }

        return Result.Success(name)
    }
}