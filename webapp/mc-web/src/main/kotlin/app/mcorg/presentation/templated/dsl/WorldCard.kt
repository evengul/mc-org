package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.world.World
import kotlinx.html.*

fun FlowContent.worldCard(world: World) {
    val progressPercent = if (world.totalProjects > 0) {
        (world.completedProjects.coerceAtMost(world.totalProjects) * 100) / world.totalProjects
    } else {
        0
    }
    val complete = world.completedProjects >= world.totalProjects && world.totalProjects > 0

    a(classes = "world-card") {
        href = "/worlds/${world.id}/projects"

        div("world-card__header") {
            span("world-card__name") { +world.name }
            span("world-card__version") { +"MC ${world.version}" }
        }

        if (world.description.isNotBlank()) {
            p("world-card__description") { +world.description }
        }

        div("world-card__progress") {
            div("world-card__progress-bar") {
                div("world-card__progress-fill${if (complete) " world-card__progress-fill--complete" else ""}") {
                    attributes["style"] = "width: ${progressPercent}%"
                    attributes["role"] = "progressbar"
                    attributes["aria-valuenow"] = world.completedProjects.toString()
                    attributes["aria-valuemin"] = "0"
                    attributes["aria-valuemax"] = world.totalProjects.toString()
                }
            }
            span("world-card__progress-label") {
                if (world.totalProjects == 0) {
                    +"No projects yet"
                } else {
                    +"${world.completedProjects} of ${world.totalProjects} project${if (world.totalProjects == 1) "" else "s"} completed"
                }
            }
        }
    }
}

fun FlowContent.worldCardList(worlds: List<World>) {
    div("world-card-list") {
        id = "world-card-list"
        worlds.forEach { worldCard(it) }
    }
}
