package app.mcorg.presentation.router

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.BannedPlugin
import app.mcorg.presentation.templated.testpage.createTestPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(AuthPlugin)
        get {
            call.handleGetLanding()
        }
        route("/test") {
            get("/ping") {
                call.respond(HttpStatusCode.OK, "OK")
            }
            get("/page") {
                if (AppConfig.env == Production) call.respond(HttpStatusCode.Forbidden)
                else call.respondHtml(createTestPage())
            }
        }
        route("/auth") {
            authRouter()
        }
        route("/app") {
            install(BannedPlugin)
            appRouterV2()
        }
    }
}
