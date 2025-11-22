package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaCategoryStep : Step<Parameters, ValidationFailure, IdeaCategory> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, IdeaCategory> {
        val categoryStr = input["category"]

        if (categoryStr.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("category"))
        }

        if (!IdeaCategory.entries.any { it.name == categoryStr }) {
            return Result.Failure(ValidationFailure.InvalidValue("category", IdeaCategory.entries.map { it.name }))
        }

        return Result.Success(IdeaCategory.valueOf(categoryStr))
    }
}