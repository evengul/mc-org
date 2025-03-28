package app.mcorg.presentation.plugins

import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.worldsApi
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.response.*

val WorldParamPlugin = createRouteScopedPlugin("WorldParamPlugin") {
    onCall {
        val worldParam = it.parameters["worldId"]?.toIntOrNull()
        if (worldParam != null) {
            if (worldsApi.worldExists(worldParam)) {
                it.setWorldId(worldParam)
            } else {
                it.respondNotFound("World not found")
            }
        } else {
            it.respondRedirect("/app/worlds")
        }
    }
}

val ProjectParamPlugin = createRouteScopedPlugin("ProjectParamPlugin") {
    onCall {
        val projectParam = it.parameters["projectId"]?.toIntOrNull()
        if (projectParam != null) {
            if (projectsApi.projectExists(projectParam)) {
                it.setProjectId(projectParam)
            } else {
                it.respondNotFound("Project not found")
            }
        } else {
            val worldId = it.getWorldId()
            it.respondRedirect("/app/worlds/$worldId/projects")
        }
    }
}

val TaskParamPlugin = createRouteScopedPlugin("TaskParamPlugin") {
    onCall {
        val taskParam = it.parameters["taskId"]?.toIntOrNull()
        if (taskParam != null) {
            val project = projectsApi.getProject(it.getProjectId(), includeTasks = true)!!
            if (project.tasks.any { task -> task.id == taskParam }) {
                it.setTaskId(taskParam)
            } else {
                it.respondNotFound("Task not found in project")
            }
        } else {
            val worldId = it.getWorldId()
            val projectId = it.getProjectId()
            it.respondRedirect("/app/worlds/$worldId/projects/$projectId/tasks")
        }
    }
}