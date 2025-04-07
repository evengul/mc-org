package app.mcorg.pipeline.permission

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface VerifyParticipantAdderIsAdminFailure : AddWorldParticipantFailure, RemoveWorldParticipantFailure {
    data object NotAdmin : VerifyParticipantAdderIsAdminFailure
    data class Other(val failure: DatabaseFailure) : VerifyParticipantAdderIsAdminFailure
}

data class VerifyUserIsAdminInWorldStep(
    val worldId: Int,
    val adminId: Int
)

object VerifyParticipantAdderIsAdmin : Step<VerifyUserIsAdminInWorldStep, VerifyParticipantAdderIsAdminFailure, Unit> {
    override fun process(input: VerifyUserIsAdminInWorldStep): Result<VerifyParticipantAdderIsAdminFailure, Unit> {
        return useConnection({ VerifyParticipantAdderIsAdminFailure.Other(it) }) {
            prepareStatement("select 1 from permission where world_id = ? and user_id = ? and authority <= ? limit 1")
                .apply {
                    setInt(1, input.worldId)
                    setInt(2, input.adminId)
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