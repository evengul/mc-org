package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.SlimProject
import app.mcorg.domain.User
import app.mcorg.domain.sortUsersBySelectedOrName
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createAssignProject(project: SlimProject, worldUsers: List<User>, currentUser: User) = createHTML().select {
    assignProject(project, worldUsers, currentUser)
}

fun SELECT.assignProject(project: SlimProject, worldUsers: List<User>, currentUser: User) {
    name = "userId"
    classes = setOf("project-assignment")
    hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/assign")
    hxTarget("this")
    hxSwap("outerHTML")
    hxTrigger("change changed")
    option {
        selected = project.assignee == null
        value = "NONE"
        + "Unassigned"
    }
    sortUsersBySelectedOrName(worldUsers, currentUser, project.assignee).forEach {
        option {
            selected = it.id == project.assignee?.id
            value = it.id.toString()
            if (it.id == project.assignee?.id) {
                + "Assigned: ${it.username}"
            } else {
                + it.username
            }
        }
    }
}