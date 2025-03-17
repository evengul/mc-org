package app.mcorg.presentation.templates.project

import app.mcorg.domain.projects.Project
import app.mcorg.domain.users.User
import app.mcorg.presentation.entities.task.TaskFiltersRequest
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import app.mcorg.presentation.templates.task.addCountableDialog
import app.mcorg.presentation.templates.task.addDoableTaskDialog
import app.mcorg.presentation.templates.task.addLitematicaTasksDialog
import app.mcorg.presentation.templates.task.board.taskBoard
import kotlinx.html.MAIN

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
    projectDialogs(project)
    taskFilterDialog(project, worldUsers, currentUser, filtersRequest)
    taskBoard(project, worldUsers, currentUser)
}

fun MAIN.projectDialogs(project: Project) {
    editCountableDialog(project)
    addCountableDialog(project)
    addDoableTaskDialog(project)
    addLitematicaTasksDialog(project)
}