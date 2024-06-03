package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.auth.registerPage

fun Application.authRouting() {
    routing {
        get("/signin") {
            call.respondSignIn()
        }
        get("/register") {
            call.respondHtml(registerPage())
        }
        post("/signin") {
            call.handlePostSignin()
        }
        post("/register") {
            call.handlePostRegister()
        }
        get("/signout") {
            call.respondSignOut()
        }
    }
}