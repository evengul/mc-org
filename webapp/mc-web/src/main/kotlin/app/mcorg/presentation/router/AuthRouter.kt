package app.mcorg.presentation.router

import app.mcorg.pipeline.auth.handleGetSignIn
import app.mcorg.pipeline.auth.handleDemoSignIn
import app.mcorg.pipeline.auth.handleSignIn
import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.SeamRateLimit
import app.mcorg.presentation.plugins.rateLimited
import io.ktor.server.routing.*

fun Route.authRouter() {
    // Not limited: a JWT check and a page, and AuthPlugin sends every signed-out request here.
    get("/sign-in") {
        call.handleGetSignIn()
    }
    get("/sign-out") {
        call.handleGetSignOut()
    }
    // The callbacks are where an anonymous caller costs something (MCO-274).
    rateLimited(SeamRateLimit.SIGN_IN) {
        get("/oidc/microsoft-redirect") {
            call.handleSignIn()
        }
        get("/oidc/demo-redirect") {
            call.handleDemoSignIn()
        }
    }
}