package app.mcorg.pipeline.invitation

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import java.sql.ResultSet
import java.time.ZonedDateTime

object GetUserInvitationsStep : Step<Int, DatabaseFailure, List<Invite>> {
    override suspend fun process(input: Int): Result<DatabaseFailure, List<Invite>> {
        return DatabaseSteps.query<Int, DatabaseFailure, List<Invite>>(
            sql = SafeSQL.select("""
                SELECT 
                    i.id,
                    i.world_id,
                    w.name as world_name,
                    i.from_user_id,
                    mp_from.username as from_username,
                    i.to_user_id,
                    mp_to.username as to_username,
                    i.role,
                    i.created_at,
                    i.status,
                    i.status_reached_at
                FROM invites i
                INNER JOIN world w ON i.world_id = w.id
                INNER JOIN users u_from ON i.from_user_id = u_from.id
                INNER JOIN users u_to ON i.to_user_id = u_to.id
                LEFT JOIN minecraft_profiles mp_from ON i.from_user_id = mp_from.user_id
                LEFT JOIN minecraft_profiles mp_to ON i.to_user_id = mp_to.user_id
                WHERE i.to_user_id = ? AND i.status = 'PENDING'
                ORDER BY i.created_at DESC
            """.trimIndent()),
            parameterSetter = { statement, userId ->
                statement.setInt(1, userId)
            },
            errorMapper = { it },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toInvite())
                    }
                }
            }
        ).process(input)
    }
}

fun ResultSet.toInvite() = Invite(
    id = getInt("id"),
    worldId = getInt("world_id"),
    worldName = getString("world_name"),
    from = getInt("from_user_id"),
    fromUsername = getString("from_username"),
    to = getInt("to_user_id"),
    toUsername = getString("to_username"),
    role = Role.fromString(getString("role")),
    createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
    expiresAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC).plusDays(7), // Assuming 7-day expiration
    status = mapInviteStatus(getString("status"), getTimestamp("status_reached_at").toInstant().atZone(java.time.ZoneOffset.UTC))
)

private fun mapInviteStatus(status: String, reachedAt: ZonedDateTime): InviteStatus {
    return when (status) {
        "PENDING" -> InviteStatus.Pending(reachedAt)
        "ACCEPTED" -> InviteStatus.Accepted(reachedAt)
        "DECLINED" -> InviteStatus.Declined(reachedAt)
        "EXPIRED" -> InviteStatus.Expired(reachedAt)
        "CANCELLED" -> InviteStatus.Cancelled(reachedAt)
        else -> throw IllegalArgumentException("Unknown invite status: $status")
    }
}
