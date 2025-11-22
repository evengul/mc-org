package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaDifficultyStep : Step<Parameters, ValidationFailure, IdeaDifficulty> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, IdeaDifficulty> {
        val difficulty = input["difficulty"]

        if (difficulty.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("difficulty"))
        }

        if (!IdeaDifficulty.entries.any { it.name == difficulty }) {
            return Result.Failure(ValidationFailure.InvalidValue("difficulty", IdeaDifficulty.entries.map { it.name }))
        }

        return Result.Success(IdeaDifficulty.valueOf(difficulty))
    }
}