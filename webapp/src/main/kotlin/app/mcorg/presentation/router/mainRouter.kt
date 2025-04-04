package app.mcorg.presentation.router

import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(EnvPlugin)
        install(AuthPlugin)
        get {
            call.handleGetLanding()
        }
        route("/auth") {
            authRouter()
        }
        route("/app") {
            appRouter()
        }
    }
}
