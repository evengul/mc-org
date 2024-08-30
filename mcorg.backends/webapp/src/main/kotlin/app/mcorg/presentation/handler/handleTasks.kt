package app.mcorg.presentation.handler

import app.mcorg.domain.Priority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.router.utils.*
import app.mcorg.presentation.templates.project.assignUser
import app.mcorg.presentation.templates.task.addCountableTask
import app.mcorg.presentation.templates.task.addDoableTask
import app.mcorg.presentation.templates.task.addTask
import app.mcorg.presentation.utils.receiveAddUserRequest
import app.mcorg.presentation.utils.receiveCountableTaskRequest
import app.mcorg.presentation.utils.receiveDoableTaskRequest
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetAddTask() {
    respondHtml(addTask(getWorldId(), getProjectId()))
}

suspend fun ApplicationCall.handleGetAddDoableTask() {
    respondHtml(addDoableTask())
}

suspend fun ApplicationCall.handleGetAddCountableTask() {
    respondHtml(addCountableTask())
}

suspend fun ApplicationCall.handlePostDoableTask() {
    val (name) = receiveDoableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addDoableTask(projectId, name, Priority.LOW)
    respondRedirect("/app/world/${getWorldId()}/project/$projectId")
}

suspend fun ApplicationCall.handlePostCountableTask() {
    val (name, amount) = receiveCountableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addCountableTask(projectId, name, Priority.LOW, needed = amount)
    respondRedirect("/app/world/${getWorldId()}/project/$projectId")
}

suspend fun ApplicationCall.handleGetAssignTask() {
    val users = permissionsApi.getUsersInWorld(getWorldId())
    respondHtml(assignUser(users, null))
}

suspend fun ApplicationCall.handlePatchTaskAssignee() {
    val (username) = receiveAddUserRequest()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val users = permissionsApi.getUsersInWorld(worldId)
    val user = users.find { it.username == username }
    if (user != null) {
        respondRedirect("/app/world/$worldId/project/$projectId")
        // TODO: Assign user to task
    } else {
        throw IllegalArgumentException("User does not exist in project")
    }
}

suspend fun ApplicationCall.handleCompleteTask() {
    val taskId = getTaskId()
    projectsApi.completeTask(taskId)
    respondRedirect("/app/world/${getWorldId()}/project/${getProjectId()}")
}

suspend fun ApplicationCall.handleIncompleteTask() {
    val taskId = getTaskId()
    projectsApi.undoCompleteTask(taskId)
    respondRedirect("/app/world/${getWorldId()}/project/${getProjectId()}")
}