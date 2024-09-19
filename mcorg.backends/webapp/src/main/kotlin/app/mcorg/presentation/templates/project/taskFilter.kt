package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.User
import app.mcorg.presentation.entities.TaskFiltersRequest
import kotlinx.html.*

fun MAIN.taskFilter(project: Project, worldUsers: List<User>, currentUser: User, filtersRequest: TaskFiltersRequest) {
    details {
        id = "project-tasks-filter-details"
        summary {
            + "Filter, sort and search"
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
                    value = "DONE"
                    selected = filtersRequest.sortBy == "DONE" || filtersRequest.sortBy == null
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
}