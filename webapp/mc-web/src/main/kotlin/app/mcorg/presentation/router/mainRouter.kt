package app.mcorg.presentation.router

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.pipeline.auth.handleDeleteAccount
import app.mcorg.presentation.handler.handleGetLanding
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.BannedPlugin
import app.mcorg.presentation.plugins.DemoUserPlugin
import app.mcorg.presentation.templated.testpage.createTestPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
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
            get("/page") {
                if (AppConfig.env == Production) call.respond(HttpStatusCode.Forbidden)
                else call.respondHtml(createTestPage())
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
        route("") {
            install(BannedPlugin)
            appRouterV2()
        }
        route("/app") {
            get("{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val query = call.request.queryString()
                val redirect = "/$path${if (query.isNotEmpty()) "?$query" else ""}"
                call.respondRedirect(redirect, permanent = true)
            }
            get {
                call.respondRedirect("/worlds", permanent = true)
            }
        }
    }
}
