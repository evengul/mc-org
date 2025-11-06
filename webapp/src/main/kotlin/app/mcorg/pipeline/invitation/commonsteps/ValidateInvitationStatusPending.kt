package app.mcorg.pipeline.invitation.commonsteps

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure

class ValidateInvitationPendingStep<I> : Step<Pair<Int, I>, AppFailure, I> {
    override suspend fun process(input: Pair<Int, I>): Result<AppFailure, I> {
        val result = DatabaseSteps.query<Int, Pair<String, Role>>(
            sql = SafeSQL.select("SELECT status, role FROM invites WHERE id = ?"),
            parameterSetter = { stmt, inviteId ->
                stmt.setInt(1, inviteId)
            },
            resultMapper = { rs ->
                rs.next()
                Pair(rs.getString("status"), Role.valueOf(rs.getString("role")))
            }
        ).process(input.first)

        return when (result) {
            is Result.Success -> {
                val inviteInfo = result.value
                if (inviteInfo.first != "PENDING") {
                    Result.failure(AppFailure.ValidationError(listOf(
                        ValidationFailure.CustomValidation("invite", "Invite is not in pending status")
                    )))
                } else {
                    Result.success(input.second)
                }
            }
            is Result.Failure -> result
        }
    }
}