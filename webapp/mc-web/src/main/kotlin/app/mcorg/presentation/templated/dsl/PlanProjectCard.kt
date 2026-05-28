package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectPlanListItem
import app.mcorg.domain.model.project.ProjectStage
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.summary
import kotlinx.html.ul
import kotlinx.html.li

private fun Dimension.toDisplayName(): String = when (this) {
    Dimension.OVERWORLD -> "Overworld"
    Dimension.NETHER -> "Nether"
    Dimension.END -> "End"
}

private fun MinecraftLocation.toDisplayString(): String =
    "${dimension.toDisplayName()} ($x, $y, $z)"

fun FlowContent.planProjectCard(worldId: Int, project: ProjectPlanListItem) {
    val isDone = project.stage == ProjectStage.COMPLETED
    val cardClasses = buildSet {
        add("project-card")
        if (isDone) add("project-card--done")
    }

    div(cardClasses.joinToString(" ")) {
        attributes["id"] = "project-card-${project.id}"

        div("project-card__header") {
            a(classes = "project-card__name") {
                href = "/worlds/$worldId/projects/${project.id}"
                +project.name
            }
            statusBadge(project.stage.toBadgeStatus())
        }

        div("project-card__meta") {
            val resourceText = if (project.resourceDefinitionCount == 0) {
                "No resources defined"
            } else {
                "${project.resourceDefinitionCount} resource${if (project.resourceDefinitionCount == 1) "" else "s"} defined"
            }
            p("project-card__resources") { +resourceText }

            p("project-card__location") {
                +project.location.toDisplayString()
            }
        }

        if (project.blockedByCount > 0 || project.blocksCount > 0) {
            div("project-card__dependencies") {
                if (project.blockedByCount > 0) {
                    dependencyDetails(
                        worldId = worldId,
                        label = "Blocked by",
                        projects = project.blockedByProjects
                    )
                }
                if (project.blocksCount > 0) {
                    dependencyDetails(
                        worldId = worldId,
                        label = "Blocks",
                        projects = project.blocksProjects
                    )
                }
            }
        }
    }
}

private fun FlowContent.dependencyDetails(worldId: Int, label: String, projects: List<NamedProjectId>) {
    val count = projects.size
    val noun = if (count == 1) "project" else "projects"
    details("project-card__dep-details") {
        summary("project-card__dep-summary") {
            +"$label: $count $noun"
        }
        ul("project-card__dep-list") {
            projects.forEach { ref ->
                li {
                    a(classes = "project-card__dep-link") {
                        href = "/worlds/$worldId/projects/${ref.id}"
                        +ref.name
                    }
                }
            }
        }
    }
}

fun FlowContent.planProjectCardList(worldId: Int, projects: List<ProjectPlanListItem>) {
    div("project-card-list") {
        attributes["id"] = "project-card-list"
        projects.forEach { planProjectCard(worldId, it) }
    }
}
