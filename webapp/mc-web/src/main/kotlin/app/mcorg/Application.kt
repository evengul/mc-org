package app.mcorg

import app.mcorg.config.AppConfig
import app.mcorg.event.configureEvents
import app.mcorg.pipeline.minecraftfiles.configureUnmappedItemWarning
import app.mcorg.webhook.configureWebhooks
import app.mcorg.presentation.plugins.configureHTTP
import app.mcorg.presentation.plugins.configureMonitoring
import app.mcorg.presentation.plugins.configurePreviewGate
import app.mcorg.presentation.plugins.configureSessions
import app.mcorg.presentation.plugins.configureStatusStaticRouter
import app.mcorg.presentation.router.configureAppRouter
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    // First, before anything binds a port or opens a pool: a bad configuration must fail here
    // rather than surface later as a runtime mystery (MCO-332).
    AppConfig.initOrExit()
    defaultServer { module() }.start(wait = true)
}

private fun defaultServer(module: Application.() -> Unit) =
    embeddedServer(
        Netty,
        environment = applicationEnvironment { },
        configure = {
            connector {
                // PORT, defaulting to 8080 (MCO-476). Set per worktree so several dev servers can
                // run at once; unset everywhere else, which is what Docker and Fly expect.
                port = AppConfig.port
            }
        },
        module
    )

private fun Application.module() {
    configureEvents()
    configureWebhooks()
    configurePreviewGate()
    configureHTTP()
    configureMonitoring()
    configureAppRouter()
    configureStatusStaticRouter()
    configureSessions()
    configureUnmappedItemWarning()
}
