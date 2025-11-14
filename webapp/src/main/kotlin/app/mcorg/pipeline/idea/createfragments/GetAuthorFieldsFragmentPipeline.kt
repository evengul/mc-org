package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.idea.validators.ValidateIdeaAuthorStep
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.idea.createwizard.singleAuthorFields
import app.mcorg.presentation.templated.idea.createwizard.teamAuthorFields
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetAuthorFields() {
    val user = this.getUser()
    val author = ValidateIdeaAuthorStep.process(request.queryParameters).recover {
        when (parameters["authorType"]) {
            "single" -> Result.success(Author.SingleAuthor(user.minecraftUsername))
            "team" -> Result.success(Author.Team(emptyList()))
            else -> Result.failure(it)
        }
    }.getOrNull()

    respondHtml(createHTML().div {
        when (author) {
            is Author.SingleAuthor -> {
                if (author.name.isBlank()) {
                    singleAuthorFields(Author.SingleAuthor(user.minecraftUsername))
                } else {
                    singleAuthorFields(author)
                }
            }
            is Author.Team -> {
                teamAuthorFields(author)
            }
            else -> singleAuthorFields(Author.SingleAuthor(user.minecraftUsername))
        }
    } + createHTML().p {
        id = "validation-error-authorType"
        hxOutOfBands("true")
    })
}