package app.mcorg.presentation.templates.project

import app.mcorg.domain.projects.Project
import app.mcorg.domain.users.User
import app.mcorg.presentation.entities.task.TaskFiltersRequest
import kotlinx.html.*

fun MAIN.taskFilterDialog(project: Project, worldUsers: List<User>, currentUser: User, filtersRequest: TaskFiltersRequest) {
    dialog {
        id = "task-filters-dialog"
        h1 {
            + "Filter tasks"
        }
        form {
            id = "project-tasks-filter-form"
            input {
                id = "project-tasks-filter-search-input"
                name = "search"
                placeholder = "Search by assignee or task name"
                filtersRequest.search?.let { value = it }
                type = InputType.text
            }
            select {
                id = "project-tasks-filter-sort-by-select"
                name = "sortBy"
                option {
                    value = "DONE"
                    selected = filtersRequest.sortBy == "DONE" || filtersRequest.sortBy == null
                    + "Sort by: Done (completed at the bottom)"
                }
                option {
                    selected = filtersRequest.sortBy == "ASSIGNEE"
                    value = "ASSIGNEE"
                    + "Sort by: Assignee"
                }
            }
            select {
                id = "project-tasks-filter-assigned-select"
                name = "assigneeFilter"
                option {
                    value = ""
                    + "Assigned to: Anyone or no one"
                }
                option {
                    selected = filtersRequest.assigneeFilter == "UNASSIGNED"
                    value = "UNASSIGNED"
                    + "Assigned to: Unassigned"
                }
                option {
                    selected = filtersRequest.assigneeFilter == "MINE"
                    value = "MINE"
                    + "Assigned to: Yours"
                }
                worldUsers
                    .filter { it.id != currentUser.id }
                    .sortedBy { it.username }
                    .forEach {
                        option {
                            selected = filtersRequest.assigneeFilter == it.id.toString()
                            value = it.id.toString()
                            + it.username
                        }
                    }
            }
            select {
                id = "project-tasks-filter-completion-select"
                name = "completionFilter"
                option {
                    + "Completed state: Any"
                }
                option {
                    selected = filtersRequest.completionFilter == "NOT_STARTED"
                    value = "NOT_STARTED"
                    + "Completed state: Not started"
                }
                option {
                    selected = filtersRequest.completionFilter == "IN_PROGRESS"
                    value = "IN_PROGRESS"
                    + "Completed state: In progress"
                }
                option {
                    selected = filtersRequest.completionFilter == "COMPLETE"
                    value = "COMPLETE"
                    + "Completed state: Completed"
                }
            }
            select {
                id = "project-tasks-filter-type-select"
                name = "taskTypeFilter"
                option {
                    + "Task type: Any"
                }
                option {
                    selected = filtersRequest.taskTypeFilter == "DOABLE"
                    value = "DOABLE"
                    + "Task type: Doable"
                }
                option {
                    selected = filtersRequest.taskTypeFilter == "COUNTABLE"
                    value = "COUNTABLE"
                    + "Task type: Countable"
                }
            }
            input {
                name = "amountFilter"
                id = "project-tasks-filter-amount-input"
                placeholder = "Amount done"
                type = InputType.number
                min = "0"
                max = Int.MAX_VALUE.toString()
            }
            a {
                href = "/app/worlds/${project.worldId}/projects/${project.id}"
                button {
                    classes = setOf("button-secondary")
                    type = ButtonType.button
                    + "Clear filters"
                }
            }
            button {
                type = ButtonType.button
                onClick = "hideDialog('task-filters-dialog')"
                classes = setOf("button-secondary")
                + "Cancel"
            }
            button {
                type = ButtonType.submit
                + "Apply"
            }
        }
    }
}