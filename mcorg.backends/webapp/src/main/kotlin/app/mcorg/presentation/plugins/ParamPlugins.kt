package app.mcorg.presentation.plugins

import app.mcorg.presentation.router.utils.*
import io.ktor.server.application.*
import io.ktor.server.response.*

val WorldParamPlugin = createRouteScopedPlugin("WorldParamPlugin") {
    onCall {
        val worldParam = it.parameters["worldId"]?.toIntOrNull()
        if (worldParam != null) {
            it.setWorldId(worldParam)
        } else {
            it.respondRedirect("/worlds")
        }
    }
}

val ProjectParamPlugin = createRouteScopedPlugin("ProjectParamPlugin") {
    onCall {
        val projectParam = it.parameters["projectId"]?.toIntOrNull()
        if (projectParam != null) {
            it.setProjectId(projectParam)
        } else {
            val worldId = it.getWorldId()
            it.respondRedirect("/worlds/$worldId/projects")
        }
    }
}

val TaskParamPlugin = createRouteScopedPlugin("TaskParamPlugin") {
    onCall {
        val taskParam = it.parameters["taskId"]?.toIntOrNull()
        if (taskParam != null) {
            it.setTaskId(taskParam)
        } else {
            val worldId = it.getWorldId()
            val projectId = it.getProjectId()
            it.respondRedirect("/worlds/$worldId/projects/$projectId/tasks")
        }
    }
}