package app.mcorg.cli

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.executeServerFilesPipeline
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("app.mcorg.cli.IngestServerFiles")

/**
 * Standalone entry point that runs server-files ingestion once and exits — no Ktor server, no
 * routing, no listeners (MCO-170). This is the process the scheduled Fly machine in MCO-171 will
 * invoke. It shares the `mc-web` classpath, so it reuses the same [Database] pool,
 * [app.mcorg.config.AppConfig] env loading, the Mojang HTTP provider, and the
 * advisory-lock-guarded [executeServerFilesPipeline].
 */
/**
 * Hard ceiling on one ingestion run (MCO-346).
 *
 * A full re-ingest of every version takes minutes, so this is deliberately loose; it exists to
 * bound the pathological case, not to police the normal one.
 */
private val WALL_CLOCK = 45.minutes

/** Exit code for a run killed by the watchdog, distinct from `1` (failed) and `2` (threw). */
private const val EXIT_WALL_CLOCK_EXCEEDED = 3

fun main() {
    // Same gate as the web app (MCO-332): a nightly ingestion pointed at a half-configured
    // database should fail loudly here, not write somewhere unexpected.
    AppConfig.initOrExit()
    startWallClockWatchdog()
    val exitCode = runBlocking {
        runIngestion(
            pipeline = { executeServerFilesPipeline() },
            shutdown = { Database.shutdown() },
        )
    }
    exitProcess(exitCode)
}

/**
 * Kills the process if a run outlives [WALL_CLOCK].
 *
 * The Fly machine is created with `--restart no` and no timeout, so before this the only thing
 * bounding a run was the run itself. The per-download timeouts in `GetServerFileStep` cover the
 * known hang, but they cannot cover a hang somewhere nobody has thought of yet, and the failure
 * mode is the worst kind: the machine simply never exits, holds the ingestion advisory lock, and
 * every subsequent nightly run logs "another ingestion run is in progress" and reports success.
 *
 * Uses `halt` rather than `exitProcess` because the thing being escaped may well be a shutdown
 * hook or a blocked pool thread.
 *
 * **This kills the process; it does not reliably free the lock.** An earlier version of this
 * comment claimed it did, reasoning that Postgres drops session-scoped advisory locks when the
 * connection drops. That holds on a *direct* connection — which is what the deployed
 * `ingest-scheduler` uses, so the nightly run is fine. It does not hold through a transaction
 * pooler: the lock lives on a server connection in PgBouncer's pool, and killing the client leaves
 * that server connection to be returned to the pool with its session state intact. Per
 * documentation/configuration.md the manual `ingest-machine.sh --once` smoke test goes through the
 * pooler, so a watchdog kill *there* can strand lock 7331 on a backend nothing will come back for.
 *
 * That is the case `withIngestionLock` now detects rather than skipping past: a lock held longer
 * than a healthy run can possibly last is logged as stuck, with the pid to
 * `pg_terminate_backend`, and fails the run so the exit code shows it. Recovering it is still a
 * manual step — see MCO-348, which is where closing the direct/pooled asymmetry belongs.
 */
private fun startWallClockWatchdog() {
    val watchdog = Thread {
        try {
            Thread.sleep(WALL_CLOCK.inWholeMilliseconds)
        } catch (_: InterruptedException) {
            return@Thread
        }
        logger.error("Ingestion exceeded its {} wall clock; killing the process", WALL_CLOCK)
        Runtime.getRuntime().halt(EXIT_WALL_CLOCK_EXCEEDED)
    }
    watchdog.isDaemon = true
    watchdog.name = "ingestion-wall-clock"
    watchdog.start()
}

/**
 * Runs [pipeline] and maps its outcome to a process exit code, always calling [shutdown] afterwards
 * (so the Hikari pool is released and the JVM can exit promptly instead of lingering on idle pool
 * threads). Extracted from [main] so the exit-code mapping is unit-testable without terminating the
 * JVM via `exitProcess`.
 *
 * Exit codes:
 * - `0` — success, including the no-op case where another run legitimately holds the advisory lock.
 * - `1` — the pipeline returned a [Result.Failure]. Since MCO-326's review this also covers a lock
 *   that looks *stuck* rather than busy, which previously exited 0 and so was indistinguishable
 *   from a healthy skip for as long as it lasted.
 * - `2` — an unexpected throwable escaped the pipeline.
 * - `3` — killed by the wall-clock watchdog.
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
