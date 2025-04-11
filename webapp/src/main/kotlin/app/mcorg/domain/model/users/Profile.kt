package app.mcorg.domain.model.users

data class Profile(val id: Int,
                   val username: String,
                   val email: String,
                   val profilePhoto: String?,
                   val selectedWorld: Int?,
                   val technicalPlayer: Boolean) {
    fun toUser() = User(id, username)

    companion object {
        fun default(): Profile {
            return Profile(
                id = 0,
                username = "",
                email = "",
                profilePhoto = null,
                selectedWorld = null,
                technicalPlayer = false
            )
        }
    }
}