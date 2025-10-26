package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.minecraft.serverFilesPipeline
import io.ktor.server.application.Application
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Configures scheduled tasks for the application.
 * Currently schedules:
 * - Server files sync: Daily at 2 AM UTC (low-traffic time)
 */
fun Application.configureScheduling() {
    val logger = LoggerFactory.getLogger("SchedulingPlugin")

    // Launch scheduled tasks in the application's coroutine scope
    // This ensures proper lifecycle management - tasks are cancelled when the app stops
    monitor.subscribe(io.ktor.server.application.ApplicationStarted) {
        launch {
            scheduleServerFilesSync(logger)
        }
    }

    logger.info("Scheduling plugin configured successfully")
}

/**
 * Schedules the server files synchronization task to run weekly on Mondays at 2 AM CET.
 *
 * Improvements over simple delay loop:
 * - Runs at a specific time (Every Monday at 2 AM CET)
 * - Catches and logs exceptions without crashing the scheduler
 * - Uses structured concurrency for proper cancellation
 * - Includes execution time tracking for monitoring
 * - Stops after 3 consecutive failures to prevent infinite retry loops
 */
private suspend fun CoroutineScope.scheduleServerFilesSync(logger: org.slf4j.Logger) {
    val targetTime = LocalTime.of(2, 0) // 2 AM
    val zoneId = ZoneId.of("CET") // Central European Time
    val maxConsecutiveFailures = 3
    var consecutiveFailures = 0

    logger.info("Server files sync scheduled for Mondays at $targetTime $zoneId")

    // Run immediately on startup
    logger.info("Executing initial server files sync on application startup...")
    try {
        val startTime = System.currentTimeMillis()
        when (val result = serverFilesPipeline.execute(Unit)) {
            is Result.Success -> {
                val duration = System.currentTimeMillis() - startTime
                logger.info("Initial server files sync completed successfully. (${duration}ms)")
            }
            is Result.Failure -> {
                val duration = System.currentTimeMillis() - startTime
                logger.error("Initial server files sync failed: ${result.error} (${duration}ms)")
                consecutiveFailures++
            }
        }
    } catch (e: Exception) {
        logger.error("Unexpected error during initial server files sync", e)
        consecutiveFailures++
    }

    // Continue with scheduled runs
    while (isActive) {
        try {
            // Calculate delay until next run
            val now = LocalDateTime.now(zoneId)
            val nextRun = calculateNextRun(now, targetTime)
            val delayMillis = ChronoUnit.MILLIS.between(now, nextRun)

            logger.info("Next server files sync scheduled for: $nextRun (in ${Duration.ofMillis(delayMillis).toHours()} hours)")

            // Wait until the scheduled time
            delay(delayMillis)

            // Execute the task
            val startTime = System.currentTimeMillis()
            logger.info("Starting scheduled server files sync...")

            when (val result = serverFilesPipeline.execute(Unit)) {
                is Result.Success -> {
                    val duration = System.currentTimeMillis() - startTime
                    logger.info("Successfully retrieved and stored server files for all Minecraft versions. (${duration}ms)")
                    consecutiveFailures = 0 // Reset failure counter on success
                }
                is Result.Failure -> {
                    val duration = System.currentTimeMillis() - startTime
                    logger.error("Failed to retrieve server files: ${result.error} (${duration}ms)")
                    consecutiveFailures++

                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        logger.error("Server files sync has failed $consecutiveFailures consecutive times. Stopping scheduler to prevent resource waste.")
                        break // Exit the loop
                    }
                }
            }

        } catch (e: CancellationException) {
            // Graceful shutdown - don't log as error
            logger.info("Server files sync scheduler stopped")
            throw e // Re-throw to properly cancel the coroutine
        } catch (e: Exception) {
            // Log unexpected errors and increment failure counter
            logger.error("Unexpected error in server files sync scheduler", e)
            consecutiveFailures++

            if (consecutiveFailures >= maxConsecutiveFailures) {
                logger.error("Server files sync has failed $consecutiveFailures consecutive times with exceptions. Stopping scheduler.")
                break // Exit the loop
            }

            // Wait 1 hour before retrying after an error
            logger.info("Waiting 1 hour before retry attempt (failure $consecutiveFailures/$maxConsecutiveFailures)")
            delay(Duration.ofHours(1).toMillis())
        }
    }

    logger.warn("Server files sync scheduler has stopped")
}

/**
 * Calculates the next run time for a weekly scheduled task on Mondays.
 * If it's Monday and the target time hasn't passed, schedules for today.
 * Otherwise, schedules for next Monday.
 */
private fun calculateNextRun(now: LocalDateTime, targetTime: LocalTime): LocalDateTime {
    val today = now.toLocalDate()
    val currentDayOfWeek = today.dayOfWeek.value // Monday = 1, Sunday = 7

    // If it's Monday and the target time hasn't passed yet
    if (currentDayOfWeek == 1 && now.toLocalTime().isBefore(targetTime)) {
        return today.atTime(targetTime)
    }

    // Calculate days until next Monday
    val daysUntilMonday = if (currentDayOfWeek == 1) {
        7 // If it's Monday but time has passed, schedule for next Monday
    } else {
        (8 - currentDayOfWeek) % 7 // Days remaining until next Monday
    }

    return today.plusDays(daysUntilMonday.toLong()).atTime(targetTime)
}
