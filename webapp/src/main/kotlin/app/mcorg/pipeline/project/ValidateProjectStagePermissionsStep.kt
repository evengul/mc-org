package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.UpdateProjectStageFailures

data class ValidateProjectStagePermissionsInput(
    val user: TokenProfile,
    val projectId: Int
)

object ValidateProjectStagePermissionsStep : Step<ValidateProjectStagePermissionsInput, UpdateProjectStageFailures, ValidateProjectStagePermissionsInput> {
    override suspend fun process(input: ValidateProjectStagePermissionsInput): Result<UpdateProjectStageFailures, ValidateProjectStagePermissionsInput> {
        // Check if user has Member+ role in the world that contains this project
        return DatabaseSteps.query<ValidateProjectStagePermissionsInput, UpdateProjectStageFailures, Boolean>(
            sql = SafeSQL.select("""
                SELECT wm.world_role
                FROM projects p
                JOIN world_members wm ON p.world_id = wm.world_id
                WHERE p.id = ? AND wm.user_id = ?
            """),
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
                statement.setInt(2, queryInput.user.id)
            },
            errorMapper = { UpdateProjectStageFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    val role = resultSet.getInt("world_role")
                    // Member+ role required (role <= 100, where Owner=0, Admin=10, Member=100)
                    role <= 100
                } else {
                    false // User not found in world or project doesn't exist
                }
            }
        ).process(input).flatMap { hasPermission ->
            if (hasPermission) {
                Result.success(input)
            } else {
                Result.failure(UpdateProjectStageFailures.InsufficientPermissions)
            }
        }
    }
}
