package app.mcorg

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import app.mcorg.presentation.plugins.*
import app.mcorg.presentation.configureAppRouter
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger(Application::class.java)
    logger.info("Starting DB migration")
    Flyway.configure()
        .dataSource(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        .locations("db/migration")
        .load()
        .migrate()
    logger.info("DB Migration complete")

    logger.info("Starting server")
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
