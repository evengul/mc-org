package app.mcorg.pipeline.items

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.presentation.templated.dsl.itemGlyph
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.span
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
                    // One of the two glyph surfaces chosen for MCO-499. This loop is every
                    // consumer of /items/search at once — the idea wizard's combos, the
                    // production and resource-detail panels, the project list, both roadmap
                    // views and the project page — so a glyph here is a glyph on all of them,
                    // and every one of those pages has to carry components/item-glyph.css.
                    //
                    // The glyph is `pointer-events: none` (item-search.css) so a click always
                    // lands on this div. `selectSearchedItem(this)` would survive a child
                    // target anyway, but the two capture-phase handlers in resource-panel.js
                    // resolve from `e.target`, and this keeps them reading the option itself.
                    itemGlyph(item.id)
                    span("item-search-option__name") { +item.name }
                }
            }
        }
    })
}
