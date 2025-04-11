package app.mcorg.presentation.router

import app.mcorg.presentation.handler.handleDeleteUser
import app.mcorg.presentation.handler.handleGetProfile
import app.mcorg.presentation.handler.handleIsTechnical
import app.mcorg.presentation.handler.handleUploadProfilePhoto
import io.ktor.server.routing.*

fun Route.profileRouter() {
    get {
        call.handleGetProfile()
    }
    patch("/photo") {
        call.handleUploadProfilePhoto()
    }
    patch("/is-technical-player") {
        call.handleIsTechnical()
    }
    delete("/user") {
        call.handleDeleteUser()
    }
}