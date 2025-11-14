package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaAuthorStep : Step<Parameters, ValidationFailure, Author> {
    override suspend fun process(input: Parameters): Result<ValidationFailure, Author> {
        val authorType = input["authorType"]?.trim() ?: "single"

        return when (authorType) {
            "single" -> {
                val name = input["authorName"]?.trim()
                if (name.isNullOrEmpty()) {
                    Result.Failure(ValidationFailure.MissingParameter("authorName"))
                } else {
                    Result.Success(Author.SingleAuthor(name))
                }
            }
            "team" -> {
                val members = mutableListOf<Author.TeamAuthor>()
                var index = 0

                while (input["teamMembers[$index][name]"] != null) {
                    val name = input["teamMembers[$index][name]"]?.trim()
                    val title = input["teamMembers[$index][role]"]?.trim() ?: ""
                    val responsibleFor = input["teamMembers[$index][contributions]"]?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()

                    if (!name.isNullOrBlank()) {
                        members.add(Author.TeamAuthor(name, index, title, responsibleFor))
                    }
                    index++
                }

                if (members.isEmpty()) {
                    Result.failure(ValidationFailure.MissingParameter("teamMembers"))
                } else {
                    Result.success(Author.Team(members))
                }
            }
            else -> Result.Failure(ValidationFailure.InvalidValue("authorType", listOf("single", "team")))
        }
    }
}