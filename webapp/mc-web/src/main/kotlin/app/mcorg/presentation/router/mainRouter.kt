package app.mcorg.presentation.router

import app.mcorg.api.apiV1Routes
import app.mcorg.pipeline.auth.handleDeleteAccount
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.handler.link.handleApproveLinkPage
import app.mcorg.presentation.handler.link.handleGetLinkPage
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
        // Mod-facing JSON API (MCO-235/236) — bearer-gated, JWT-exempt (see AuthPlugin allowlist).
        apiV1Routes()
        // Device-link approval page — JWT-authed (normal app auth), HTML.
        route("/link") {
            get { call.handleGetLinkPage() }
            post { call.handleApproveLinkPage() }
        }
        route("") {
            install(BannedPlugin)
            appRouterV2()
        }
    }
}
