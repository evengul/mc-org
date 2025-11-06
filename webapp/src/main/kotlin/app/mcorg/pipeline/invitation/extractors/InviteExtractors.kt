package app.mcorg.pipeline.invitation.extractors

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import java.sql.ResultSet
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun ResultSet.toInvite() = Invite(
    id = getInt("id"),
    worldId = getInt("world_id"),
    worldName = getString("world_name"),
    from = getInt("from_user_id"),
    fromUsername = getString("from_username"),
    to = getInt("to_user_id"),
    toUsername = getString("to_username"),
    role = Role.fromString(getString("role")),
    createdAt = getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
    expiresAt = getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC).plusDays(7), // Assuming 7-day expiration
    status = mapInviteStatus(getString("status"), getTimestamp("status_reached_at").toInstant().atZone(ZoneOffset.UTC))
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