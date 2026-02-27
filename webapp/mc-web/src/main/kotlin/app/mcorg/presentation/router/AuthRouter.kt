package app.mcorg.presentation.router

import app.mcorg.pipeline.auth.handleGetSignIn
import app.mcorg.pipeline.auth.handleDemoSignIn
import app.mcorg.pipeline.auth.handleSignIn
import app.mcorg.presentation.handler.*
import io.ktor.server.routing.*

fun Route.authRouter() {
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