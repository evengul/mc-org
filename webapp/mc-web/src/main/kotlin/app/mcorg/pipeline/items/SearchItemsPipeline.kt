package app.mcorg.pipeline.items

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleSearchItems() {
    val q = parameters["q"]?.trim()?.lowercase() ?: ""
    if (q.isBlank()) {
        respondHtml("")
        return
    }

    val versionRange = ValidateIdeaMinecraftVersionStep.process(parameters).getOrNull()
        ?: MinecraftVersionRange.Unbounded

    val items = GetItemsInVersionRangeStep.process(versionRange)
        .getOrNull()
        .orEmpty()
        .filter { it.name.lowercase().contains(q) }
        .take(20)

    respondHtml(createHTML().div {
        if (items.isEmpty()) {
            div("item-search-empty") { +"No items found" }
        } else {
            items.forEach { item ->
                div("item-search-option") {
                    attributes["data-item-id"] = item.id
                    attributes["data-item-name"] = item.name
                    attributes["onclick"] = "selectSearchedItem(this)"
                    +item.name
                }
            }
        }
    })
}
