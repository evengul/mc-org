package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.idea.createwizard.singleAuthorFields
import app.mcorg.presentation.templated.idea.createwizard.teamAuthorFields
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftAuthorFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val author = data.author
    val authorType = when (author) {
        is Author.Team -> "team"
        else -> "single"
    }

    div {
        label {
            htmlFor = "draft-author-type"
            +"Author Type"
            span("required-indicator") { +"*" }
        }
        select(classes = "form-control") {
            id = "draft-author-type"
            name = "authorType"
            required = true
            hxGet("/ideas/create/author-fields")
            hxTrigger("change")
            hxTarget("#author-fields-container")
            hxSwap("outerHTML")
            hxInclude("this")
            option {
                value = "single"
                selected = authorType == "single"
                +"Single Author"
            }
            option {
                value = "team"
                selected = authorType == "team"
                +"Team"
            }
        }
        p("form-error") { id = "error-authorType" }
    }

    div {
        id = "author-fields-container"
        when (author) {
            is Author.Team -> teamAuthorFields(author)
            is Author.SingleAuthor -> singleAuthorFields(author)
            else -> singleAuthorFields()
        }
    }
}
