package app.mcorg.pipeline.world

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.HandleGetWorldFailure

data class GetWorldInvitationsInput(
    val worldId: Int,
    val statusFilter: StatusFilter = StatusFilter.PENDING
) {
    enum class StatusFilter {
        ALL,
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED,
        CANCELLED
    }
}

data class GetWorldInvitationsOutput(
    val filteredInvitations: List<Invite>,
    val invitationsCount: Map<GetWorldInvitationsInput.StatusFilter, Int>,
)

private val worldInvitationsCountStep = DatabaseSteps.query<GetWorldInvitationsInput, HandleGetWorldFailure, Map<GetWorldInvitationsInput.StatusFilter, Int>>(
    sql = SafeSQL.select("""
        SELECT 
            SUM(CASE WHEN i.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
            SUM(CASE WHEN i.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted_count,
            SUM(CASE WHEN i.status = 'DECLINED' THEN 1 ELSE 0 END) AS declined_count,
            SUM(CASE WHEN i.status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired_count,
            SUM(CASE WHEN i.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
            COUNT(*) AS count
        FROM invites i
        WHERE i.world_id = ?
            """.trimIndent()),
    parameterSetter = { stmt, input ->
        stmt.setInt(1, input.worldId)
    },
    errorMapper = { HandleGetWorldFailure.SystemError(it.javaClass.simpleName) },
    resultMapper = { rs ->
        val counts = mutableMapOf<GetWorldInvitationsInput.StatusFilter, Int>()
        if (rs.next()) {
            counts[GetWorldInvitationsInput.StatusFilter.PENDING] = rs.getInt("pending_count")
            counts[GetWorldInvitationsInput.StatusFilter.ACCEPTED] = rs.getInt("accepted_count")
            counts[GetWorldInvitationsInput.StatusFilter.DECLINED] = rs.getInt("declined_count")
            counts[GetWorldInvitationsInput.StatusFilter.EXPIRED] = rs.getInt("expired_count")
            counts[GetWorldInvitationsInput.StatusFilter.CANCELLED] = rs.getInt("cancelled_count")
            counts[GetWorldInvitationsInput.StatusFilter.ALL] = rs.getInt("count")
        }
        counts
    }
)

val worldInvitationsStep = DatabaseSteps.query<GetWorldInvitationsInput, HandleGetWorldFailure, List<Invite>>(
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
                WHERE i.world_id = ? AND (? = 'ALL' OR i.status = ?)
                ORDER BY i.created_at DESC
            """.trimIndent()),
    parameterSetter = { stmt, input ->
        stmt.setInt(1, input.worldId)
        stmt.setString(2, input.statusFilter.name)
        stmt.setString(3, input.statusFilter.name)
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
)

/**
 * Database step to retrieve all invitations for a specific world
 * Used in world settings to display pending and historical invitations
 */
object GetWorldInvitationsStep : Step<GetWorldInvitationsInput, HandleGetWorldFailure, GetWorldInvitationsOutput> {
    override suspend fun process(input: GetWorldInvitationsInput): Result<HandleGetWorldFailure, GetWorldInvitationsOutput> {
        return DatabaseSteps.transaction(
            object : Step<GetWorldInvitationsInput, HandleGetWorldFailure, GetWorldInvitationsOutput> {
                override suspend fun process(input: GetWorldInvitationsInput): Result<HandleGetWorldFailure, GetWorldInvitationsOutput> {
                    val invitationsResult = worldInvitationsStep.process(input)
                    if (invitationsResult is Result.Failure) {
                        return Result.Failure(invitationsResult.error)
                    }
                    val countResult = worldInvitationsCountStep.process(input)
                    if (countResult is Result.Failure) {
                        return Result.Failure(countResult.error)
                    }
                    else if (invitationsResult is Result.Success && countResult is Result.Success) {
                        return Result.Success(GetWorldInvitationsOutput(
                            filteredInvitations = invitationsResult.value,
                            invitationsCount = countResult.value
                        ))
                    }
                    return Result.failure(HandleGetWorldFailure.SystemError("Unknown error") )
                }
            },
            errorMapper = { HandleGetWorldFailure.SystemError("Unable to load world invitations") }
        ).process(input)
    }
}
