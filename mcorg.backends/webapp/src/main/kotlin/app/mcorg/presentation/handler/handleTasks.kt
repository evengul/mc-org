package app.mcorg.presentation.handler

import app.mcorg.domain.Priority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.router.utils.*
import app.mcorg.presentation.templates.project.assignUser
import app.mcorg.presentation.templates.task.addCountableTask
import app.mcorg.presentation.templates.task.addDoableTask
import app.mcorg.presentation.templates.task.addTask
import app.mcorg.presentation.utils.*
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

suspend fun ApplicationCall.handleGetAssignTask() {
    val users = permissionsApi.getUsersInWorld(getWorldId())
    respondHtml(assignUser("/app/worlds/${getWorldId()}/projects/${getProjectId()}", users, null))
}

suspend fun ApplicationCall.handlePatchTaskAssignee() {
    val (username) = receiveAddUserRequest()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val taskId = getTaskId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.username == username }
    if (user != null) {
        projectsApi.assignTask(taskId, user.id)
        respondRedirect("/app/worlds/$worldId/projects/$projectId")
    } else {
        throw IllegalArgumentException("User does not exist in project")
    }
}

suspend fun ApplicationCall.handleDeleteTaskAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val taskId = getTaskId()
    projectsApi.removeTaskAssignment(taskId)
    respondRedirect("/app/worlds/$worldId/projects/$projectId")
}

suspend fun ApplicationCall.handleCompleteTask() {
    val taskId = getTaskId()
    projectsApi.completeTask(taskId)
    respondRedirect("/app/worlds/${getWorldId()}/projects/${getProjectId()}")
}

suspend fun ApplicationCall.handleIncompleteTask() {
    val taskId = getTaskId()
    projectsApi.undoCompleteTask(taskId)
    respondRedirect("/app/worlds/${getWorldId()}/projects/${getProjectId()}")
}