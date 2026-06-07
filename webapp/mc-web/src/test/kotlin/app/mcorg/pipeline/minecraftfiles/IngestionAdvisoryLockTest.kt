package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.Database
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Proves the ingestion advisory lock serialises overlapping runs (MCO-169). Each call opens its own
 * session, so the lock genuinely contends across connections.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class IngestionAdvisoryLockTest {

    /** Must match INGESTION_ADVISORY_LOCK_KEY in GetServerFilesPipeline.kt. */
    private val key = 7331L

    @Test
    fun `runs the block and releases the lock when it is free`() {
        var count = 0
        val first = runBlocking { withIngestionLock { count++; Result.success() } }
        assertIs<Result.Success<*>>(first)
        assertEquals(1, count)

        // The lock was released, so a second run executes too.
        val second = runBlocking { withIngestionLock { count++; Result.success() } }
        assertIs<Result.Success<*>>(second)
        assertEquals(2, count)
    }

    @Test
    fun `skips the block and returns success when another session holds the lock`() {
        holdingLock {
            var ran = false
            val result = runBlocking { withIngestionLock { ran = true; Result.success() } }
            assertIs<Result.Success<*>>(result)
            assertFalse(ran, "block must not run while the lock is held by another session")
        }
    }

    @Test
    fun `releases the lock even when the block fails`() {
        val failed = runBlocking { withIngestionLock { Result.failure(AppFailure.ApiError.UnknownError) } }
        assertIs<Result.Failure<*>>(failed)

        // If the lock had leaked, this would be skipped; instead it must run.
        var ran = false
        val next = runBlocking { withIngestionLock { ran = true; Result.success() } }
        assertIs<Result.Success<*>>(next)
        assertTrue(ran, "lock must be freed on the failure path")
    }

    @Test
    fun `holds the lock for the duration of the block`() {
        var unavailableToOthersDuringBlock: Boolean? = null
        val result = runBlocking {
            withIngestionLock {
                unavailableToOthersDuringBlock = !tryLockOnNewSession()
                Result.success()
            }
        }
        assertIs<Result.Success<*>>(result)
        assertEquals(true, unavailableToOthersDuringBlock, "other sessions must not acquire the lock during the block")
        // And it is available again afterwards.
        assertTrue(tryLockOnNewSession(), "lock should be free once the block has finished")
    }

    /** Opens a fresh session, tries the lock, and closes (releasing it if acquired). */
    private fun tryLockOnNewSession(): Boolean =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, key)
                stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }

    /** Holds the lock on a dedicated session for the duration of [block], then releases it. */
    private fun holdingLock(block: () -> Unit) {
        val conn = Database.getConnection()
        try {
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, key)
                stmt.executeQuery().use { rs -> assertTrue(rs.next() && rs.getBoolean(1), "precondition: should acquire the lock") }
            }
            block()
        } finally {
            conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                stmt.setLong(1, key)
                stmt.execute()
            }
            conn.close()
        }
    }
}
