package app.mcorg.presentation.handler

import app.mcorg.domain.Priority
import app.mcorg.domain.isCountable
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.entities.AssignUserRequest
import app.mcorg.presentation.entities.DeleteAssignmentRequest
import app.mcorg.presentation.router.utils.*
import app.mcorg.presentation.templates.task.*
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetAddTask() {
    respondHtml(addTask("/app/worlds/${getWorldId()}/projects/${getProjectId()}", getWorldId(), getProjectId()))
}

suspend fun ApplicationCall.handleGetAddDoableTask() {
    respondHtml(addDoableTask("/app/worlds/${getWorldId()}/projects/${getProjectId()}/add-task"))
}

suspend fun ApplicationCall.handleGetAddCountableTask() {
    respondHtml(addCountableTask("/app/worlds/${getWorldId()}/projects/${getProjectId()}/add-task"))
}

suspend fun ApplicationCall.handleGetUploadLitematicaTasks() {
    respondHtml(addLitematicaTasks(worldId = getWorldId(), projectId = getProjectId()))
}

suspend fun ApplicationCall.handlePostDoableTask() {
    val (name) = receiveDoableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addDoableTask(projectId, name, Priority.LOW)
    respondRedirect("/app/worlds/${getWorldId()}/projects/$projectId")
}

suspend fun ApplicationCall.handlePostCountableTask() {
    val (name, amount) = receiveCountableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addCountableTask(projectId, name, Priority.LOW, needed = amount)
    respondRedirect("/app/worlds/${getWorldId()}/projects/$projectId")
}

suspend fun ApplicationCall.handlePostLitematicaTasks() {
    val projectId = getProjectId()
    val tasks = receiveMaterialListTasks()
    tasks.forEach {
        projectsApi.addCountableTask(
            projectId = projectId,
            name = it.name,
            priority = Priority.LOW,
            needed = it.needed
        )
    }
    respondRedirect("/app/worlds/${getWorldId()}/projects/$projectId")
}

suspend fun ApplicationCall.handleDeleteTask() {
    val taskId = getTaskId()
    projectsApi.removeTask(taskId)
    respondEmptyHtml()
}

suspend fun ApplicationCall.handlePatchCountableTaskDoneMore() {
    val projectId = getProjectId()
    val done = parameters["done"]?.toIntOrNull() ?: 0
    val taskId = getTaskId()
    projectsApi.taskDoneMore(taskId, done)
    respondTask(projectId, taskId)
}

suspend fun ApplicationCall.handlePatchTaskAssignee() {
    val request = receiveAssignUserRequest()
    if (request is DeleteAssignmentRequest) return handleDeleteTaskAssignee()
    val (userId) = request as AssignUserRequest
    val worldId = getWorldId()
    val projectId = getProjectId()
    val taskId = getTaskId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.id == userId }
    if (user != null) {
        projectsApi.assignTask(taskId, user.id)
        respondTask(projectId, taskId)
    } else {
        throw IllegalArgumentException("User does not exist in project")
    }
}

suspend fun ApplicationCall.handleDeleteTaskAssignee() {
    val projectId = getProjectId()
    val taskId = getTaskId()
    projectsApi.removeTaskAssignment(taskId)
    respondTask(projectId, taskId)
}

suspend fun ApplicationCall.handleCompleteTask() {
    handleTaskCompletion(true)
}

suspend fun ApplicationCall.handleIncompleteTask() {
    handleTaskCompletion(false)
}

private suspend fun ApplicationCall.handleTaskCompletion(complete: Boolean) {
    val projectId = getProjectId()
    val taskId = getTaskId()
    if(complete) {
        projectsApi.completeTask(taskId)
    } else {
        projectsApi.undoCompleteTask(taskId)
    }
    respondTask(projectId, taskId)
}

suspend fun ApplicationCall.handleEditTaskRequirements() {
    val projectId = getProjectId()
    val (id, needed, done) = getEditCountableTaskRequirements()
    projectsApi.editTaskRequirements(id, needed, done)
    respondTask(projectId, id)
}

private suspend fun ApplicationCall.respondTask(projectId: Int, taskId: Int) {
    val project = projectsApi.getProject(projectId, includeTasks = true)
    val task = project?.tasks?.find { it.id == taskId }
    if (project != null && task != null) {
        val users = permissionsApi.getUsersInWorld(project.worldId)
        val currentUser = getUser()
        if (task.isCountable()) {
            respondHtml(createCountableTask(project, task, users, currentUser))
        } else {
            respondHtml(createDoableTask(project, task, users, currentUser))
        }
    } else {
        respond(HttpStatusCode.BadRequest)
    }
}