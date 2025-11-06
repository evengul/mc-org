package app.mcorg.domain.model.user

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import java.time.ZonedDateTime

sealed interface User {
    val id: Int
}

data class WorldMember(
    override val id: Int,
    val worldId: Int,
    val displayName: String,
    val worldRole: Role,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
) : User

data class Profile(
    override val id: Int,
    val email: String
) : User {
    val discordConnection: Boolean
        get() = false
    val microsoftConnection: Boolean
        get() = true
    val avatarUrl: String?
        get() = null
}

data class TokenProfile(
    override val id: Int,
    val uuid: String,
    val minecraftUsername: String,
    val displayName: String,
    val roles: List<String>,
) : User {
    val isSuperAdmin: Boolean
        get() = roles.contains("superadmin")
    val isModerator: Boolean
        get() = isSuperAdmin || roles.contains("moderator")
    val isIdeaCreator: Boolean
        get() = isSuperAdmin || roles.contains("idea_creator")
    val isBanned: Boolean
        get() = roles.contains("banned")
    val isDemoUserInProduction: Boolean
        get() = roles.contains("demo_user") && AppConfig.env == Production
}

data class MinecraftProfile(
    val uuid: String,
    val username: String,
    val isDemoUser: Boolean = false
)
