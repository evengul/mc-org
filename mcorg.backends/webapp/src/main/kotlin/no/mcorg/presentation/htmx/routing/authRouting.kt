package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.*

fun Application.authRouting() {
    routing {
        get("/signin") {
            call.handleGetSignin()
        }
        get("/register") {
            call.handleGetRegister()
        }
        post("/signin") {
            call.handlePostSignin()
        }
        post("/register") {
            call.handlePostRegister()
        }
        get("/signout") {
            call.handleGetSignout()
        }
    }
}