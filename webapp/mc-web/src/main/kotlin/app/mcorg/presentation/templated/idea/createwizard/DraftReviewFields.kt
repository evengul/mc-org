package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftReviewFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())

    p { +"Review your idea before publishing." }
    hr {}

    reviewRow("Name", data.name ?: "(not set)")
    reviewRow("Description", data.description?.take(200)?.let { if (data.description.length > 200) "$it…" else it } ?: "(not set)")
    reviewRow("Difficulty", data.difficulty?.toPrettyEnumName() ?: "(not set)")

    data.author?.let { author ->
        when (author) {
            is Author.SingleAuthor -> reviewRow("Author", author.name)
            is Author.Team -> reviewRow("Team", author.members.joinToString(", ") { it.name })
            else -> {}
        }
    } ?: reviewRow("Author", "(not set)")

    reviewRow("Version Range", data.versionRange?.toString() ?: "(not set)")

    val itemReqs = data.itemRequirements
    if (!itemReqs.isNullOrEmpty()) {
        if (itemReqs.size > 10) {
            reviewRow("Item Requirements", "${itemReqs.size} items (${itemReqs.values.sum()} total)")
        } else {
            reviewRow("Item Requirements", itemReqs.entries.joinToString(", ") { "${it.key} ×${it.value}" })
        }
    } else {
        reviewRow("Item Requirements", "None")
    }

    reviewRow("Category", data.category?.toPrettyEnumName() ?: "(not set)")

    data.category?.let { cat ->
        val schema = IdeaCategorySchemas.getSchema(cat)
        data.categoryData?.forEach { (key, value) ->
            schema.getField(key)?.let { field ->
                reviewRow(field.label, value.display())
            }
        }
    }
}

private fun FlowContent.reviewRow(label: String, value: String) {
    div("review-row") {
        strong("review-row__label") { +"$label: " }
        span("review-row__value") { +value }
    }
}
