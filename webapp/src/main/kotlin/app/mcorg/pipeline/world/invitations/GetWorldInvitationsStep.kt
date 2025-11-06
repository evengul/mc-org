package app.mcorg.pipeline.world.invitations

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.invitation.extractors.toInvite

enum class InvitationStatusFilter {
    ALL,
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED;

    companion object {
        fun fromApiName(name: String?, default: InvitationStatusFilter? = PENDING): InvitationStatusFilter? = when(name?.lowercase()?.trim()) {
            "all" -> ALL
            "pending" -> PENDING
            "accepted" -> ACCEPTED
            "declined" -> DECLINED
            "cancelled" -> CANCELLED
            "expired" -> EXPIRED
            else -> default
        }
    }
}

data class GetWorldInvitationsStep(val worldId: Int) : Step<InvitationStatusFilter, AppFailure.DatabaseError, List<Invite>> {
    override suspend fun process(input: InvitationStatusFilter): Result<AppFailure.DatabaseError, List<Invite>> {
        return DatabaseSteps.query<InvitationStatusFilter, List<Invite>>(
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
            parameterSetter = { statement, filter ->
                statement.setInt(1, worldId)
                statement.setString(2, filter.name)
                statement.setString(3, filter.name)
            },
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