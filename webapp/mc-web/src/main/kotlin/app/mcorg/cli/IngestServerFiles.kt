package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.executeServerFilesPipeline
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("app.mcorg.cli.IngestServerFiles")

/**
 * Standalone entry point that runs server-files ingestion once and exits — no Ktor server, no
 * routing, no listeners (MCO-170). This is the process the scheduled Fly machine in MCO-171 will
 * invoke. It shares the `mc-web` classpath, so it reuses the same [Database] pool,
 * [app.mcorg.config.AppConfig] env loading, the Mojang HTTP provider, and the
 * advisory-lock-guarded [executeServerFilesPipeline].
 */
fun main() {
    val exitCode = runBlocking {
        runIngestion(
            pipeline = { executeServerFilesPipeline() },
            shutdown = { Database.shutdown() },
        )
    }
    exitProcess(exitCode)
}

/**
 * Runs [pipeline] and maps its outcome to a process exit code, always calling [shutdown] afterwards
 * (so the Hikari pool is released and the JVM can exit promptly instead of lingering on idle pool
 * threads). Extracted from [main] so the exit-code mapping is unit-testable without terminating the
 * JVM via `exitProcess`.
 *
 * Exit codes:
 * - `0` — success, including the no-op case where another run already holds the advisory lock.
 * - `1` — the pipeline returned a [Result.Failure].
 * - `2` — an unexpected throwable escaped the pipeline.
 */
internal suspend fun runIngestion(
    pipeline: suspend () -> Result<AppFailure, Unit>,
    shutdown: () -> Unit,
): Int {
    return try {
        logger.info("Starting standalone server-files ingestion")
        when (val result = pipeline()) {
            is Result.Success -> {
                logger.info("Server-files ingestion completed successfully")
                0
            }
            is Result.Failure -> {
                logger.error("Server-files ingestion failed: ${result.error}")
                1
            }
        }
    } catch (e: Throwable) {
        logger.error("Unexpected error during server-files ingestion", e)
        2
    } finally {
        shutdown()
    }
}
