package app.mcorg.presentation.templates.project

import app.mcorg.domain.model.projects.SlimProject
import app.mcorg.domain.model.users.User
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.assign
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createAssignProject(project: SlimProject, worldUsers: List<User>, currentUser: User) = createHTML().select {
    assignProject(project, worldUsers, currentUser)
}

fun SELECT.assignProject(project: SlimProject, worldUsers: List<User>, currentUser: User) {
    classes = setOf("project-assignment")
    hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/assign")
    hxTarget("this")
    assign(worldUsers, currentUser, project.assignee)
}