package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.task.ActionTask
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.form.searchField.searchField
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import kotlinx.html.*

fun DIV.tasksTab(tab: ProjectTab.Tasks) {
    classes += "project-tasks-tab"
    projectProgressSection(tab.project)
    taskManagementSection(tab)
}

private fun DIV.projectProgressSection(project: Project) {
    div("project-tasks-progress") {
        h2 {
            + "Project Progress"
        }
        projectProgress(project.tasksCompleted, project.tasksTotal)
    }
}

fun DIV.projectProgress(completed: Int, total: Int) {
    progressComponent {
        id = "project-progress"
        value = completed.toDouble()
        max = total.toDouble()
        showPercentage = false
        label = "$completed of $total task${if(total == 1) "" else "s"} completed"
    }
}

private fun DIV.taskManagementSection(tab: ProjectTab.Tasks) {
    div("project-tasks") {
        taskManagementHeader(tab.project.worldId, tab.project.id)
        form {
            id = "project-create-action-task-form"

            encType = FormEncType.applicationXWwwFormUrlEncoded

            hxPost(Link.Worlds.world(tab.project.worldId).project(tab.project.id).tasks().to)
            hxTarget("#tasks-list")
            hxSwap("afterbegin")

            // language=js
            attributes["hx-on::after-request"] = """
                if (event.detail.xhr.status < 300) {
                    document.getElementById('new-task-name-input').value = '';
                    document.getElementById('validation-error-name').innerText = '';
                    document.getElementById('new-task-name-input').focus();
                }
            """.trimIndent()

            input {
                type = InputType.text
                minLength = "3"
                maxLength = "100"
                required = true
                name = "name"
                placeholder = "New task name..."
                id = "new-task-name-input"
            }
            p("validation-error-message") {
                id = "validation-error-name"
            }
            neutralButton("Add Task") {
                iconLeft = Icons.MENU_ADD
                iconSize = IconSize.SMALL
            }
        }
        if (tab.totalTasksCount == 0) {
            div {
                emptyTasksDisplay()
            }
        }
        p {
            id = "no-tasks-found"
            classes += "subtle"
            if (tab.tasks.isEmpty() && tab.totalTasksCount > 0) {
                + "No tasks found matching the search criteria."
            }
        }
        ul {
            tasksList(tab.project.worldId, tab.project.id, tab.tasks)
        }
    }
}

private fun DIV.taskManagementHeader(worldId: Int, projectId: Int) {
    h2 {
        + "Tasks"
    }
    taskSearchAndFilters(worldId, projectId)
}

private fun DIV.taskSearchAndFilters(worldId: Int, projectId: Int) {
    form(classes = "project-tasks-search-filter") {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxGet("${Link.Worlds.world(worldId).project(projectId).tasks().to}/search")
        hxTarget("#tasks-list")
        hxSwap("outerHTML")
        hxIndicator(".search-wrapper")
        hxTrigger("""
            input from:#task-search-input delay:500ms,
            change from:#task-search-input changed,
            change from:select[name="completionStatus"],
            change from:select[name="priority"],
            change from:select[name="stage"],
            change from:select[name="sortBy"]
            reset delay:100ms,
            submit
        """.trimIndent())
        searchField("task-search-input") {
            placeHolder = "Search tasks by name or description..."
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
            name = "sortBy"
            option {
                value = "lastModified_desc"
                + "Sort by Last Modified"
            }
            option {
                value = "name_asc"
                + "Sort by Name (A-Z)"
            }
        }
        neutralButton("Clear filters") {
            buttonBlock = {
                type = ButtonType.reset
            }
        }
    }
}

fun DIV.emptyTasksDisplay() {
    emptyState(
        id = "empty-tasks-state",
        title = "No Tasks Yet",
        description = "Create your first task to break down this project into manageable steps.",
        icon = Icons.MENU_ADD
    ) {
        actionButton("Add first task") {
            // language=js
            onClick = "document.getElementById('new-task-name-input')?.focus()"
        }
    }
}

fun UL.tasksList(worldId: Int, projectId: Int, tasks: List<ActionTask>) {
    id = "tasks-list"
    tasks.forEach { task ->
        li {
            taskItem(worldId, projectId, task)
        }
    }
}

fun LI.taskItem(worldId: Int, projectId: Int, task: ActionTask) {
    classes += "task-item"
    id = "task-${task.id}"
    div("task-item-start") {
        input {
            taskCompletionCheckbox(worldId, projectId, task.id, task.completed)
        }
        h3 {
            + task.name
        }
    }
    div("task-item-end") {
        iconButton(
            icon = Icons.DELETE,
            ariaLabel = "Delete task",
            iconSize = IconSize.SMALL,
            color = IconButtonColor.DANGER,
        ) {
            buttonBlock = {
                hxDeleteWithConfirm(
                    url = Link.Worlds.world(worldId).project(projectId).tasks().task(task.id),
                    title = "Delete Task",
                    description = "Are you sure you want to delete the task \"${task.name}\"? This action cannot be undone."
                )
                hxTarget("#task-${task.id}")
                hxSwap("delete")
                title = "Delete task"
            }
        }
    }
}

fun INPUT.taskCompletionCheckbox(worldId: Int, projectId: Int, taskId: Int, completed: Boolean) {
    id = "task-${taskId}-complete"
    classes += "task-completion-checkbox"
    checked = completed
    disabled = completed
    hxTarget("#task-${taskId}-complete")
    hxPatch(Link.Worlds.world(worldId).project(projectId).tasks().task(taskId) + "/complete")
    hxSwap("outerHTML")
    type = InputType.checkBox
}

