package app.mcorg.utils

import org.flywaydb.core.Flyway
import org.slf4j.Logger

fun migrateDb(logger: Logger) {
    logger.info("Starting DB migration")
    val (url, user, password) = getDatabaseConfig()
    Flyway.configure()
        .dataSource(url, user, password)
        .locations("db/migration")
        .load()
        .migrate()
    logger.info("DB Migration complete")
}

private data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
)

private fun getDatabaseConfig() = DatabaseConfig(
    url = System.getenv("DB_URL"),
    user = System.getenv("DB_USER"),
    password = System.getenv("DB_PASSWORD")
)