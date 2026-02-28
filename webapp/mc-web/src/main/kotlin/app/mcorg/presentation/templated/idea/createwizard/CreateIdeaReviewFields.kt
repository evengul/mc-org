package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FORM
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.strong

fun FORM.reviewIdeaFields(data: CreateIdeaWizardSession) {
    p {
        + "Please review your idea details before submission."
    }
    hr {  }
    data.name?.let {
        reviewField("Idea Name", it)
    }
    data.description?.let {
        reviewField("Description", it)
    }
    data.difficulty?.let {
        reviewField("Difficulty", it.toPrettyEnumName())
    }

    data.author?.let {
        when (it) {
            is Author.SingleAuthor -> {
                reviewField("Author Name", it.name)
            }
            is Author.Team -> {
                val memberNames = it.members.joinToString(", ") { member -> member.name }
                reviewField("Team Members", memberNames)
            }
            else -> {}
        }
    }

    data.versionRange?.let {
        reviewField("Version Range", it.toString())
    }

    data.itemRequirements?.let {
        if (it.isNotEmpty()) {
            if (it.entries.size > 10) {
                reviewField("Item Requirements", "${it.entries.size} items selected. A total of ${it.values.sum()} items.")
                return@let
            }
            val requirementsText = it.entries.joinToString(", ") { entry -> "${entry.key} (x${entry.value})" }
            reviewField("Item Requirements", requirementsText)
        } else {
            reviewField("Item Requirements", "None")
        }
    }

    data.category?.let {
        reviewField("Category", it.name)
        val schema = IdeaCategorySchemas.getSchema(it)
        data.categoryData?.let { map ->
            map.forEach { (key, value) ->
                schema.getField(key)?.let { field ->
                    reviewField(field.label, value.display())
                }
            }
        }
    }

}

private fun FORM.reviewField(labelText: String, valueText: String) {
    p {
        strong { + "$labelText: " }
        + valueText
    }
}