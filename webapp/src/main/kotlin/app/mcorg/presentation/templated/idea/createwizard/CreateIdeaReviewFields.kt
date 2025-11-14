package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FORM
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.strong

fun FORM.reviewIdeaFields(data: CreateIdeaWizardData) {
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

    data.categoryData?.let { (category, map) ->
        reviewField("Category", category.name)
        val schema = IdeaCategorySchemas.getSchema(category)
        map.forEach { (key, value) ->
            schema.getField(key)?.let { field ->
                reviewField(field.label, field.displayValue(value))
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