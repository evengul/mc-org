package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.presentation.templated.idea.emptyCategoryFilters
import app.mcorg.presentation.templated.idea.renderFilterField
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetCategoryFilters() {
    val categoryParam = parameters["category"]?.uppercase() ?: run {
        respondHtml(createHTML().div {
            emptyCategoryFilters()
        })
        return
    }

    try {
        val category = IdeaCategory.valueOf(categoryParam)
        val schema = IdeaCategorySchemas.getSchema(category)

        respondHtml(createHTML().div {
            // Render all filterable fields from the schema
            schema.getFilterableFields().forEach { field ->
                renderFilterField(field)
            }

            // If no filterable fields exist
            if (schema.getFilterableFields().isEmpty()) {
                emptyCategoryFilters()
            }
        })
    } catch (_: IllegalArgumentException) {
        // Invalid category name
        respondHtml(createHTML().div {
            emptyCategoryFilters()
        })
    }
}