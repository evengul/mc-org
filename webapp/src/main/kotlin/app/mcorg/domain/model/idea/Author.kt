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
        val index: Int,
        val title: String,
        val responsibleFor: List<String>
    ) : Author
}
