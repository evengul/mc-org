package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Priority
import no.mcorg.presentation.configuration.projectsApi

suspend fun ApplicationCall.handleCreateTask() {
    val worldId = parameters["worldId"]?.toIntOrNull()
    val teamId = parameters["teamId"]?.toIntOrNull()
    val projectId = parameters["projectId"]?.toIntOrNull()

    val parts = receiveMultipart().readAllParts()
    val taskName = (parts.find { it.name == "task-name" } as PartData.FormItem?)?.value
    val priority = (parts.find { it.name == "task-priority" } as PartData.FormItem?)?.value?.toPriority()

    val needed = (parts.find { it.name == "task-needed" } as PartData.FormItem?)?.value?.toInt()

    if (worldId == null || teamId == null || projectId == null || taskName == null || priority == null) {
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

private fun String.toPriority(): Priority {
    when(this) {
        "NONE" -> return Priority.NONE
        "LOW" -> return Priority.LOW
        "MEDIUM" -> return Priority.MEDIUM
        "HIGH" -> return Priority.HIGH
    }

    throw IllegalArgumentException("Unknown priority: $this")
}