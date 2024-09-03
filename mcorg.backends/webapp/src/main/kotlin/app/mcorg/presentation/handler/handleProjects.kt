package app.mcorg.presentation.handler

import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.router.utils.*
import app.mcorg.presentation.templates.project.addProject
import app.mcorg.presentation.templates.project.assignUser
import app.mcorg.presentation.templates.project.project
import app.mcorg.presentation.templates.project.projects
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProjects() {
    val worldId = getWorldId()
    val projects = projectsApi.getWorldProjects(worldId)
    respondHtml(projects(worldId, projects))
}

suspend fun ApplicationCall.handleGetAddProject() {
    val userId = getUserId()
    val profile = usersApi.getProfile(userId) ?: throw IllegalArgumentException("No user found")
    respondHtml(addProject(backLink = "/app/worlds/${getWorldId()}/projects", profile.technicalPlayer))
}

suspend fun ApplicationCall.handlePostProject() {
    val (name, priority, dimension, requiresPerimeter) = receiveCreateProjectRequest()
    val worldId = getWorldId()
    val projectId = projectsApi.createProject(worldId, name, dimension, priority, requiresPerimeter)
    respondRedirect("/app/worlds/$worldId/projects/$projectId")
}

suspend fun ApplicationCall.handleGetProject() {
    val projectId = getProjectId()
    val project = projectsApi.getProject(projectId, includeTasks = true, includeDependencies = false) ?: throw IllegalArgumentException("Project not found")
    respondHtml(project("/app/worlds/${getWorldId()}/projects", project))
}

suspend fun ApplicationCall.handleGetAssignProject() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val selectedUser = projectsApi.getProjectAssignee(projectId)
    val projectLink = "/app/worlds/$worldId/projects/$projectId"
    respondHtml(assignUser(projectLink, "$projectLink/assign", users, selectedUser?.id))
}

suspend fun ApplicationCall.handlePostProjectAssignee() {
    val (username) = receiveAssignUserRequest()
    val projectId = getProjectId()
    val worldId = getWorldId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.username == username }
    if (user != null) {
        projectsApi.assignProject(projectId, user.id)
        clientRefresh()
    } else {
        throw IllegalArgumentException("User is not in project")
    }
}

suspend fun ApplicationCall.handleDeleteProjectAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    projectsApi.removeProjectAssignment(projectId)
    respondRedirect("/app/worlds/$worldId/projects/$projectId")
}