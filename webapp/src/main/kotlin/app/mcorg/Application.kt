package app.mcorg

import app.mcorg.pipeline.ApiSteps
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import app.mcorg.presentation.plugins.*
import app.mcorg.presentation.router.configureAppRouter

fun main() {
    // Add shutdown hook for proper resource cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        ApiSteps.cleanup()
    })

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
}
