package app.mcorg.pipeline.world

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure

sealed interface ValidateWorldMemberRoleFailure {
    data object InsufficientPermissions : ValidateWorldMemberRoleFailure
    data class DatabaseError(val cause: DatabaseFailure) : ValidateWorldMemberRoleFailure
}

data class ValidateWorldMemberRole(val user: TokenProfile, val role: Role) : Step<Int, ValidateWorldMemberRoleFailure, Int> {
    override suspend fun process(input: Int): Result<ValidateWorldMemberRoleFailure, Int> {
        return DatabaseSteps.query<Int, ValidateWorldMemberRoleFailure, Boolean>(
            sql = hasWorldRoleOrHigherQuery,
            parameterSetter = { statement, worldId ->
                statement.setInt(1, user.id)
                statement.setInt(2, worldId)
                statement.setInt(3, role.level)
            },
            errorMapper = { ValidateWorldMemberRoleFailure.DatabaseError(it) },
            resultMapper = {
                if (it.next()) {
                    it.getBoolean("has_role")
                } else {
                    false
                }
            }
        ).process(input).flatMap {
            if (it) {
                Result.success(input)
            } else {
                Result.failure(ValidateWorldMemberRoleFailure.InsufficientPermissions)
            }
        }
    }
}
