package app.mcorg.presentation.router

import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import io.ktor.server.routing.*

fun Route.worldRouter() {
    get {
        call.handleGetWorlds()
    }
    post {
        call.handlePostWorld()
    }
    patch("/select") {
        call.handleSelectWorld()
    }
    route("/{worldId}") {
        install(WorldParamPlugin)
        install(WorldParticipantPlugin)
        get {
            call.handleGetWorld()
        }
        delete {
            call.handleDeleteWorld()
        }
        route("/users") {
            userRouter()
        }
        route("/projects") {
            projectRouter()
        }
    }
}