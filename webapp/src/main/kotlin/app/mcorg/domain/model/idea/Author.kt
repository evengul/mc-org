package app.mcorg.domain.model.idea

sealed interface Author {
    val name: String

    data class SingleAuthor(override val name: String) : Author

    data class Team(val members: List<TeamAuthor>) : Author {
        override val name: String
            get() = members.joinToString(", ") { it.name }
    }

    data class TeamAuthor(
        override val name: String,
        val order: Int,
        val role: String,
        val contributions: List<String>
    ) : Author
}
