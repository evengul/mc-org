package app.mcorg.pipeline.world.invitations

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class CountWorldInvitationsResult(
    val all: Int,
    val pending: Int,
    val accepted: Int,
    val declined: Int,
    val expired: Int,
    val cancelled: Int
)

data class CountWorldInvitationsStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, CountWorldInvitationsResult> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, CountWorldInvitationsResult> {
        return DatabaseSteps.query<Unit, CountWorldInvitationsResult>(
            sql = SafeSQL.select("""
                SELECT 
                    SUM(CASE WHEN i.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                    SUM(CASE WHEN i.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted_count,
                    SUM(CASE WHEN i.status = 'DECLINED' THEN 1 ELSE 0 END) AS declined_count,
                    SUM(CASE WHEN i.status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired_count,
                    SUM(CASE WHEN i.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
                    COUNT(i.id) AS count
                FROM invites i
                WHERE i.world_id = ?
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    CountWorldInvitationsResult(
                        all = rs.getInt("count"),
                        pending = rs.getInt("pending_count"),
                        accepted = rs.getInt("accepted_count"),
                        declined = rs.getInt("declined_count"),
                        expired = rs.getInt("expired_count"),
                        cancelled = rs.getInt("cancelled_count")
                    )
                } else {
                    CountWorldInvitationsResult(
                        all = 0,
                        pending = 0,
                        accepted = 0,
                        declined = 0,
                        expired = 0,
                        cancelled = 0
                    )
                }
            }
        ).process(input)
    }
}