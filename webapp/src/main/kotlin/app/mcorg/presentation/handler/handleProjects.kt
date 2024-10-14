package app.mcorg.presentation.handler

import app.mcorg.domain.*
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.entities.AssignUserRequest
import app.mcorg.presentation.entities.DeleteAssignmentRequest
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templates.project.*
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetProjects() {
    val worldId = getWorldId()
    val projects = projectsApi.getWorldProjects(worldId)
    val users = permissionsApi.getUsersInWorld(worldId)
    val currentUser = usersApi.getProfile(getUserId()) ?: throw NotFoundException()
    val filters = receiveProjectFilters()
    respondHtml(projects(worldId, projects, users, currentUser, filters))
}

suspend fun ApplicationCall.handlePostProject() {
    val (name, priority, dimension, requiresPerimeter) = receiveCreateProjectRequest()
    val worldId = getWorldId()
    val projectId = projectsApi.createProject(worldId, name, dimension, priority, requiresPerimeter)
    val project = projectsApi.getProject(projectId)?.toSlim() ?: throw NotFoundException()
    val users = permissionsApi.getUsersInWorld(worldId)
    val currentUser = getUser()
    val filter = createProjectFilters(request.headers["HX-Current-URL"])
    if (project.allowedByFilter(filter)) {
        respondHtml(
            getFilteredAndTotalProjectBasedOnFilter(worldId, request) + "\n" + createProjectListElement(worldId, project, users, currentUser)
        )
    } else {
        respondHtml(getFilteredAndTotalProjectBasedOnFilter(worldId, request))
    }
}

suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    projectsApi.deleteProject(projectId)
    respondHtml(getFilteredAndTotalProjectBasedOnFilter(worldId, request))
}

suspend fun ApplicationCall.handleGetProject() {
    val userId = getUserId()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val currentUser = getUser()
    val users = permissionsApi.getUsersInWorld(worldId)
    val project = projectsApi.getProject(projectId, includeTasks = true, includeDependencies = false) ?: throw IllegalArgumentException("Project not found")

    val filters = receiveTaskFilters()

    respondHtml(
        project(
            "/app/worlds/${getWorldId()}/projects",
            filterAndSortProject(userId, project, filters),
            users,
            currentUser,
            filters
        )
    )
}

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    val request = receiveAssignUserRequest()
    if (request is DeleteAssignmentRequest) return handleDeleteProjectAssignee()
    val (userId) = request as AssignUserRequest
    val projectId = getProjectId()
    val worldId = getWorldId()
    val worldUsers = permissionsApi.getUsersInWorld(worldId)
    val user = worldUsers.find { it.id == userId }
    if (user != null) {
        projectsApi.assignProject(projectId, user.id)
        handleEditProjectAssignee(projectId, worldUsers)
    } else {
        throw IllegalArgumentException("User is not in project")
    }
}

private fun getFilteredAndTotalProjectBasedOnFilter(worldId: Int, request: ApplicationRequest): String {
    val filter = createProjectFilters(request.headers["HX-Current-URL"])
    val allProjects = projectsApi.getWorldProjects(worldId)
    val filteredProjects = allProjects.filter { it.allowedByFilter(filter) }.size
    if (filteredProjects != allProjects.size) {
        return createHTML().p {
            oobFilteredProjectsDisplay(filteredProjects, allProjects.size)
        }
    }
    return ""
}

suspend fun ApplicationCall.handleDeleteProjectAssignee() {
    val projectId = getProjectId()
    val worldId = getWorldId()
    projectsApi.removeProjectAssignment(projectId)
    val worldUsers = permissionsApi.getUsersInWorld(worldId)
    handleEditProjectAssignee(projectId, worldUsers)
}

private suspend fun ApplicationCall.handleEditProjectAssignee(projectId: Int, worldUsers: List<User>) {
    val worldId = getWorldId()
    val currentUser = getUser()
    val project = projectsApi.getProject(projectId)?.toSlim() ?: throw IllegalArgumentException("Project does not exist")
    val filter = createProjectFilters(this.request.headers["HX-Current-URL"])
    if (project.allowedByFilter(filter)) {
        respondHtml(getFilteredAndTotalProjectBasedOnFilter(worldId, this.request) + "\n" + createAssignProject(project, worldUsers, currentUser))
    } else {
        hxTarget("#project-${project.id}")
        hxSwap("outerHTML")
        respondHtml(createHTML().div {
            id = "project-${project.id}"
            hxOutOfBands("delete")
        } + "\n" + getFilteredAndTotalProjectBasedOnFilter(worldId, this.request))
    }
}