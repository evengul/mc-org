package app.mcorg.pipeline.idea

import app.mcorg.presentation.templated.idea.emptyCategoryFilters
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleClearCategoryFilters() {
    respondHtml(createHTML().div {
        emptyCategoryFilters()
    })
}