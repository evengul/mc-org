package app.mcorg.presentation.handler

import app.mcorg.domain.TaskType
import app.mcorg.domain.isCountable
import app.mcorg.domain.isDone
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
import java.util.*

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
    respondRedirect("/app/worlds/$worldId/projects/$projectId/add-task")
}

suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    projectsApi.deleteProject(projectId)
    clientRedirect("/app/worlds/$worldId")
}

suspend fun ApplicationCall.handleGetProject() {
    val userId = getUserId()
    val projectId = getProjectId()
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
                || (assigneeFilter == "MINE" && (it.assignee == null || it.assignee.id != userId))) {
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
        "DONE" -> tasks.sortedBy { it.isDone() }
        "ASSIGNEE" -> tasks.sortedByDescending { it.assignee?.username }
        else -> tasks.sortedBy { it.name }
    }

    respondHtml(project("/app/worlds/${getWorldId()}/projects", project.copy(tasks = sortedTasks.toMutableList()), filters))
}

suspend fun ApplicationCall.handleGetAssignProject() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val from = parameters["from"] ?: "single"
    val selectedUser = projectsApi.getProjectAssignee(projectId)
    val projectLink = "/app/worlds/$worldId/projects/$projectId"
    val backLink = when(from) {
        "list" -> "/app/worlds/$worldId/projects"
        else -> projectLink
    }
    respondHtml(assignUser(backLink, "$projectLink/assign", from, users, selectedUser?.id))
}

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    val (username) = receiveAssignUserRequest()
    val from = parameters["from"] ?: "single"
    val projectId = getProjectId()
    val worldId = getWorldId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.username == username }
    if (user != null) {
        projectsApi.assignProject(projectId, user.id)
        if (from == "list") {
            clientRedirect("/app/worlds/$worldId/projects")
        } else {
            clientRedirect("/app/worlds/$worldId/projects/$projectId")
        }
    } else {
        throw IllegalArgumentException("User is not in project")
    }
}

suspend fun ApplicationCall.handleDeleteProjectAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val from = parameters["from"] ?: "single"
    projectsApi.removeProjectAssignment(projectId)
    if (from == "list") {
        clientRedirect("/app/worlds/$worldId/projects")
    } else {
        clientRedirect("/app/worlds/$worldId/projects/$projectId")
    }
}