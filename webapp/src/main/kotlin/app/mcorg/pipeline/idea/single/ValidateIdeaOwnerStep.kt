package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class ValidateIdeaOwnerInput(
    val ideaId: Int,
    val userId: Int
)

sealed interface ValidateIdeaOwnerFailure {
    object NotOwner : ValidateIdeaOwnerFailure
    object DatabaseError : ValidateIdeaOwnerFailure
}

object ValidateIdeaOwnerStep : Step<ValidateIdeaOwnerInput, ValidateIdeaOwnerFailure, Unit> {
    override suspend fun process(input: ValidateIdeaOwnerInput): Result<ValidateIdeaOwnerFailure, Unit> {
        return DatabaseSteps.query<ValidateIdeaOwnerInput, ValidateIdeaOwnerFailure.DatabaseError, Boolean>(
            sql = SafeSQL.select("SELECT COUNT(*) > 0 AS is_owner FROM ideas WHERE id = ? AND created_by = ?"),
            parameterSetter = { statement, input ->
                statement.setInt(1, input.ideaId)
                statement.setInt(2, input.userId)
            },
            errorMapper = { ValidateIdeaOwnerFailure.DatabaseError },
            resultMapper = { resultSet ->
                 resultSet.next() && resultSet.getBoolean("is_owner")
            }
        ).process(input)
            .map {
                if (it) {
                    Result.success()
                } else {
                    Result.failure(ValidateIdeaOwnerFailure.NotOwner)
                }
            }
    }
}