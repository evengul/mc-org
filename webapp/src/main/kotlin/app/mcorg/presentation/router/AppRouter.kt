package app.mcorg.presentation.router

import app.mcorg.presentation.plugins.*
import io.ktor.server.routing.*

fun Route.appRouter() {
    install(AuthPlugin)
    route("/profile") {
        profileRouter()
    }
    route("/worlds") {
        worldRouter()
    }
}