package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.world.World
import kotlinx.html.*

fun FlowContent.worldCard(world: World) {
    a(classes = "world-card") {
        href = "/worlds/${world.id}/projects"

        div("world-card__header") {
            span("world-card__name") { +world.name }
            span("world-card__version") { +"MC ${world.version}" }
        }

        if (world.description.isNotBlank()) {
            p("world-card__description") { +world.description }
        }

        div("world-card__tally") {
            worldTally(world.projectTally, compact = true)
        }
    }
}

fun FlowContent.worldCardList(worlds: List<World>) {
    div("world-card-list") {
        id = "world-card-list"
        attributes["hx-get"] = "/worlds/search"
        attributes["hx-trigger"] = "worldListChanged from:body"
        attributes["hx-swap"] = "outerHTML"
        worlds.forEach { worldCard(it) }
    }
}
