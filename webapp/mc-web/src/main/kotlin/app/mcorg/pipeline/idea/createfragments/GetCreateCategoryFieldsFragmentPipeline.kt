package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.idea.createwizard.renderCreateField
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetCreateCategoryFields() {
    // The category is a path parameter (/ideas/create/fields/{category}), so read it off the
    // merged parameters — request.queryParameters never sees it.
    val categoryParam = parameters["category"]?.uppercase() ?: run {
        respondHtml(createHTML().div {
            p("subtle wizard-field-placeholder") {
                +"Select a category to see specific fields"
            }
        })
        return
    }

    try {
        val category = IdeaCategory.valueOf(categoryParam)
        val schema = IdeaCategorySchemas.getSchema(category)
        val versionRange = ValidateIdeaMinecraftVersionStep.process(parameters).getOrNull() ?: MinecraftVersionRange.Unbounded

        respondHtml(createHTML().div {
            classes += "stack stack--sm"

            schema.fields.forEach { field ->
                renderCreateField(versionRange, field)
            }

            if (schema.fields.isEmpty()) {
                p("subtle wizard-field-placeholder") {
                    +"No additional fields for this category"
                }
            }
        } + createHTML().p("form-error") {
            // Clears any "Category is required" error now that one is picked. The id must match
            // the paragraph draftCategoryFields renders, or HTMX drops the swap with oobErrorNoTarget.
            hxOutOfBands("true")
            id = "error-category"
        })
    } catch (_: IllegalArgumentException) {
        // Invalid category name
        respondHtml(createHTML().div {
            p("subtle wizard-field-placeholder") {
                +"Invalid category"
            }
        })
    }
}
