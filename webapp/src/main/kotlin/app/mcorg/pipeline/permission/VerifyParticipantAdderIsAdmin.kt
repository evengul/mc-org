package app.mcorg.pipeline.permission

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface VerifyParticipantAdderIsAdminFailure : AddWorldParticipantFailure, RemoveWorldParticipantFailure, GetOtherUsersFailure {
    data object NotAdmin : VerifyParticipantAdderIsAdminFailure
    data class Other(val failure: DatabaseFailure) : VerifyParticipantAdderIsAdminFailure
}

object VerifyParticipantAdderIsAdmin : Step<WorldUser, VerifyParticipantAdderIsAdminFailure, Unit> {
    override suspend fun process(input: WorldUser): Result<VerifyParticipantAdderIsAdminFailure, Unit> {
        return useConnection({ VerifyParticipantAdderIsAdminFailure.Other(it) }) {
            prepareStatement("select 1 from permission where world_id = ? and user_id = ? and authority <= ? limit 1")
                .apply {
                    setInt(1, input.worldId)
                    setInt(2, input.userId)
                    setInt(3, Authority.ADMIN.level)
                }
                .executeQuery()
                .apply {
                    if (!next()) {
                        return@useConnection Result.failure(VerifyParticipantAdderIsAdminFailure.NotAdmin)
                    }
                }
            return@useConnection Result.success(Unit)
        }
    }
}