package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.entities.TaskFiltersRequest
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.ul

fun project(backLink: String, project: Project, worldUsers: List<User>, currentUser: User, filtersRequest: TaskFiltersRequest): String = subPageTemplate(
    title = project.name,
    backLink = backLink,
    rightIcons = listOf(
        NavBarRightIcon(
            icon = "filter",
            id = "add-task-button",
            title = "Add task",
            onClick = "showDialog('task-filters-dialog')"
        )
    )
) {
    editCountableDialog(project)
    taskFilterDialog(project, worldUsers, currentUser, filtersRequest)
    ul {
        taskList(users = worldUsers, project = project, currentUser = currentUser)
    }
    addTaskButtons(project)
}