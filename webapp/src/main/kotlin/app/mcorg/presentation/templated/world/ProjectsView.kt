package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.infoChip
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.LI
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlin.collections.plus

fun UL.projectList(projects: List<Project>) {
    id = "world-projects-list"
    projects.sortedBy { it.name }.forEach { project ->
        li {
            projectItem(project)
        }
    }
}

fun LI.projectItem(project: Project) {
    classes += "project-item"
    div("project-item-header") {
        h2 {
            + project.name
        }
        infoChip(
            icon = Icons.Menu.UTILITIES,
            text = project.stage.toPrettyEnumName()
        )
    }
    if (project.description.isNotBlank()) {
        p("subtle") {
            + project.description
        }
    }
    div("project-item-meta") {
        p("subtle") {
            + "${project.type.toPrettyEnumName()} Project"
        }
        p("subtle") {
            + "Updated ${project.updatedAt.formatAsRelativeOrDate()}"
        }
    }
    progressComponent {
        value = project.tasksCompleted.toDouble()
        max = project.tasksTotal.toDouble()
        showPercentage = false
        label = "${project.tasksCompleted} of ${project.tasksTotal} task${if(project.tasksTotal == 1) "" else "s"} completed"
    }
    actionButton("View project") {
        href = Link.Worlds.world(project.worldId).project(project.id).to
    }
}