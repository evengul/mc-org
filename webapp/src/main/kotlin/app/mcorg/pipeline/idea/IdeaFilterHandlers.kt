package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.templated.idea.emptyCategoryFilters
import app.mcorg.presentation.templated.idea.ideaList
import app.mcorg.presentation.templated.idea.renderFilterField
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

sealed interface SearchIdeasFailure {
    object DatabaseError : SearchIdeasFailure
}

/**
 * HTMX endpoint: Returns filtered idea list based on query parameters.
 */
suspend fun ApplicationCall.handleSearchIdeas() {
    // Parse filter parameters
    val query = request.queryParameters["query"]
    val category = request.queryParameters["category"]?.takeIf { it.isNotEmpty() }
        ?.let { IdeaCategory.valueOf(it) }

    // Query database for ideas
    when (val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(category))) {
        is Result.Success -> {
            // Return HTML fragment for idea list
            respondHtml(createHTML().ul {
                ideaList(result.value)
            })
        }
        is Result.Failure -> {
            // Return empty list on error
            respondHtml(createHTML().ul {
                ideaList(emptyList())
            })
        }
    }
}

/**
 * HTMX endpoint: Returns category-specific filter fields based on the schema.
 * This loads dynamically when a user selects a category.
 */
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

/**
 * HTMX endpoint: Clears category-specific filters (when "All Categories" is selected).
 */
suspend fun ApplicationCall.handleClearCategoryFilters() {
    respondHtml(createHTML().div {
        emptyCategoryFilters()
    })
}
