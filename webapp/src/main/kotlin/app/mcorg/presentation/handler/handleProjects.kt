package app.mcorg.presentation.handler

import app.mcorg.domain.model.projects.filterSortTasks
import app.mcorg.domain.model.projects.matches
import app.mcorg.domain.model.users.User
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.entities.user.AssignUserRequest
import app.mcorg.presentation.entities.user.DeleteAssignmentRequest
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.URLMappers
import app.mcorg.presentation.mappers.project.createProjectInputMapper
import app.mcorg.presentation.mappers.project.projectFilterInputMapper
import app.mcorg.presentation.mappers.project.projectFilterURLMapper
import app.mcorg.presentation.mappers.task.taskFilterInputMapper
import app.mcorg.presentation.mappers.user.assignUserInputMapper
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
    val specification = InputMappers.projectFilterInputMapper(parameters)
    val displayedProjects = projects.filter { it.matches(specification) }.sortedBy { it.name }
    respondHtml(projects(worldId, displayedProjects, users, currentUser, specification, projects.size))
}

suspend fun ApplicationCall.handlePostProject() {
    val (name, priority, dimension, requiresPerimeter) = InputMappers.createProjectInputMapper(receiveParameters())
    val worldId = getWorldId()
    val projectId = projectsApi.createProject(worldId, name, dimension, priority, requiresPerimeter)
    val project = projectsApi.getProject(projectId)?.toSlim() ?: throw NotFoundException()
    val users = permissionsApi.getUsersInWorld(worldId)
    val currentUser = getUser()
    val filter = URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"])
    if (project.matches(filter)) {
        respondHtml(
            getFilteredAndTotalProjectBasedOnFilter(worldId, request) + "\n" + createProjectListElement(
                worldId,
                project,
                users,
                currentUser
            )
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
    val project = projectsApi.getProject(projectId, includeTasks = true, includeDependencies = false)
        ?: throw IllegalArgumentException("Project not found")

    val taskSpecification = InputMappers.taskFilterInputMapper(parameters)

    respondHtml(
        project(
            "/app/worlds/${getWorldId()}/projects",
            project.filterSortTasks(taskSpecification, userId),
            users,
            currentUser,
            taskSpecification
        )
    )
}

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    val request = InputMappers.assignUserInputMapper(receiveParameters())

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
    val specification = URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"])
    val allProjects = projectsApi.getWorldProjects(worldId)
    val filteredProjects = allProjects.filter { it.matches(specification) }.size
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
    val project =
        projectsApi.getProject(projectId)?.toSlim() ?: throw IllegalArgumentException("Project does not exist")
    val filter = URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"])
    if (project.matches(filter)) {
        respondHtml(
            getFilteredAndTotalProjectBasedOnFilter(worldId, this.request) + "\n" + createAssignProject(
                project,
                worldUsers,
                currentUser
            )
        )
    } else {
        hxTarget("#project-${project.id}")
        hxSwap("outerHTML")
        respondHtml(createHTML().div {
            id = "project-${project.id}"
            hxOutOfBands("delete")
        } + "\n" + getFilteredAndTotalProjectBasedOnFilter(worldId, this.request))
    }
}