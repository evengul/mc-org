package app.mcorg

import app.mcorg.presentation.plugins.configureHTTP
import app.mcorg.presentation.plugins.configureMonitoring
import app.mcorg.presentation.plugins.configureScheduling
import app.mcorg.presentation.plugins.configureSessions
import app.mcorg.presentation.plugins.configureStatusStaticRouter
import app.mcorg.presentation.router.configureAppRouter
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    defaultServer { module() }.start(wait = true)
}

private fun defaultServer(module: Application.() -> Unit) =
    embeddedServer(
        Netty,
        environment = applicationEnvironment { },
        configure = {
            connector {
                port = 8080
            }
        },
        module
    )

private fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureAppRouter()
    configureStatusStaticRouter()
    configureScheduling()
    configureSessions()
}
