package app.mcorg.config

import app.mcorg.logging.describeWithoutMessages
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Refuses to start against a database whose schema is not the one this build was compiled for
 * (MCO-551).
 *
 * Migrations run out of band: `mvn flyway:migrate` in the deploy workflow ahead of `flyctl deploy`,
 * `migrate-worktree.sh` / `migrate-locally.sh` by hand. Nothing checked that the step had
 * happened, so a worktree whose provisioning aborted before its migration (MCO-510), or a deploy
 * that ran ahead of its migration, booted cleanly and failed later — one 42P01 at a time, on
 * whichever query first touched the new column. This turns that into a startup failure that
 * names the migration.
 *
 * `validate` only, never `migrate`: it reads `flyway_schema_history` and writes nothing, which is
 * also what makes it safe over Neon's pooler. It fails when a migration on the classpath has not
 * been applied (the stale-schema case), when an applied migration is missing from the classpath
 * (an older build against a newer database — a rollback that would otherwise pass silently), and
 * when a checksum differs (someone edited an applied migration).
 */
object SchemaValidation {
    private val logger = LoggerFactory.getLogger(SchemaValidation::class.java)

    /** Every way the database at [url] disagrees with this build's migrations; empty when they agree. */
    fun problems(url: String, user: String, password: String): List<String> {
        val result = Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .load()
            .validateWithResult()
        if (result.validationSuccessful) return emptyList()

        val perMigration = result.invalidMigrations.orEmpty().map { migration ->
            val name = listOfNotNull(migration.version, migration.description).joinToString(" ")
            "$name: ${migration.errorDetails?.errorMessage ?: "invalid"}"
        }
        return perMigration.ifEmpty { listOf(result.errorDetails?.errorMessage ?: "validation failed") }
    }

    fun validateOrExit() {
        val config = Database.Config.get()
        val problems = try {
            problems(config.url, config.user, config.password)
        } catch (e: Exception) {
            // Types and code locations, no messages: a driver's connection error names the host and
            // can carry the URL it was given (documentation/logging.md). The class name alone was
            // not enough — MCO-560 spent a day on a log line that said `NullPointerException` and
            // nothing else, when the one frame it withheld named the cause outright.
            logger.error("Could not validate the database schema; refusing to start.\n{}", e.describeWithoutMessages())
            exitProcess(1)
        }
        if (problems.isEmpty()) {
            logger.info("Database schema matches this build's migrations.")
            return
        }
        logger.error(
            "Database schema does not match this build's migrations:\n{}\n" +
                "Apply the migration first (migrate-worktree.sh in a worktree, migrate-locally.sh on the main " +
                "checkout, `mvn flyway:migrate` in CI), then start again.",
            problems.joinToString("\n") { "  - $it" },
        )
        exitProcess(1)
    }
}
