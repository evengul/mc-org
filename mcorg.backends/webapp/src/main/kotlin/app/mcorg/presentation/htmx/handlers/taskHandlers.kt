package app.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import app.mcorg.domain.Priority
import app.mcorg.domain.tasksFromMaterialList
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.htmx.routing.respondHtml
import app.mcorg.presentation.htmx.templates.pages.project.completeTaskButton
import app.mcorg.presentation.htmx.templates.pages.project.countableTaskListElement
import app.mcorg.presentation.htmx.templates.pages.project.incompleteTaskButton

suspend fun ApplicationCall.handleCreateTask(worldId: Int, teamId: Int, projectId: Int) {

    val parts = receiveMultipart().readAllParts()
    val taskName = (parts.find { it.name == "task-name" } as PartData.FormItem?)?.value
    val priority = (parts.find { it.name == "task-priority" } as PartData.FormItem?)?.value?.toPriority()

    val needed = (parts.find { it.name == "task-needed" } as PartData.FormItem?)?.value?.toInt()

    if (taskName == null || priority == null) {
        respond(HttpStatusCode.BadRequest)
    } else {
        if (needed == null) {
            projectsApi().addDoableTask(projectId, taskName, priority)
        } else {
            projectsApi().addCountableTask(projectId, taskName, priority, needed)
        }
        respondRedirect("/worlds/$worldId/teams/$teamId/projects/$projectId")
    }
}

suspend fun ApplicationCall.handleCompleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    projectsApi().completeTask(taskId)

    respondHtml(incompleteTaskButton(worldId, teamId, projectId, taskId))
}

suspend fun ApplicationCall.handleIncompleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    projectsApi().undoCompleteTask(taskId)

    respondHtml(completeTaskButton(worldId, teamId, projectId, taskId))
}

suspend fun ApplicationCall.handleUpdateCountableTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    val parts = receiveMultipart().readAllParts()
    val needed = (parts.find { it.name == "needed" } as PartData.FormItem?)?.value?.toInt()
    val done = (parts.find { it.name == "done" } as PartData.FormItem?)?.value?.toInt()

    if (needed == null || done == null || needed < done) {
        respond(HttpStatusCode.BadRequest)
    } else {
        projectsApi().updateCountableTask(taskId, needed, done)

        val task = projectsApi().getTask(projectId, taskId)

        if (task == null) {
            respond(HttpStatusCode.NotFound)
        } else {
            respondHtml(countableTaskListElement(worldId, teamId, projectId, task))
        }
    }
}

suspend fun ApplicationCall.handleUploadMaterialList(worldId: Int, teamId: Int, projectId: Int) {
    val parts = receiveMultipart().readAllParts()
    val file = parts.find { it.name == "file" } as PartData.FileItem?
    val tasks = file?.streamProvider?.let {
        it().tasksFromMaterialList()
    }

    if (!tasks.isNullOrEmpty()) {
        tasks.forEach {
            projectsApi().addCountableTask(
                projectId = projectId,
                name = it.name,
                priority = Priority.NONE,
                needed = it.needed
            )
        }
    }

    respondRedirect("/worlds/$worldId/teams/$teamId/projects/$projectId")
}

private fun String.toPriority(): Priority {
    when(this) {
        "NONE" -> return Priority.NONE
        "LOW" -> return Priority.LOW
        "MEDIUM" -> return Priority.MEDIUM
        "HIGH" -> return Priority.HIGH
    }

    throw IllegalArgumentException("Unknown priority: $this")
}