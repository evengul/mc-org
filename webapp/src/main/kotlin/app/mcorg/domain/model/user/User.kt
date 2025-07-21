package app.mcorg.domain.model.v2.user

sealed interface User {
    val id: Int
}

data class WorldMember(
    override val id: Int,
    val displayName: String,
    val worldRole: Role,
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
    val displayName: String
) : User

data class MinecraftProfile(
    val uuid: String,
    val username: String
)
