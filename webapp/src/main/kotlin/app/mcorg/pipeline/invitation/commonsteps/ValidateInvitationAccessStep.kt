package app.mcorg.pipeline.invitation.commonsteps

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class ValidateInvitationAccessStep(val userId: Int) : Step<Int, AppFailure, Pair<Int, String>> {
    override suspend fun process(input: Int): Result<AppFailure, Pair<Int, String>> {
        return DatabaseSteps.query<Int, Pair<Int, String>>(
            sql = SafeSQL.select("""
                SELECT i.world_id, w.name as world_name 
                FROM invites i 
                JOIN world w ON i.world_id = w.id 
                WHERE i.id = ? AND i.to_user_id = ?
            """.trimIndent()),
            parameterSetter = { stmt, inviteId ->
                stmt.setInt(1, inviteId)
                stmt.setInt(2, userId)
            },
            resultMapper = { rs ->
                rs.next()
                Pair(rs.getInt("world_id"), rs.getString("world_name"))
            }
        ).process(input)
    }
}