package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class IngestionLedgerStepsTest {

    private val v1 = MinecraftVersion.Release(1, 21, 4)
    private val v2 = MinecraftVersion.Release(1, 20, 6)
    private val v3 = MinecraftVersion.Release(1, 19, 2)

    @BeforeEach
    fun clearLedger() {
        DatabaseTestExtension.executeSQL("TRUNCATE TABLE minecraft_version_ingestion")
    }

    // --- LoadIngestionStatusStep ---------------------------------------------------------------

    @Test
    fun `LoadIngestionStatusStep returns a status per ledger row and omits absent versions`() {
        seed(v1, IngestionStatus.COMPLETED)
        seed(v2, IngestionStatus.FAILED)

        val statuses = runBlocking { LoadIngestionStatusStep.process(Unit) }
        val map = assertIs<Result.Success<Map<MinecraftVersion.Release, String>>>(statuses).value

        assertEquals(IngestionStatus.COMPLETED, map[v1])
        assertEquals(IngestionStatus.FAILED, map[v2])
        assertNull(map[v3]) // no row → absent from map
    }

    // --- MarkIngestionInProgressStep -----------------------------------------------------------

    @Test
    fun `MarkIngestionInProgress inserts a fresh in_progress row for a version with no data`() {
        val result = runBlocking { MarkIngestionInProgressStep.process(v1) }
        assertIs<Result.Success<*>>(result)

        val row = assertNotNull(readRow(v1))
        assertEquals(IngestionStatus.IN_PROGRESS, row.status)
        assertEquals(1, row.attemptCount)
        assertTrue(row.hasStarted)
        assertNull(row.lastError)
    }

    @Test
    fun `MarkIngestionInProgress on an existing row bumps attempt_count and clears last_error`() {
        seed(v1, IngestionStatus.FAILED, attemptCount = 2, lastError = "boom")

        val result = runBlocking { MarkIngestionInProgressStep.process(v1) }
        assertIs<Result.Success<*>>(result)

        val row = assertNotNull(readRow(v1))
        assertEquals(IngestionStatus.IN_PROGRESS, row.status)
        assertEquals(3, row.attemptCount)
        assertNull(row.lastError)
    }

    // --- MarkIngestionCompletedStep ------------------------------------------------------------

    @Test
    fun `MarkIngestionCompleted sets completed, stamps completed_at and clears last_error`() {
        seed(v1, IngestionStatus.IN_PROGRESS, attemptCount = 1, lastError = "stale")

        val result = runBlocking { MarkIngestionCompletedStep.process(v1) }
        assertIs<Result.Success<*>>(result)

        val row = assertNotNull(readRow(v1))
        assertEquals(IngestionStatus.COMPLETED, row.status)
        assertTrue(row.hasCompleted)
        assertNull(row.lastError)
    }

    // --- MarkIngestionFailedStep ---------------------------------------------------------------

    @Test
    fun `MarkIngestionFailed records the error and leaves completed_at null`() {
        seed(v1, IngestionStatus.IN_PROGRESS)

        val result = runBlocking { MarkIngestionFailedStep.process(v1 to "download timed out") }
        assertIs<Result.Success<*>>(result)

        val row = assertNotNull(readRow(v1))
        assertEquals(IngestionStatus.FAILED, row.status)
        assertEquals("download timed out", row.lastError)
        assertEquals(false, row.hasCompleted)
    }

    // --- FilterAlreadyStoredVersionsStep -------------------------------------------------------

    @Test
    fun `Filter keeps versions that are missing, failed or in_progress and drops completed`() {
        seed(v1, IngestionStatus.COMPLETED)
        seed(v2, IngestionStatus.FAILED)
        // v3 intentionally has no ledger row (the truncate-to-recreate path)

        val input = listOf(
            v1 to URI.create("https://example.test/v1/server.jar"),
            v2 to URI.create("https://example.test/v2/server.jar"),
            v3 to URI.create("https://example.test/v3/server.jar"),
        )

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(setOf(v2, v3), kept.map { it.first }.toSet())
    }

    @Test
    fun `Filter keeps everything when the ledger is empty`() {
        val input = listOf(
            v1 to URI.create("https://example.test/v1/server.jar"),
            v2 to URI.create("https://example.test/v2/server.jar"),
        )

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(setOf(v1, v2), kept.map { it.first }.toSet())
    }

    // --- helpers --------------------------------------------------------------------------------

    private data class LedgerRow(
        val status: String,
        val attemptCount: Int,
        val lastError: String?,
        val hasStarted: Boolean,
        val hasCompleted: Boolean,
    )

    private fun seed(
        version: MinecraftVersion.Release,
        status: String,
        attemptCount: Int = 0,
        lastError: String? = null,
    ) = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_version_ingestion (version, status, attempt_count, last_error) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version.toString())
                stmt.setString(2, status)
                stmt.setInt(3, attemptCount)
                stmt.setString(4, lastError)
            }
        ).process(Unit)
        assertIs<Result.Success<*>>(result)
    }

    private fun readRow(version: MinecraftVersion.Release): LedgerRow? = runBlocking {
        DatabaseSteps.query<Unit, LedgerRow?>(
            sql = SafeSQL.select(
                "SELECT status, attempt_count, last_error, started_at, completed_at FROM minecraft_version_ingestion WHERE version = ?"
            ),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs ->
                if (rs.next()) {
                    LedgerRow(
                        status = rs.getString("status"),
                        attemptCount = rs.getInt("attempt_count"),
                        lastError = rs.getString("last_error"),
                        hasStarted = rs.getTimestamp("started_at") != null,
                        hasCompleted = rs.getTimestamp("completed_at") != null,
                    )
                } else null
            }
        ).process(Unit).getOrThrow()
    }
}
