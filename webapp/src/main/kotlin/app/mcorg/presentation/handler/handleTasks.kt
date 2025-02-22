package app.mcorg.presentation.handler

import app.mcorg.domain.projects.Priority
import app.mcorg.domain.projects.TaskStages
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.entities.AssignUserRequest
import app.mcorg.presentation.entities.DeleteAssignmentRequest
import app.mcorg.presentation.templates.task.board.createTaskBoard
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handlePostDoableTask() {
    val (name) = receiveDoableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addDoableTask(projectId, name, Priority.LOW)

    respondUpdatedTaskList(projectId)
}

suspend fun ApplicationCall.handlePostCountableTask() {
    val (name, amount) = receiveCountableTaskRequest()
    val projectId = getProjectId()
    projectsApi.addCountableTask(projectId, name, Priority.LOW, needed = amount)
    respondUpdatedTaskList(projectId)
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
    respondUpdatedTaskList(projectId)
}

suspend fun ApplicationCall.handleDeleteTask() {
    val projectId = getProjectId()
    val taskId = getTaskId()
    projectsApi.removeTask(taskId)

    respondUpdatedTaskList(projectId)
}

suspend fun ApplicationCall.handlePatchCountableTaskDoneMore() {
    val projectId = getProjectId()
    val done = parameters["done"]?.toIntOrNull() ?: 0
    val taskId = getTaskId()
    projectsApi.taskDoneMore(taskId, done)

    respondUpdatedTaskList(projectId)
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

        respondUpdatedTaskList(projectId)
    } else {
        throw IllegalArgumentException("User does not exist in project")
    }
}

suspend fun ApplicationCall.handleUpdateTaskStage() {
    val stage = when(parameters["stage"]) {
        "TODO" -> TaskStages.TODO
        "IN_PROGRESS" -> TaskStages.IN_PROGRESS
        "DONE" -> TaskStages.DONE
        else -> throw BadRequestException("Invalid stage")
    }

    val taskId = getTaskId()
    projectsApi.updateTaskStage(taskId, stage)
    respond(HttpStatusCode.NoContent)
}

suspend fun ApplicationCall.handleDeleteTaskAssignee() {
    val projectId = getProjectId()
    val taskId = getTaskId()
    projectsApi.removeTaskAssignment(taskId)

    respondUpdatedTaskList(projectId)
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

    respondUpdatedTaskList(projectId)
}

suspend fun ApplicationCall.handleEditTaskRequirements() {
    val projectId = getProjectId()
    val (id, needed, done) = getEditCountableTaskRequirements()
    projectsApi.editTaskRequirements(id, needed, done)

    respondUpdatedTaskList(projectId)
}

private suspend fun ApplicationCall.respondUpdatedTaskList(projectId: Int) {
    hxTarget("#task-board")
    hxSwap("outerHTML")

    val worldId = getWorldId()
    val filters = createTaskFilters(getCurrentUrl())

    val project = projectsApi.getProject(projectId, includeTasks = true) ?: throw NotFoundException("Could not find project $projectId")
    val users = permissionsApi.getUsersInWorld(worldId)
    val currentUser = getUser()

    val sortedFilteredProject = filterAndSortProject(currentUser.id, project, filters)

    respondHtml(createTaskBoard(sortedFilteredProject, users, currentUser))
}