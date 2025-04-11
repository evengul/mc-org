package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface RemoveUserAssignmentsInWorldFailure : RemoveWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : RemoveUserAssignmentsInWorldFailure
}

data class RemoveUserAssignmentsInWorldInput(
    val userId: Int,
    val worldId: Int
)

object RemoveUserProjectAssignmentsInWorld : Step<RemoveUserAssignmentsInWorldInput, RemoveUserAssignmentsInWorldFailure, RemoveUserAssignmentsInWorldInput> {
    override suspend fun process(input: RemoveUserAssignmentsInWorldInput): Result<RemoveUserAssignmentsInWorldFailure, RemoveUserAssignmentsInWorldInput> {
        return useConnection({ RemoveUserAssignmentsInWorldFailure.Other(it) }) {
            prepareStatement("update project set assignee = null where assignee = ? and world_id = ?")
                .apply {
                    setInt(1, input.userId)
                    setInt(2, input.worldId)
                }
                .executeUpdate()
            return@useConnection Result.success(input)
        }
    }
}

object RemoveUserTaskAssignmentsInWorld : Step<RemoveUserAssignmentsInWorldInput, RemoveUserAssignmentsInWorldFailure, RemoveUserAssignmentsInWorldInput> {
    override suspend fun process(input: RemoveUserAssignmentsInWorldInput): Result<RemoveUserAssignmentsInWorldFailure, RemoveUserAssignmentsInWorldInput> {
        return useConnection({ RemoveUserAssignmentsInWorldFailure.Other(it) }) {
            prepareStatement("update task set assignee = null where assignee = ? and project_id in (select id from project where world_id = ?)")
                .apply {
                    setInt(1, input.userId)
                    setInt(2, input.worldId)
                }
                .executeUpdate()
            return@useConnection Result.success(input)
        }
    }
}