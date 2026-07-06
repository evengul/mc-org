package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.config.CachedItemSourceGraph
import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TestUtils
import app.mcorg.pipeline.failure.AppFailure
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLTimeoutException
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the MCO-252 self-invalidating epoch check: [GetItemSourceGraphForVersionStep] must
 * discard a cached graph once `minecraft_version_ingestion.completed_at` reports a completion
 * strictly after the cache was built, and must otherwise serve the cache without hitting the DB
 * more than the bounded [CacheManager.versionIngestionEpoch] TTL allows. JDBC is mocked (no
 * Testcontainers) following the existing [app.mcorg.pipeline.DatabaseStepsTest] pattern; the
 * real-DB rebuild-on-stale scenario is covered by the `database`-tagged
 * [ItemSourceGraphStepsTest].
 */
class GetItemSourceGraphForVersionStepTest {

    private val mockConnection = mockk<Connection>()
    private val mockStatement = mockk<PreparedStatement>()
    private val mockResultSet = mockk<ResultSet>()
    private val mockProvider = mockk<DatabaseConnectionProvider>()

    private val version = "1.21.4"

    @BeforeEach
    fun setUp() {
        CacheManager.invalidateAll()

        Database.setProvider(mockProvider)
        every { mockProvider.getConnection() } returns mockConnection
        every { mockConnection.prepareStatement(any()) } returns mockStatement
        every { mockConnection.close() } just Runs
        every { mockStatement.close() } just Runs
        every { mockStatement.setString(any(), any()) } just Runs
        every { mockStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.close() } just Runs
    }

    @AfterEach
    fun tearDown() {
        Database.resetProvider()
        CacheManager.invalidateAll()
        unmockkAll()
    }

    // ── isStale: pure decision logic ─────────────────────────────────────

    @Test
    fun `isStale is true when the DB epoch is strictly after the cached build`() {
        val builtAt = Instant.parse("2026-01-01T00:00:00Z")
        val dbEpoch = builtAt.plusSeconds(1)

        assertTrue(GetItemSourceGraphForVersionStep.isStale(builtAt, dbEpoch))
    }

    @Test
    fun `isStale is false when the DB epoch equals the cached build`() {
        val builtAt = Instant.parse("2026-01-01T00:00:00Z")

        assertFalse(GetItemSourceGraphForVersionStep.isStale(builtAt, builtAt))
    }

    @Test
    fun `isStale is false when the DB epoch is before the cached build`() {
        val builtAt = Instant.parse("2026-01-01T00:00:00Z")
        val dbEpoch = builtAt.minusSeconds(1)

        assertFalse(GetItemSourceGraphForVersionStep.isStale(builtAt, dbEpoch))
    }

    @Test
    fun `isStale is false when the DB epoch is unknown`() {
        val builtAt = Instant.parse("2026-01-01T00:00:00Z")

        assertFalse(GetItemSourceGraphForVersionStep.isStale(builtAt, null))
    }

    // ── LoadVersionIngestionEpochStep: the underlying SELECT ─────────────

    @Test
    fun `LoadVersionIngestionEpochStep returns the stored completed_at for a completed row`() {
        val completedAt = Instant.parse("2026-06-01T12:00:00Z")
        every { mockResultSet.next() } returns true andThen false
        every { mockResultSet.getTimestamp("completed_at") } returns Timestamp.from(completedAt)

        val result = TestUtils.executeAndAssertSuccess(LoadVersionIngestionEpochStep, version)

        assertEquals(completedAt, result)
        verify { mockStatement.setString(1, version) }
    }

    @Test
    fun `LoadVersionIngestionEpochStep returns null when the version has no completed row`() {
        every { mockResultSet.next() } returns false

        val result = TestUtils.executeAndAssertSuccess(LoadVersionIngestionEpochStep, version)

        assertNull(result)
    }

    @Test
    fun `LoadVersionIngestionEpochStep returns null when completed_at is NULL`() {
        every { mockResultSet.next() } returns true andThen false
        every { mockResultSet.getTimestamp("completed_at") } returns null

        val result = TestUtils.executeAndAssertSuccess(LoadVersionIngestionEpochStep, version)

        assertNull(result)
    }

    @Test
    fun `LoadVersionIngestionEpochStep maps a connection failure to a DatabaseError`() {
        every { mockProvider.getConnection() } throws SQLTimeoutException("down")

        val error = TestUtils.executeAndAssertFailure(LoadVersionIngestionEpochStep, version)

        assertIs<AppFailure.DatabaseError.ConnectionError>(error)
    }

    // ── currentEpoch: bounded by CacheManager.versionIngestionEpoch ──────

    @Test
    fun `currentEpoch queries the DB once and reuses the cached epoch on a second lookup`() {
        val completedAt = Instant.parse("2026-06-01T12:00:00Z")
        every { mockResultSet.next() } returns true andThen false
        every { mockResultSet.getTimestamp("completed_at") } returns Timestamp.from(completedAt)

        val first = runBlocking { GetItemSourceGraphForVersionStep.currentEpoch(version) }
        val second = runBlocking { GetItemSourceGraphForVersionStep.currentEpoch(version) }

        assertEquals(completedAt, first)
        assertEquals(completedAt, second)
        verify(exactly = 1) { mockStatement.executeQuery() }
        assertEquals(completedAt, CacheManager.versionIngestionEpoch.getIfPresent(version))
    }

    @Test
    fun `currentEpoch does not cache a null result, so an unignested version is re-checked next time`() {
        every { mockResultSet.next() } returns false

        val first = runBlocking { GetItemSourceGraphForVersionStep.currentEpoch(version) }
        val second = runBlocking { GetItemSourceGraphForVersionStep.currentEpoch(version) }

        assertNull(first)
        assertNull(second)
        verify(exactly = 2) { mockStatement.executeQuery() }
    }

    // ── process(): cache-hit freshness gate ──────────────────────────────

    @Test
    fun `process serves the cached graph without rebuilding when the DB epoch is not newer`() {
        val builtAt = Instant.now().minus(1, ChronoUnit.HOURS)
        val cachedGraph = ItemSourceGraph.builder().build()
        CacheManager.itemSourceGraph.put(version, CachedItemSourceGraph(cachedGraph, builtAt))

        // DB reports a completion from before the cached build → not stale.
        every { mockResultSet.next() } returns true andThen false
        every { mockResultSet.getTimestamp("completed_at") } returns Timestamp.from(builtAt.minusSeconds(60))

        val result = runBlocking { GetItemSourceGraphForVersionStep.process(version) }

        assertIs<Result.Success<ItemSourceGraph>>(result)
        assertSame(cachedGraph, result.value)
        // Only the epoch SELECT ran — no rebuild queries were issued.
        verify(exactly = 1) { mockStatement.executeQuery() }
    }

    @Test
    fun `process treats a missing ledger row as fresh and keeps serving the cache`() {
        val builtAt = Instant.now().minus(1, ChronoUnit.HOURS)
        val cachedGraph = ItemSourceGraph.builder().build()
        CacheManager.itemSourceGraph.put(version, CachedItemSourceGraph(cachedGraph, builtAt))

        every { mockResultSet.next() } returns false

        val result = runBlocking { GetItemSourceGraphForVersionStep.process(version) }

        assertIs<Result.Success<ItemSourceGraph>>(result)
        assertSame(cachedGraph, result.value)
    }
}
