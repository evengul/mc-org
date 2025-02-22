package app.mcorg.domain.users

data class Profile(val id: Int,
                   val username: String,
                   val email: String,
                   val profilePhoto: String?,
                   val selectedWorld: Int?,
                   val technicalPlayer: Boolean) {
    fun toUser() = User(id, username)
}