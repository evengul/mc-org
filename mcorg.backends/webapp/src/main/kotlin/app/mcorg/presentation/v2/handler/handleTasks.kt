package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.v2.configuration.projectsApi
import app.mcorg.presentation.v2.router.utils.clientRefresh
import app.mcorg.presentation.v2.router.utils.getTaskId
import app.mcorg.presentation.v2.router.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetAddTask() {
    respondHtml("Add task")
}

suspend fun ApplicationCall.handleGetAddDoableTask() {
    respondHtml("Add doable task")
}

suspend fun ApplicationCall.handleGetAddCountableTask() {
    respondHtml("Add countable task")
}

suspend fun ApplicationCall.handlePostDoableTask() {
    // TODO: Read form and create task
    clientRefresh()
}

suspend fun ApplicationCall.handlePostCountableTask() {
    // TODO: Read form and create task
    clientRefresh()
}

suspend fun ApplicationCall.handleGetAssignTask() {
    respondHtml("Assign task")
}

suspend fun ApplicationCall.handlePatchTaskAssignee() {
    // TODO: Read form and assign task if user is in project
    clientRefresh()
}

suspend fun ApplicationCall.handleCompleteTask() {
    val taskId = getTaskId()
    projectsApi.completeTask(taskId)
    clientRefresh()
}

suspend fun ApplicationCall.handleIncompleteTask() {
    val taskId = getTaskId()
    projectsApi.undoCompleteTask(taskId)
    clientRefresh()
}