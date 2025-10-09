package app.mcorg.domain.model.admin

import app.mcorg.domain.model.user.Role
import java.time.ZonedDateTime

data class ManagedUser(
    val id: Int,
    val displayName: String,
    val minecraftUsername: String,
    val email: String,
    val globalRole: Role,
    val joinedAt: ZonedDateTime,
    val lastSeen: ZonedDateTime? = null,
)