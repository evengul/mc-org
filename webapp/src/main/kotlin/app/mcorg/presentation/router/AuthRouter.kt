package app.mcorg.presentation.router

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
    get("/oidc/local-redirect") {
        call.handleLocalSignIn()
    }
    get("/oidc/test-redirect") {
        call.handleTestSignIn()
    }
}