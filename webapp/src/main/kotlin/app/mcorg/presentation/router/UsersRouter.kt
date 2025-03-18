package app.mcorg.presentation.router

import app.mcorg.presentation.handler.handleDeleteWorldUser
import app.mcorg.presentation.handler.handleGetUsers
import app.mcorg.presentation.handler.handlePostUser
import app.mcorg.presentation.plugins.WorldAdminPlugin
import io.ktor.server.routing.*

fun Route.userRouter() {
    get {
        call.handleGetUsers()
    }
    delete("/{userId}") {
        install(WorldAdminPlugin)
        call.handleDeleteWorldUser()
    }
    post {
        install(WorldAdminPlugin)
        call.handlePostUser()
    }
}