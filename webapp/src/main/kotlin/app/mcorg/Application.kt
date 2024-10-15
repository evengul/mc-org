package app.mcorg

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import app.mcorg.presentation.plugins.*
import app.mcorg.presentation.configureAppRouter
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Application::class.java)

fun main() {
    migrate()

    logger.info("Starting server")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module, watchPaths = getWatchPaths())
            .start(wait = true)
}

private fun migrate() {
    logger.info("Starting DB migration")
    val url = System.getenv("DB_URL")
    val user = System.getenv("DB_USER")
    val password = System.getenv("DB_PASSWORD")
    val finalUrl = if (url.contains("?")) "$url&user=$user&password=$password" else "$url?user=$user&password=$password"
    val pgSource = PGSimpleDataSource().apply { setURL(finalUrl) }
    Flyway.configure()
        .dataSource(pgSource)
        .locations("db/migration")
        .load()
        .migrate()
    logger.info("DB Migration complete")
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
