package app.mcorg.domain.model.v2.admin

import app.mcorg.domain.model.v2.user.Role
import java.time.ZonedDateTime

data class ManagedUser(
    val id: Int,
    val displayName: String,
    val email: String,
    val globalRole: Role,
    val joinedAt: ZonedDateTime,
)