package app.mcorg.presentation.handler

import app.mcorg.domain.Task
import app.mcorg.domain.isCountable
import app.mcorg.domain.isDone
import app.mcorg.domain.toSlim
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.entities.AssignUserRequest
import app.mcorg.presentation.entities.DeleteAssignmentRequest
import app.mcorg.presentation.router.utils.*
import app.mcorg.presentation.templates.project.*
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetProjects() {
    val worldId = getWorldId()
    val projects = projectsApi.getWorldProjects(worldId)
    val users = permissionsApi.getUsersInWorld(worldId)
    val currentUser = getUser()
    respondHtml(projects(worldId, projects, users, currentUser))
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
    respondRedirect("/app/worlds/$worldId/projects/$projectId/add-task")
}

suspend fun ApplicationCall.handleDeleteProject() {
    val projectId = getProjectId()
    projectsApi.deleteProject(projectId)
    respondEmptyHtml()
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
            if (it.assignee != null && !it.assignee.username.lowercase().contains(search.lowercase())) {
                return@filter false
            }
            if (!it.name.lowercase().contains(search.lowercase())) {
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

        return@filter true
    }

    val sortBy = filters.sortBy

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
    if (a.done == b.done) return a.name.compareTo(b.name)
    return b.done - a.done
}

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    val request = receiveAssignUserRequest()
    if (request is DeleteAssignmentRequest) return handleDeleteProjectAssignee()
    val (userId) = request as AssignUserRequest
    val projectId = getProjectId()
    val worldId = getWorldId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.id == userId }
    if (user != null) {
        projectsApi.assignProject(projectId, user.id)
        val currentUser = getUser()
        val project = projectsApi.getProject(projectId)?.toSlim() ?: throw IllegalArgumentException("Project does not exist")
        respondHtml(createAssignProject(project, users, currentUser))
    } else {
        throw IllegalArgumentException("User is not in project")
    }
}

suspend fun ApplicationCall.handleDeleteProjectAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    projectsApi.removeProjectAssignment(projectId)
    val worldUsers = permissionsApi.getUsersInWorld(worldId)
    val currentUser = getUser()
    val project = projectsApi.getProject(projectId)?.toSlim() ?: throw IllegalArgumentException("Project does not exist")
    respondHtml(createAssignProject(project, worldUsers, currentUser))
}