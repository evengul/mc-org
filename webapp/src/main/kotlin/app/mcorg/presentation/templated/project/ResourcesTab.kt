package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectResourceGathering
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.progress.progressComponent
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import java.util.Locale

fun DIV.resourcesTab(project: Project, production: List<ProjectProduction>, gathering: ProjectResourceGathering) {
    classes += "project-resources-tab"
    if (project.stage != ProjectStage.COMPLETED && gathering.totalNeeded > 0) {
        div("project-resources-collection") {
            h2 {
                + "Item Collection Progress"
            }
            div("project-resources-collection-summary") {
                p {
                    + "Total Progress"
                }
                p {
                    + "${gathering.totalCollected} / ${gathering.totalNeeded} (${((gathering.totalCollected.toDouble() / gathering.totalNeeded.toDouble()) * 100.0).toInt()}%)"
                }
            }
            progressComponent {
                value = gathering.totalCollected.toDouble()
                max = gathering.totalNeeded.toDouble()
            }
            gathering.toCollect.forEach {
                div("project-resources-collection-summary") {
                    p {
                        + it.name
                    }
                    p {
                        + "${it.collected} / ${it.needed} (${((it.collected.toDouble() / it.needed.toDouble()) * 100.0).toInt()}%)"
                    }
                }
                progressComponent {
                    value = it.collected.toDouble()
                    max = it.needed.toDouble()
                }
            }
        }
    }
    div("project-resources-production") {
        div {
            h2 {
                + "Resource Production"
            }
            p("subtle") {
                + "The resources that this project will produce when complete."
            }
        }
        ul {
            production.forEach { prod ->
                li {
                    p {
                        + prod.name
                    }
                    p("subtle") {
                        + "${prod.ratePerHour.toRate()}/h"
                    }
                }
            }
        }
        neutralButton("Edit production") {
            iconLeft = Icons.Menu.CONTRAPTIONS
            iconSize = IconSize.SMALL
        }
    }
}

private fun Int.toRate(): String {
    if (this < 1000) {
        return this.toString()
    }
    if (this < 1_000_000) {
        return "${String.format(Locale.US, "%.1f", (this / 1000.0))}k"
    }
    return "${String.format(Locale.US, "%.2f", (this / 1_000_000.0))}m"
}