package app.mcorg.pipeline.idea.validators

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaDescriptionStep : Step<Parameters, ValidationFailure, String> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, String> {
        val description = input["description"]?.trim().orEmpty()

        // Optional, and with no lower bound. A private design may be a name and a category; a
        // twenty-character minimum is a rule about what deserves to be on the community hub, and
        // that belongs to publishing rather than to saving your own note.
        if (description.length > 5000) {
            return Result.Failure(ValidationFailure.InvalidLength("description", 0, 5000))
        }

        return Result.Success(description)
    }
}
