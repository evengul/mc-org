package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.infoChip
import app.mcorg.presentation.templated.common.chip.neutralChip
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlin.collections.plus

fun UL.projectList(projects: List<Project>) {
    classes += "world-projects-list"
    projects.forEach { project ->
        li {
            classes += "project-item"
            div("project-item-header") {
                h2 {
                    + project.name
                }
                val completeStatus = if (project.tasksCompleted == 0) 0.0 else (project.tasksCompleted.toDouble() / project.tasksTotal.toDouble()) * 100
                neutralChip(
                    text = "${completeStatus.toInt()}% Complete"
                )
            }
            infoChip(
                icon = Icons.Menu.UTILITIES,
                text = project.stage.toPrettyEnumName()
            )
            p("subtle") {
                + project.description
            }
            div("project-item-meta") {
                p("subtle") {
                    + "${project.tasksCompleted} of ${project.tasksTotal} tasks completed"
                }
                p("subtle") {
                    + "Updated ${project.updatedAt.formatAsRelativeOrDate()}"
                }
            }
            progressComponent {
                value = project.tasksCompleted.toDouble()
                max = project.tasksTotal.toDouble()
            }
            div {
                p("subtle") {
                    + "Current stage progress"
                }
                progressComponent {
                    value = project.stageProgress
                    max = 100.0
                }
            }
            actionButton("View project") {
                href = Link.Worlds.world(project.worldId).project(project.id).to
            }
        }
    }
}