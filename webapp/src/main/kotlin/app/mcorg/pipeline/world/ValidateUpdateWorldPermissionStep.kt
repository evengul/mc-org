package app.mcorg.pipeline.world

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step

data class ValidateUpdateWorldPermissionStep(val user: TokenProfile) : Step<Int, UpdateWorldFailures, Int> {
    override suspend fun process(input: Int): Result<UpdateWorldFailures, Int> {
        return ValidateWorldMemberRole(user, Role.ADMIN).process(input)
            .mapError { failure ->
                when (failure) {
                    is ValidateWorldMemberRoleFailure.InsufficientPermissions -> UpdateWorldFailures.InsufficientPermissions
                    is ValidateWorldMemberRoleFailure.DatabaseError -> UpdateWorldFailures.DatabaseError(failure.cause)
                }
            }
    }
}
