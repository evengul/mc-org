package app.mcorg.domain.model.idea

import kotlinx.serialization.Serializable

@Serializable
sealed interface Author {
    val name: String

    @Serializable
    data class SingleAuthor(override val name: String) : Author

    @Serializable
    data class Team(val members: List<TeamAuthor>) : Author {
        override val name: String
            get() = members.joinToString(", ") { it.name }
    }

    @Serializable
    data class TeamAuthor(
        override val name: String,
        val order: Int,
        val role: String,
        val contributions: List<String>
    ) : Author
}
