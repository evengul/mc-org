package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.presentation.templated.idea.renderCreateField
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.style

suspend fun ApplicationCall.handleGetCreateCategoryFields() {
    val categoryParam = request.queryParameters["category"]?.uppercase() ?: run {
        respondHtml(createHTML().div {
            p("subtle") {
                style = "text-align: center; padding: var(--spacing-sm);"
                +"Select a category to see specific fields"
            }
        })
        return
    }

    try {
        val category = IdeaCategory.valueOf(categoryParam)
        val schema = IdeaCategorySchemas.getSchema(category)

        respondHtml(createHTML().div {
            classes += "stack stack--sm"

            // Render all fields from the schema (not just filterable ones)
            schema.fields.forEach { field ->
                renderCreateField(field)
            }

            // If no fields exist
            if (schema.fields.isEmpty()) {
                p("subtle") {
                    style = "text-align: center; padding: var(--spacing-sm);"
                    +"No additional fields for this category"
                }
            }
        })
    } catch (_: IllegalArgumentException) {
        // Invalid category name
        respondHtml(createHTML().div {
            p("subtle") {
                style = "text-align: center; padding: var(--spacing-sm);"
                +"Invalid category"
            }
        })
    }
}