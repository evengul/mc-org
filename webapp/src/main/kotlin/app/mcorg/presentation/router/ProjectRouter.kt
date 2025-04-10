package app.mcorg.presentation.router

import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.ProjectParamPlugin
import io.ktor.server.routing.*

fun Route.projectRouter() {
    get {
        call.handleGetProjects()
    }
    post {
        call.handlePostProject()
    }
    route("/{projectId}") {
        install(ProjectParamPlugin)
        get {
            call.handleGetProject()
        }
        delete {
            call.handleDeleteProject()
        }
        patch("/assign") {
            call.handlePatchProjectAssignee()
        }
        route("/tasks") {
            taskRouter()
        }
    }
}