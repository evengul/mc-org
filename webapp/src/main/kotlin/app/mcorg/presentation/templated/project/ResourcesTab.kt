package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectResourceGathering
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import kotlinx.html.DIV
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
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
        if (project.stage != ProjectStage.COMPLETED) {
            form {
                id = "project-resources-production-form"
                encType = FormEncType.applicationXWwwFormUrlEncoded
                hxPost(Link.Worlds.world(project.worldId).project(project.id).to + "/resources")
                hxTarget("#project-resources-production-list")
                hxSwap("afterbegin")
                attributes["hx-on::after-request"] = "this.reset(); document.getElementById('project-resources-name-input')?.focus();"

                input {
                    id = "project-resources-name-input"
                    name = "name"
                    placeholder = "Item name (e.g., Oak Logs, Stone, Diamond)"
                    type = InputType.text
                    required = true
                    maxLength = "100"
                }

                input {
                    id = "project-resources-rate-input"
                    name = "ratePerHour"
                    placeholder = "Production rate per hour (If applicable)"
                    type = InputType.number
                    required = false
                    min = "0"
                    max = "2000000000"
                }

                neutralButton("Add resource production") {
                    iconLeft = Icons.Menu.CONTRAPTIONS
                    iconSize = IconSize.SMALL
                }
            }
        }
        ul {
            id = "project-resources-production-list"
            production.sortedBy { it.name }.forEach { prod ->
                li {
                    projectResourceProductionItem(project.worldId, prod)
                }
            }
        }
    }
}

fun LI.projectResourceProductionItem(worldId: Int, production: ProjectProduction) {
    id = "project-resource-production-${production.id}"
    span {
        classes = setOf("production-item-start")
        p {
            + production.name
        }
        p("subtle") {
            if (production.ratePerHour > 0) {
                + "${production.ratePerHour.toRate()} per hour"
            } else {
                + "No hourly production rate set."
            }
        }
    }
    span {
        classes = setOf("production-item-end")
        iconButton(Icons.DELETE, "Delete project production value", color = IconButtonColor.DANGER, iconSize = IconSize.SMALL) {
            buttonBlock = {
                hxDelete(Link.Worlds.world(worldId).project(production.projectId).to + "/resources/${production.id}")
                hxTarget("#project-resource-production-${production.id}")
                hxSwap("delete")
                hxConfirm("Are you sure you want to delete this production item?")
            }
        }
    }
}

private fun Int.toRate(): String {
    if (this < 1000) {
        return this.toString()
    }
    if (this < 1_000_000) {
        val value = this / 1000.0
        return if (value % 1.0 == 0.0) {
            "${value.toInt()}k"
        } else {
            "${String.format(Locale.US, "%.1f", value)}k"
        }
    }
    val value = this / 1_000_000.0
    return if (value % 1.0 == 0.0) {
        "${value.toInt()}M"
    } else {
        "${String.format(Locale.US, "%.2f", value)}M"
    }
}