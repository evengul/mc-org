package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskProjectStage
import app.mcorg.domain.model.task.TaskRequirement
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.tasksTab(project: Project, totalTasksCount: Int, tasks: List<Task>) {
    classes += "project-tasks-tab"
    div("project-tasks-content") {
        projectProgressSection(project)
        taskManagementSection(project, totalTasksCount, tasks)
    }
    projectDetailsSidebar(project)
}

private fun DIV.projectProgressSection(project: Project) {
    div("project-tasks-progress") {
        h2 {
            + "Project Progress"
        }
        progressComponent {
            value = project.tasksCompleted.toDouble()
            max = project.tasksTotal.toDouble()
            showPercentage = false
            label = "${project.tasksCompleted} of ${project.tasksTotal} task${if (project.tasksTotal == 1) "" else "s"} completed"
        }
    }
}

private fun DIV.taskManagementSection(project: Project, totalTasksCount: Int, tasks: List<Task>) {
    div("project-tasks") {
        taskManagementHeader(project.worldId, project.id, project.stage)
        if (totalTasksCount == 0) {
            div {
                emptyTasksDisplay(project)
            }
        }
        p {
            id = "no-tasks-found"
            classes += "subtle"
            if (tasks.isEmpty() && totalTasksCount > 0) {
                + "No tasks found matching the search criteria."
            }
        }
        ul {
            tasksList(project.worldId, project.id, tasks)
        }
    }
}

private fun DIV.taskManagementHeader(worldId: Int, projectId: Int, projectStage: ProjectStage) {
    h2 {
        + "Tasks"
    }
    taskSearchAndFilters(worldId, projectId, projectStage)
}

private fun DIV.taskSearchAndFilters(worldId: Int, projectId: Int, projectStage: ProjectStage) {
    form(classes = "project-tasks-search-filter") {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxGet("${Link.Worlds.world(worldId).project(projectId).tasks().to}/search")
        hxTarget("#tasks-list")
        hxSwap("outerHTML")
        input {
            id = "task-search-input"
            name = "query"
            type = InputType.search
            placeholder = "Search tasks..."
        }
        select {
            name = "completionStatus"
            option {
                value = "ALL"
                + "All Tasks"
            }
            option {
                value = "IN_PROGRESS"
                selected = true
                + "Active Tasks"
            }
            option {
                value = "COMPLETED"
                + "Completed Tasks"
            }
        }
        select {
            name = "priority"
            option {
                value = "ALL"
                + "All Priorities"
            }
            Priority.entries.forEach {
                option {
                    value = it.name
                    + it.toPrettyEnumName()
                }
            }
        }
        select {
            name = "stage"
            option {
                value = "ALL"
                + "All Stages"
            }
            TaskProjectStage.entries.forEach {
                option {
                    selected = when(projectStage) {
                        ProjectStage.IDEA -> it == TaskProjectStage.IDEA
                        ProjectStage.DESIGN -> it == TaskProjectStage.DESIGN
                        ProjectStage.PLANNING -> it == TaskProjectStage.PLANNING
                        ProjectStage.RESOURCE_GATHERING -> it == TaskProjectStage.RESOURCE_GATHERING
                        ProjectStage.BUILDING -> it == TaskProjectStage.BUILDING
                        ProjectStage.TESTING -> it == TaskProjectStage.TESTING
                        else -> false
                    }
                    value = it.name
                    + it.toPrettyEnumName()
                }
            }
        }
        neutralButton("Clear filters") {
            buttonBlock = {
                type = ButtonType.reset
            }
        }
        actionButton("Search") {
            buttonBlock = {
                type = ButtonType.submit
            }
        }
    }
}

fun DIV.emptyTasksDisplay(project: Project) {
    classes += "project-tasks-empty"
    h2 {
        + "No Tasks Yet"
    }
    p("subtle") {
        + "Create your first task to get started."
    }
    createTaskModal(project)
}

fun UL.tasksList(worldId: Int, projectId: Int, tasks: List<Task>) {
    id = "tasks-list"
    tasks.sortedBy { it.isCompleted() }.forEach { task ->
        li {
            taskItem(worldId, projectId, task)
        }
    }
}

fun LI.taskItem(worldId: Int, projectId: Int, task: Task) {
    classes += "task-item"
    id = "task-${task.id}"
    taskHeader(worldId, projectId, task)
    if (task.description.isNotBlank()) {
        p("subtle") {
            + task.description
        }
    }
    span {
        taskProgressDisplay(task.id, task.progress())
    }
    taskRequirements(task, worldId, projectId)
}

private fun LI.taskHeader(worldId: Int, projectId: Int, task: Task) {
    div("task-header") {
        div("task-header-start") {
            input {
               taskCompletionCheckbox(worldId, projectId, task.id, task.isCompleted())
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
            iconButton(
                icon = Icons.DELETE,
                ariaLabel = "Delete task",
                iconSize = IconSize.SMALL,
                color = IconButtonColor.DANGER,
            ) {
                buttonBlock = {
                    hxDelete(Link.Worlds.world(worldId).project(projectId).tasks().task(task.id), "Are you sure you want to delete the task \"${task.name}\"? This action cannot be undone.")
                    hxTarget("#task-${task.id}")
                    hxSwap("delete")
                    title = "Delete task"
                }
            }
        }
    }
}

fun INPUT.taskCompletionCheckbox(worldId: Int, projectId: Int, taskId: Int, completed: Boolean) {
    id = "task-${taskId}-complete"
    classes += "task-completion-checkbox"
    checked = completed
    disabled = completed
    hxTarget("#task-${taskId}")
    hxPatch(Link.Worlds.world(worldId).project(projectId).tasks().task(taskId) + "/complete")
    hxSwap("outerHTML")
    type = InputType.checkBox
}

fun SPAN.taskProgressDisplay(taskId: Int, progress: Double) {
    id = "task-${taskId}-progress"
    div("task-progress-description") {
        p("subtle") {
            +"Progress"
        }
        p("subtle") {
            +"${progress.toInt()}% completed"
        }
    }
    progressComponent {
        value = progress
        max = 100.0
    }
}

private fun LI.taskRequirements(task: Task, worldId: Int, projectId: Int) {
    if (task.requirements.isEmpty() || (task.requirements.size == 1 && task.requirements[0].title() == task.name)) {
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
            task.requirements.sortedBy { it.isCompleted() }.forEach { requirement ->
                li {
                    requirement(requirement, worldId, projectId, task.id)
                }
            }
        }
    }
}

fun LI.requirement(requirement: TaskRequirement, worldId: Int, projectId: Int, taskId: Int) {
    id = "requirement-${requirement.id}"
    if (requirement.isCompleted()) {
        classes += "completed"
    }
    when(requirement) {
        is ActionRequirement -> actionRequirement(requirement, worldId, projectId, taskId)
        is ItemRequirement -> itemRequirement(requirement, worldId, projectId, taskId)
    }
}

private fun LI.actionRequirement(requirement: ActionRequirement, worldId: Int, projectId: Int, taskId: Int) {
    classes += "action-requirement"

    span("action-requirement-info") {
        input {
            id = "requirement-checkbox-${requirement.id}"
            if (requirement.isCompleted()) {
                checked = true
            }
            type = InputType.checkBox
            hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/${requirement.id}/toggle")
            hxTarget("#requirement-${requirement.id}")
            hxSwap("outerHTML")
        }
        label {
            htmlFor = "requirement-checkbox-${requirement.id}"
            + requirement.action
        }
    }
}

private fun LI.itemRequirement(requirement: ItemRequirement, worldId: Int, projectId: Int, taskId: Int) {
    classes += "item-requirement"

    div("item-requirement-header") {
        div("item-requirement-info") {
            p("item-name") {
                + requirement.item
            }
            span("item-counts") {
                + "${requirement.collected} / ${requirement.requiredAmount}"
            }
        }
        div("item-requirement-controls") {
            iconButton(Icons.MENU, "Edit requirement", iconSize = IconSize.SMALL) {
                buttonBlock = {
                    classes += "edit-requirement-btn"
                    onClick = "openEditRequirementModal(${requirement.id}, '${requirement.item}', ${requirement.requiredAmount}, $worldId, $projectId, $taskId)"
                    title = "Edit requirement"
                }
            }
        }
    }

    progressComponent {
        value = requirement.collected.toDouble()
        max = requirement.requiredAmount.toDouble()
    }

    if (!requirement.isCompleted()) {
        itemRequirementActions(requirement, worldId, projectId, taskId)
    }
}

private fun LI.itemRequirementActions(requirement: ItemRequirement, worldId: Int, projectId: Int, taskId: Int) {
    div("item-requirement-actions") {
        neutralButton("+1") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 1}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/${requirement.id}/done-more")
                hxTarget("#requirement-${requirement.id}")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+64") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 64}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/${requirement.id}/done-more")
                hxTarget("#requirement-${requirement.id}")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+1728") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 1728}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/${requirement.id}/done-more")
                hxTarget("#requirement-${requirement.id}")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+3456") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 3456}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/${requirement.id}/done-more")
                hxTarget("#requirement-${requirement.id}")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+ Custom") {
            buttonBlock = {
                onClick = "openCustomAmountModal(${requirement.id}, $worldId, $projectId, $taskId)"
            }
        }
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
            + project.createdAt.formatAsRelativeOrDate()
        }
        p("details-label") {
            + "Last Updated"
        }
        p {
            + project.updatedAt.formatAsRelativeOrDate()
        }
    }
}