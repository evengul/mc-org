package app.mcorg.pipeline.world

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.HandleGetWorldFailure

/**
 * Database step to retrieve all invitations for a specific world
 * Used in world settings to display pending and historical invitations
 */
object GetWorldInvitationsStep : Step<Int, HandleGetWorldFailure, List<Invite>> {
    override suspend fun process(input: Int): Result<HandleGetWorldFailure, List<Invite>> {
        val worldId = input

        return DatabaseSteps.query<Int, HandleGetWorldFailure, List<Invite>>(
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
                    i.created_at + INTERVAL '7 days' as expires_at,
                    i.status,
                    i.status_reached_at
                FROM invites i
                JOIN world w ON i.world_id = w.id
                JOIN users u_from ON i.from_user_id = u_from.id
                JOIN minecraft_profiles mp_from ON u_from.id = mp_from.user_id
                JOIN users u_to ON i.to_user_id = u_to.id
                JOIN minecraft_profiles mp_to ON u_to.id = mp_to.user_id
                WHERE i.world_id = ?
                ORDER BY i.created_at DESC
            """.trimIndent()),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
            },
            errorMapper = { HandleGetWorldFailure.SystemError(it.javaClass.simpleName) },
            resultMapper = { rs ->
                val invitations = mutableListOf<Invite>()
                while (rs.next()) {
                    val statusString = rs.getString("status")
                    val statusReachedAt = rs.getTimestamp("status_reached_at").toInstant()
                        .atZone(java.time.ZoneOffset.UTC)

                    val status = when (statusString) {
                        "PENDING" -> InviteStatus.Pending(statusReachedAt)
                        "ACCEPTED" -> InviteStatus.Accepted(statusReachedAt)
                        "DECLINED" -> InviteStatus.Declined(statusReachedAt)
                        "EXPIRED" -> InviteStatus.Expired(statusReachedAt)
                        "CANCELLED" -> InviteStatus.Cancelled(statusReachedAt)
                        else -> InviteStatus.Pending(statusReachedAt) // Default fallback
                    }

                    val invite = Invite(
                        id = rs.getInt("id"),
                        worldId = rs.getInt("world_id"),
                        worldName = rs.getString("world_name"),
                        from = rs.getInt("from_user_id"),
                        fromUsername = rs.getString("from_username"),
                        to = rs.getInt("to_user_id"),
                        toUsername = rs.getString("to_username"),
                        role = Role.valueOf(rs.getString("role")),
                        createdAt = rs.getTimestamp("created_at").toInstant()
                            .atZone(java.time.ZoneOffset.UTC),
                        expiresAt = rs.getTimestamp("expires_at").toInstant()
                            .atZone(java.time.ZoneOffset.UTC),
                        status = status
                    )
                    invitations.add(invite)
                }
                invitations
            }
        ).process(worldId)
    }
}
