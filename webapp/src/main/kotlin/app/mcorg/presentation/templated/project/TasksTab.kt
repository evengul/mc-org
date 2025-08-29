package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*
import java.time.format.DateTimeFormatter

fun DIV.tasksTab(project: Project, tasks: List<Task>) {
    classes += "project-tasks-tab"
    div("project-tasks-content") {
        projectProgressSection(project)
        taskManagementSection(project, tasks)
    }
    projectDetailsSidebar(project)
}

private fun DIV.projectProgressSection(project: Project) {
    div("project-tasks-progress") {
        h2 {
            + "Project Progress"
        }
        p("subtle") {
            + "${project.tasksCompleted} of ${project.tasksTotal} tasks completed"
        }
        progressComponent {
            value = project.tasksCompleted.toDouble()
            max = project.tasksTotal.toDouble()
        }
        p("subtle") {
            + "Current stage progress"
        }
        progressComponent {
            value = project.stageProgress
            max = 100.0
        }
    }
}

private fun DIV.taskManagementSection(project: Project, tasks: List<Task>) {
    div("project-tasks") {
        taskManagementHeader()
        if (tasks.isEmpty()) {
            emptyTasksDisplay(project)
        }
        tasksList(project.worldId, project.id, tasks)
    }
}

private fun DIV.taskManagementHeader() {
    h2 {
        + "Tasks"
    }
    taskSearchAndFilters()
}

private fun DIV.taskSearchAndFilters() {
    div("project-tasks-search-filter") {
        input {
            placeholder = "Search tasks..."
        }
        select {
            name = "completionStatus"
            option {
                value = "all"
                + "All Tasks"
            }
            option {
                value = "in-progress"
                + "Active Tasks"
            }
            option {
                value = "completed"
                + "Completed Tasks"
            }
        }
        select {
            Priority.entries.forEach {
                option {
                    value = it.name
                    + it.toPrettyEnumName()
                }
            }
        }
    }
}

private fun DIV.emptyTasksDisplay(project: Project) {
    div("project-tasks-empty") {
        h2 {
            + "No Tasks Yet"
        }
        p("subtle") {
            + "Create your first task to get started."
        }
        createTaskModal(project)
    }
}

fun DIV.tasksList(worldId: Int, projectId: Int, tasks: List<Task>) {
    ul {
        id = "tasks-list"
        tasks.sortedBy { it.isCompleted() }.forEach { task ->
            taskItem(worldId, projectId, task)
        }
    }
}

private fun UL.taskItem(worldId: Int, projectId: Int, task: Task) {
    li {
        taskHeader(worldId, projectId, task)
        if (task.description.isNotBlank()) {
            p("subtle") {
                + task.description
            }
        }
        taskProgressDisplay(task)
        taskRequirements(task)
    }
}

private fun LI.taskHeader(worldId: Int, projectId: Int, task: Task) {
    div("task-header") {
        div("task-header-start") {
            input {
                id = "task-${task.id}-complete"
                checked = task.isCompleted()
                disabled = task.isCompleted()
                hxTarget("#task-${task.id}-complete")
                hxPatch(Link.Worlds.world(worldId).project(projectId).tasks().task(task.id) + "/complete")
                hxSwap("outerHTML")
                type = InputType.checkBox
            }
            h3 {
                + task.name
            }
        }
        div("task-header-end") {
            chipComponent {
                icon = when(task.priority) {
                    Priority.HIGH, Priority.CRITICAL -> Icons.Priority.HIGH
                    Priority.MEDIUM -> Icons.Priority.MEDIUM
                    Priority.LOW -> Icons.Priority.LOW
                }
                variant = when(task.priority) {
                    Priority.HIGH, Priority.CRITICAL -> ChipVariant.DANGER
                    Priority.MEDIUM -> ChipVariant.WARNING
                    Priority.LOW -> ChipVariant.INFO
                }
            }
        }
    }
}

private fun LI.taskProgressDisplay(task: Task) {
    div("task-progress-description") {
        p("subtle") {
            +"Progress"
        }
        p("subtle") {
            +"${task.progress().toInt()}% completed"
        }
    }
    progressComponent {
        value = task.progress()
        max = 100.0
    }
}

private fun LI.taskRequirements(task: Task) {
    if (task.requirements.isEmpty()) {
        return
    }
    neutralButton("Show details") {
        classes += "task-requirements-toggle"
        onClick = "document.getElementById('task-requirements-${task.id}')?.classList.toggle('visible'); this.textContent = this.textContent === 'Show details' ? 'Hide details' : 'Show details';"
    }
    div("task-requirements") {
        id = "task-requirements-${task.id}"
        p {
            + "Requirements:"
        }
        ul {
            task.requirements.forEach { requirement ->
                li {
                    if (requirement.isCompleted()) {
                        classes += "completed"
                    }
                    when(requirement) {
                        is ActionRequirement -> actionRequirement(requirement)
                        is ItemRequirement -> itemRequirement(requirement)
                    }
                }
            }
        }
    }
}

private fun LI.actionRequirement(requirement: ActionRequirement) {
    classes += "action-requirement"
    input {
        id = "requirement-${requirement.id}"
        checked = requirement.isCompleted()
        type = InputType.checkBox
    }
    label {
        htmlFor = "requirement-${requirement.id}"
        + requirement.action
    }
}

private fun LI.itemRequirement(requirement: ItemRequirement) {
    classes += "item-requirement"
    div("item-requirement-counts") {
        p {
            + requirement.item
        }
        span {
            p {
                + "${requirement.collected} / ${requirement.requiredAmount}"
            }
            // TODO(ICON): Edit icon for item requirement
            iconButton(Icons.MENU_ADD, iconSize = IconSize.SMALL)
        }
    }
    progressComponent {
        value = requirement.collected.toDouble()
        max = requirement.requiredAmount.toDouble()
    }
    itemRequirementActions()
}

private fun LI.itemRequirementActions() {
    div("item-requirement-actions") {
        neutralButton("+1")
        neutralButton("+64")
        neutralButton("+1728")
        neutralButton("+3456")
        neutralButton("+ Custom")
    }
}

private fun DIV.projectDetailsSidebar(project: Project) {
    aside {
        h2 {
            + "Project Details"
        }
        p("details-label") {
            + "Project Type"
        }
        p {
            + project.type.toPrettyEnumName()
        }
        p("details-label") {
            + "Created"
        }
        p {
            + project.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
        p("details-label") {
            + "Last Updated"
        }
        p {
            + project.updatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }
}