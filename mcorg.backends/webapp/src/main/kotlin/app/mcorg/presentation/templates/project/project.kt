package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.entities.TaskFiltersRequest
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import io.ktor.util.*
import kotlinx.html.*

fun project(backLink: String, project: Project, filtersRequest: TaskFiltersRequest): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("user", "Assign user", "/app/worlds/${project.worldId}/projects/${project.id}/assign?from=single"),
    NavBarRightIcon("menu-add", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    dialog {
        id = "edit-task-dialog"
        h1 {
            + "Edit task"
        }
        form {
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/requirements")
            input {
                id = "edit-task-id-input"
                name = "id"
                type = InputType.text
            }
            label {
                htmlFor = "edit-task-done-input"
                + "Done"
            }
            input {
                name = "done"
                id = "edit-task-done-input"
                type = InputType.number
                required = true
                min = "0"
            }
            label {
                htmlFor = "edit-task-needed-input"
                + "Needed"
            }
            input {
                name = "needed"
                id = "edit-task-needed-input"
                type = InputType.number
                required = true
                min = "0"
                max = "200000000"
            }
            span {
                classes = setOf("button-row")
                button {
                    onClick = "cancelDialog()"
                    classes = setOf("button-secondary")
                    type = ButtonType.button
                    + "Cancel"
                }
                button {
                    type = ButtonType.submit
                    + "Save"
                }
            }
        }
    }
    details {
        id = "project-tasks-filter-details"
        summary {
            + "Filter"
        }
        form {
            label {
                htmlFor = "project-tasks-filter-search-input"
                + "Search by assignee or task name"
            }
            input {
                id = "project-tasks-filter-search-input"
                name = "search"
                filtersRequest.search?.let { value = it }
                type = InputType.text
            }
            label {
                htmlFor = "project-tasks-filter-sort-by-select"
                + "Sort by"
            }
            select {
                id = "project-tasks-filter-sort-by-select"
                name = "sortBy"
                option {
                    + ""
                }
                option {
                    value = "DONE"
                    selected = filtersRequest.sortBy == "DONE"
                    + "Done (completed at the bottom)"
                }
                option {
                    selected = filtersRequest.sortBy == "ASSIGNEE"
                    value = "ASSIGNEE"
                    + "Assignee"
                }
            }
            label {
                htmlFor = "assigneeFilter"
                + "Filter by assignee"
            }
            select {
                id = "project-tasks-filter-assigned-select"
                name = "assigneeFilter"
                option {
                    + ""
                }
                option {
                    selected = filtersRequest.assigneeFilter == "UNASSIGNED"
                    value = "UNASSIGNED"
                    + "Unassigned"
                }
                option {
                    selected = filtersRequest.assigneeFilter == "MINE"
                    value = "MINE"
                    + "Yours"
                }
                // TODO: All the other users
            }
            label {
                htmlFor = "project-tasks-filter-completion-select"
                + "Filter by completion state"
            }
            select {
                id = "project-tasks-filter-completion-select"
                name = "completionFilter"
                option {
                    + ""
                }
                option {
                    selected = filtersRequest.completionFilter == "NOT_STARTED"
                    value = "NOT_STARTED"
                    + "Not started"
                }
                option {
                    selected = filtersRequest.completionFilter == "IN_PROGRESS"
                    value = "IN_PROGRESS"
                    + "In progress"
                }
                option {
                    selected = filtersRequest.completionFilter == "COMPLETE"
                    value = "COMPLETE"
                    + "Completed"
                }
            }
            label {
                htmlFor = "project-tasks-filter-type-select"
                + "Filter by task type"
            }
            select {
                id = "project-tasks-filter-type-select"
                name = "taskTypeFilter"
                option {
                    + ""
                }
                option {
                    selected = filtersRequest.taskTypeFilter == "DOABLE"
                    value = "DOABLE"
                    + "Doable"
                }
                option {
                    selected = filtersRequest.taskTypeFilter == "COUNTABLE"
                    value = "COUNTABLE"
                    + "Countable"
                }
            }
            span {
                classes = setOf("button-row")
                a {
                    href = "/app/worlds/${project.worldId}/projects/${project.id}"
                    button {
                        classes = setOf("button-secondary")
                        type = ButtonType.button
                        + "Clear filters"
                    }
                }
                button {
                    type = ButtonType.submit
                    + "Apply"
                }
            }
        }
    }
    ul {
        id = "task-list"
        script {
            src = "/static/scripts/progress-edit-dialog.js"
            nonce = generateNonce()
        }
        project.tasks.forEach {
            if (it.isCountable()) {
                li {
                    classes = setOf("task")
                    div {
                        classes = setOf("task-name-assign")
                        h2 {
                            + it.name
                        }
                        assignTask(project, it)
                    }
                    div {
                        classes = setOf("doable-task-progress")
                        appProgress(max = it.needed.toDouble(), value = it.done.toDouble(), isItemAmount = true)
                        button {
                            disabled = it.done >= it.needed
                            val toAdd = (it.needed - it.done + 64).coerceAtMost(64)
                            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=$toAdd")
                            + "+1 stack"
                        }
                        button {
                            disabled = it.done >= it.needed
                            val toAdd = (it.needed - it.done + 64).coerceAtMost(1728)
                            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=$toAdd")
                            + "+1 Shulker box"
                        }
                        button {
                            disabled = it.done >= it.needed
                            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=${it.needed - it.done}")
                            + "Done"
                        }
                        button {
                            classes = setOf("button-danger")
                            hxDelete("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}")
                            + "Delete"
                        }
                        button {
                            id = "edit-task-${it.id}"
                            classes = setOf("button-secondary")
                            onClick = "editTask(this)"
                            attributes["id"] = it.id.toString()
                            attributes["needed"] = it.needed.toString()
                            attributes["done"] = it.done.toString()
                            + "Edit"
                        }
                    }
                }
            } else {
                li {
                    classes = setOf("task")
                    div {
                        classes = setOf("task-name-assign")
                        h2 {
                            + it.name
                        }
                        assignTask(project, it)
                    }
                    span {
                        classes = setOf("doable-task-actions")
                        input {
                            id = "project-doable-task-${it.id}-change-input"
                            if (it.isDone()) {
                                hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/incomplete")
                            } else {
                                hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/complete")
                            }
                            type = InputType.checkBox
                            checked = it.isDone()
                        }
                        button {
                            classes = setOf("button-danger")
                            hxDelete("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}")
                            + "Delete"
                        }
                    }
                }
            }
        }
    }
}

private fun DIV.assignTask(project: Project, task: Task) {
    a {
        id = "project-task-${task.id}-assign-link"
        href = "/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/assign"
        button {
            id = "project-task-${task.id}-assign-button"
            if (task.assignee == null) {
                classes = setOf("project-task-assign-button", "button-secondary", "icon-row")
                span {
                    classes = setOf("icon-small", "icon-user-small")
                }
                p {
                    + "Assign user"
                }
            } else {
                classes = setOf("project-task-assign-button", "selected", "button-secondary", "icon-row")
                span {
                    classes = setOf("icon-small", "icon-user-small")
                }
                p {
                    + task.assignee.username
                }
            }
        }
    }
}