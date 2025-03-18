package app.mcorg.presentation.router

import app.mcorg.presentation.handler.handleDeleteWorldUser
import app.mcorg.presentation.handler.handleGetUsers
import app.mcorg.presentation.handler.handlePostUser
import io.ktor.server.routing.*

fun Route.userRouter() {
    get {
        call.handleGetUsers()
    }
    post {
        call.handlePostUser()
    }
    delete("/{userId}") {
        call.handleDeleteWorldUser()
    }
}