package app.mcorg.pipeline.permission

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface AddWorldParticipantStepFailure : AddWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : AddWorldParticipantStepFailure
}

object AddWorldParticipantStep : Step<AddUserInput, AddWorldParticipantStepFailure, AddUserInput> {
    override suspend fun process(input: AddUserInput): Result<AddWorldParticipantStepFailure, AddUserInput> {
        return useConnection({ AddWorldParticipantStepFailure.Other(it) }) {
            prepareStatement("insert into permission (world_id, user_id, authority) values (?, ?, ?)")
                .apply {
                    setInt(1, input.worldId)
                    setInt(2, input.userId)
                    setInt(3, Authority.PARTICIPANT.level)
                }
                .executeUpdate()
            Result.success(input)
        }
    }
}