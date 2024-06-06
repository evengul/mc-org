package app.mcorg.presentation.htmx.routing

import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import io.ktor.server.application.*
import io.ktor.server.routing.*
import app.mcorg.presentation.htmx.handlers.*
import app.mcorg.presentation.htmx.templates.pages.auth.registerPage

fun Application.authRouting() {
    routing {
        getAuthed("/profile", PermissionLevel.AUTHENTICATED, Authority.OWNER) {
            call.respondProfile()
        }
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