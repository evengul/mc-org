package app.mcorg

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import app.mcorg.presentation.plugins.*
import app.mcorg.presentation.configureAppRouter

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module, watchPaths = getWatchPaths())
            .start(wait = true)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureAppRouter()
    configureStatusStaticRouter()
}

private fun getWatchPaths(): List<String> {
    if (System.getenv("ENV") == "LOCAL") {
        return listOf("classes", "resources")
    }
    return emptyList()
}
