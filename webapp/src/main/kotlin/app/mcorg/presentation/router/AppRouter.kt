package app.mcorg.presentation.router

import io.ktor.server.routing.*

fun Route.appRouter() {
    route("/profile") {
        profileRouter()
    }
    route("/worlds") {
        worldRouter()
    }
}