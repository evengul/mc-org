package app.mcorg.domain.model.v2.invite

import app.mcorg.domain.model.v2.user.Role
import java.time.ZonedDateTime

data class Invite(
    val worldId: Int,
    val from: Int,
    val to: Int,
    val role: Role,
    val createdAt: ZonedDateTime,
    val status: InviteStatus
) {
    companion object {
        fun create(worldId: Int, from: Int, to: Int, role: Role = Role.MEMBER): Invite {
            val now = ZonedDateTime.now()
            return Invite(
                worldId = worldId,
                from = from,
                to = to,
                role = role,
                createdAt = now,
                status = InviteStatus.Pending(createdAt = now)
            )
        }
    }
}
