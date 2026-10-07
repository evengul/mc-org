package app.mcorg.presentation.router

import app.mcorg.pipeline.auth.handleGetSignIn
import app.mcorg.pipeline.auth.handleDemoSignIn
import app.mcorg.pipeline.auth.handleSignIn
import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.SeamRateLimit
import app.mcorg.presentation.plugins.rateLimited
import io.ktor.server.routing.*

fun Route.authRouter() = rateLimited(SeamRateLimit.SIGN_IN) {
    get("/sign-in") {
        call.handleGetSignIn()
    }
    get("/sign-out") {
        call.handleGetSignOut()
    }
    get("/oidc/microsoft-redirect") {
        call.handleSignIn()
    }
    get("/oidc/demo-redirect") {
        call.handleDemoSignIn()
    }
}