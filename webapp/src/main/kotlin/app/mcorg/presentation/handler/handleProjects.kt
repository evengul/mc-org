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

    val tasks = project.tasks.filter {
        val search = filters.search
        if (!search.isNullOrBlank()) {
            if ((it.assignee != null && it.assignee.username.lowercase().contains(search.lowercase())) || (!it.name.lowercase().contains(search.lowercase()))) {
                return@filter false
            }
        }

        val assigneeFilter = filters.assigneeFilter
        if (!assigneeFilter.isNullOrBlank()) {
            if ((assigneeFilter == "UNASSIGNED" && it.assignee != null)
                || (assigneeFilter == "MINE" && (it.assignee == null || it.assignee.id != userId))
                || (assigneeFilter.toIntOrNull() != null && (it.assignee == null || it.assignee.id.toString() != assigneeFilter))) {
                return@filter false
            }
        }

        val completionFilter = filters.completionFilter
        if (!completionFilter.isNullOrBlank()) {
            if ((completionFilter == "NOT_STARTED" && it.done > 0) ||
                (completionFilter == "IN_PROGRESS" && (it.done == 0 || it.isDone())) ||
                (completionFilter == "COMPLETE" && !it.isDone())) {
                return@filter false
            }
        }

        val typeFilter = filters.taskTypeFilter
        if (!typeFilter.isNullOrBlank()) {
            if ((typeFilter == "DOABLE" && it.isCountable()) ||
                (typeFilter == "COUNTABLE") && !it.isCountable()) {
                return@filter false
            }
        }

        val amountFilter = filters.amountFilter
        if (amountFilter != null) {
            if ((it.isCountable() && it.done < amountFilter) || (!it.isCountable() && amountFilter > 1)) {
                return@filter false
            }
        }

        return@filter true
    }

    val sortBy = filters.sortBy ?: "DONE"

    val sortedTasks = when(sortBy) {
        "DONE" -> tasks.sortedWith(::sortProjectsByCompletion)
        "ASSIGNEE" -> tasks.sortedByDescending { it.assignee?.username }
        else -> tasks.sortedBy { it.name }
    }

    respondHtml(
        project(
            "/app/worlds/${getWorldId()}/projects",
            project.copy(tasks = sortedTasks.toMutableList()),
            users,
            currentUser,
            filters
        )
    )
}

private fun sortProjectsByCompletion(a: Task, b: Task): Int {
    if (a.isDone()) {
        if (b.isDone()) {
            return a.name.compareTo(b.name)
        }
        return 1
    } else if(b.isDone()) {
        return -1
    }
    if (a.done == b.done && a.needed == b.needed) return a.name.compareTo(b.name)
    if (a.needed == b.needed) return b.done - a.done
    return b.needed - a.needed
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