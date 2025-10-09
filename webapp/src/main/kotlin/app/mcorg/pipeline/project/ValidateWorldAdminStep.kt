package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.CreateProjectFailures
import app.mcorg.pipeline.world.hasWorldRoleOrHigherQuery

data class ValidateWorldAdminStep(val user: TokenProfile, val worldId: Int) : Step<CreateProjectInput, CreateProjectFailures, CreateProjectInput> {
    override suspend fun process(input: CreateProjectInput): Result<CreateProjectFailures, CreateProjectInput> {
        return DatabaseSteps.query<CreateProjectInput, CreateProjectFailures, Boolean>(
            hasWorldRoleOrHigherQuery,
            parameterSetter = { statement, membershipInput ->
                statement.setInt(1, user.id)
                statement.setInt(2, worldId)
                statement.setInt(3, Role.ADMIN.level)
            },
            errorMapper = { CreateProjectFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getBoolean("has_role")
                } else {
                    false
                }
            }
        ).process(input).flatMap { hasPermission ->
            if (hasPermission) {
                Result.success(input)
            } else {
                Result.failure(CreateProjectFailures.InsufficientPermissions)
            }
        }
    }
}
