package app.mcorg.presentation.plugins

import app.mcorg.presentation.templated.error.notFoundPage
import app.mcorg.presentation.templated.error.serverErrorPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*

fun Application.configureStatusStaticRouter() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logError(call, cause)
            call.respondHtml(serverErrorPage(), HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondHtml(notFoundPage(), HttpStatusCode.NotFound)
        }
    }
    routing {
        staticResources("/static", "static")
    }
}
