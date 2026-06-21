package app.mcorg.presentation.router

import app.mcorg.pipeline.auth.handleDeleteAccount
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.BannedPlugin
import app.mcorg.presentation.plugins.DemoUserPlugin
import app.mcorg.webhook.webhookAdminRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(AuthPlugin)
        install(DemoUserPlugin)
        get {
            call.handleGetLanding()
        }
        route("/test") {
            get("/ping") {
                call.respond(HttpStatusCode.OK, "OK")
            }
        }
        route("/account") {
            delete {
                call.handleDeleteAccount()
            }
        }
        route("/auth") {
            authRouter()
        }
        // Machine-facing webhook admin surface — shared-secret gated, JWT-exempt (see AuthPlugin).
        webhookAdminRoutes()
        route("") {
            install(BannedPlugin)
            appRouterV2()
        }
    }
}
