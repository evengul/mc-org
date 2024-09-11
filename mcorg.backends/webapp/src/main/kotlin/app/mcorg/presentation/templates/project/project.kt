package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.entities.TaskFiltersRequest
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate


fun project(backLink: String, project: Project, worldUsers: List<User>, currentUser: User, filtersRequest: TaskFiltersRequest): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("menu-add", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    editCountableDialog(project)
    taskFilter(project, worldUsers, currentUser, filtersRequest)
    taskList(worldUsers, currentUser, project)
}