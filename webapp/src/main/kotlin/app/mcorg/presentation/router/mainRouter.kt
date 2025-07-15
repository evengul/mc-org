package app.mcorg.presentation.router

import app.mcorg.domain.Prod
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.plugins.EnvPlugin
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.templated.testpage.createTestPage
import app.mcorg.presentation.utils.getEnvironment
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(EnvPlugin)
        install(AuthPlugin)
        get {
            call.handleGetLanding()
        }
        route("/test") {
            get("/ping") {
                call.respond(HttpStatusCode.OK, "OK")
            }
            get("/page") {
                if (call.getEnvironment() == Prod) call.respond(HttpStatusCode.Forbidden)
                else call.respondHtml(createTestPage())
            }
        }
        route("/auth") {
            authRouter()
        }
        route("/app") {
            appRouter()
        }
    }
}
