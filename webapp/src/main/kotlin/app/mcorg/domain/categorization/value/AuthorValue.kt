package app.mcorg.domain.categorization.value

data class AuthorValue(var authors: MutableList<String> = mutableListOf(), var credits: MutableList<String> = mutableListOf()) : CategoryValue {
    override val id: String
        get() = "common.author"
    override val name: String
        get() = "Authors"
}

fun AuthorValue.author(author: String) {
    this.authors.add(author)
}

fun AuthorValue.credit(credit: String) {
    this.credits.add(credit)
}
