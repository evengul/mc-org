package app.mcorg.presentation.router

import app.mcorg.api.apiV1Routes
import app.mcorg.pipeline.auth.handleDeleteAccount
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.handler.handleReadinessProbe
import app.mcorg.presentation.handler.link.handleApproveLinkPage
import app.mcorg.presentation.handler.link.handleGetLinkPage
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.BannedPlugin
import app.mcorg.presentation.plugins.DemoUserPlugin
import app.mcorg.presentation.plugins.MachineEndpointAuthPlugin
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
        // Liveness and readiness, deliberately separate (MCO-349).
        //
        // The obvious design — one endpoint that probes the database, checked by Fly every 30s —
        // would cost real money here. Neon suspends the compute after 300s idle, and master
        // currently runs ~530s active per day against ~86400s of wall clock. Any periodic check
        // that touches the database pins it awake permanently, which is the shape that inflated
        // this project's bill once already.
        //
        // So: /ping answers "is this JVM serving HTTP" and is what Fly polls. /ready answers "can
        // this JVM reach its dependencies" and is called on demand — deploy smoke tests now, and
        // the ingestion alert in MCO-344 when that lands.
        //
        // /ready carries the machine-endpoint secret; /ping deliberately does not. The asymmetry
        // is the whole point of splitting them. /ping is a constant and is public because Fly's
        // health checker cannot present a credential. /ready runs `SELECT 1` on the production
        // pool, so leaving it open would hand any stranger the exact cost failure this comment
        // describes avoiding — one curl loop pins Neon awake 24/7 — plus a free pool-exhaustion
        // lever and unbounded ERROR log volume during an outage. Both of its intended callers are
        // machines that can send a header.
        route("/test") {
            get("/ping") {
                call.respond(HttpStatusCode.OK, "OK")
            }
            route("/ready") {
                install(MachineEndpointAuthPlugin)
                get {
                    call.handleReadinessProbe()
                }
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
