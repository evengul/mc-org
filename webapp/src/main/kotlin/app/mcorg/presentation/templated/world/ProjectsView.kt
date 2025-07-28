package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import java.time.format.DateTimeFormatter
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
                chipComponent {
                    color = ChipColor.NEUTRAL
                    val completeStatus = if (project.tasksCompleted == 0) 0.0 else (project.tasksCompleted.toDouble() / project.tasksTotal.toDouble()) * 100
                    + "${completeStatus.toInt()}% Complete"
                }
            }
            chipComponent {
                // TODO: ChipColor based on project stage
                color = ChipColor.INFO
                // TODO(ICON): Proper stage icon
                icon = Icons.Menu.UTILITIES
                // TODO: Proper stage name
                + project.stage.toPrettyEnumName()
            }
            p("subtle") {
                + project.description
            }
            div("project-item-meta") {
                p("subtle") {
                    + "${project.tasksCompleted} of ${project.tasksTotal} tasks completed"
                }
                p("subtle") {
                    + "Updated ${project.updatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
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