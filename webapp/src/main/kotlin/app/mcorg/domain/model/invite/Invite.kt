package app.mcorg.domain.model.invite

import app.mcorg.domain.model.user.Role
import java.time.ZonedDateTime

data class Invite(
    val id: Int,
    val worldId: Int,
    val worldName: String,
    val from: Int,
    val fromUsername: String,
    val to: Int,
    val role: Role,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime,
    val status: InviteStatus
)
