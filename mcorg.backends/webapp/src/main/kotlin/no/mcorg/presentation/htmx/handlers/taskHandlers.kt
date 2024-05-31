package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.button
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import no.mcorg.domain.Priority
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.hxPut
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.pages.deleteTask
import no.mcorg.presentation.htmx.templates.pages.updateCountableForm

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

    respondHtml(createHTML().button {
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/incomplete")
        hxSwap("outerHTML")
        + "Not complete"
    })
}

suspend fun ApplicationCall.handleIncompleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    projectsApi().undoCompleteTask(taskId)

    respondHtml(createHTML().button {
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/complete")
        hxSwap("outerHTML")
        + "Complete!"
    })
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
            respondHtml(createHTML().li {
                + task.name
                updateCountableForm(worldId, teamId, projectId, task)
                deleteTask(worldId, teamId, projectId, taskId)
            })
        }
    }
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