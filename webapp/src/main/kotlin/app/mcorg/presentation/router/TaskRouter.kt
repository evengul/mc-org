package app.mcorg.presentation.router

import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.TaskParamPlugin
import io.ktor.server.routing.*

fun Route.taskRouter() {
    post("/doable") {
        call.handlePostDoableTask()
    }
    post("/countable") {
        call.handlePostCountableTask()
    }
    post("/litematica") {
        call.handlePostLitematicaTasks()
    }
    patch("/requirements") {
        call.handleEditTaskRequirements()
    }
    route("/{taskId}") {
        install(TaskParamPlugin)
        delete {
            call.handleDeleteTask()
        }
        patch("/assign") {
            call.handlePatchTaskAssignee()
        }
        patch("/stage") {
            call.handleUpdateTaskStage()
        }
        patch("/complete") {
            call.handleCompleteTask()
        }
        patch("/incomplete") {
            call.handleIncompleteTask()
        }
        patch("/do-more") {
            call.handlePatchCountableTaskDoneMore()
        }
    }
}