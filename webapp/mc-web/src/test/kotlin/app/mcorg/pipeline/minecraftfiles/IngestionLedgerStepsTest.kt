package app.mcorg.pipeline.minecraftfiles

import app.mcorg.data.minecraft.ExtractionVersion
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
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

    /**
     * Synthetic versions for the `minecraft_items` tests, deliberately not any real release. The ITs
     * share one database and nothing truncates the item catalog between classes, so seeding a
     * plausible version could both read another class's rows and delete them on the way out.
     */
    private val itemsA = MinecraftVersion.Release(1, 90, 1)
    private val itemsB = MinecraftVersion.Release(1, 90, 2)
    private val itemsEmpty = MinecraftVersion.Release(1, 90, 3)

    @BeforeEach
    fun clearLedger() {
        DatabaseTestExtension.executeSQL("TRUNCATE TABLE minecraft_version_ingestion")
        dropSyntheticVersions()
    }

    /** Nothing truncates between classes, so the last test's rows must not outlive this class. */
    @AfterAll
    fun dropSyntheticVersionsAfterwards() = dropSyntheticVersions()

    private fun dropSyntheticVersions() {
        DatabaseTestExtension.executeSQL(
            "DELETE FROM minecraft_items WHERE version IN ('$itemsA', '$itemsB', '$itemsEmpty')"
        )
        DatabaseTestExtension.executeSQL(
            "DELETE FROM minecraft_version WHERE version IN ('$itemsA', '$itemsB', '$itemsEmpty')"
        )
    }

    // --- LoadIngestionStatusStep ---------------------------------------------------------------

    @Test
    fun `LoadIngestionStatusStep returns status plus SHA per row and omits absent versions`() {
        seed(v1, IngestionStatus.COMPLETED, serverJarSha = "sha-1")
        seed(v2, IngestionStatus.FAILED)

        val loaded = runBlocking { LoadIngestionStatusStep.process(Unit) }
        val map = assertIs<Result.Success<Map<MinecraftVersion.Release, IngestionLedgerEntry>>>(loaded).value

        assertEquals(IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-1"), map[v1])
        assertEquals(IngestionLedgerEntry(IngestionStatus.FAILED, null), map[v2])
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
    fun `MarkIngestionCompleted sets completed, records SHA and URL, clears last_error`() {
        seed(v1, IngestionStatus.IN_PROGRESS, attemptCount = 1, lastError = "stale")

        val result = runBlocking { MarkIngestionCompletedStep.process(completed(v1, "fresh-sha")) }
        assertIs<Result.Success<*>>(result)

        val row = assertNotNull(readRow(v1))
        assertEquals(IngestionStatus.COMPLETED, row.status)
        assertTrue(row.hasCompleted)
        assertNull(row.lastError)
        assertEquals("fresh-sha", row.serverJarSha)
        assertEquals("https://example.test/${v1}/server.jar", row.serverJarUrl)
    }

    // --- unmapped_items on the ledger row (MCO-475) ---------------------------------------------

    @Test
    fun `MarkIngestionCompleted records the item ids no glyph rule covers`() {
        seed(v1, IngestionStatus.IN_PROGRESS)

        val result = runBlocking {
            MarkIngestionCompletedStep.process(completed(v1, "sha", listOf("brand_new_thing", "another_gap")))
        }
        assertIs<Result.Success<*>>(result)

        assertEquals(listOf("brand_new_thing", "another_gap"), assertNotNull(readRow(v1)).unmappedItems)
    }

    /**
     * The self-healing property: adding a glyph rule has to clear the gap on the next nightly with
     * no manual resolution step. That only holds because the column is overwritten unconditionally,
     * including with an empty array — a write that skipped the empty case would leave a fixed gap
     * reported forever.
     */
    @Test
    fun `a later run with no gaps clears a previously recorded gap`() {
        seed(v1, IngestionStatus.IN_PROGRESS)
        runBlocking { MarkIngestionCompletedStep.process(completed(v1, "sha", listOf("brand_new_thing"))) }
        assertEquals(listOf("brand_new_thing"), assertNotNull(readRow(v1)).unmappedItems)

        val rerun = runBlocking { MarkIngestionCompletedStep.process(completed(v1, "sha", emptyList())) }
        assertIs<Result.Success<*>>(rerun)

        assertEquals(emptyList(), assertNotNull(readRow(v1)).unmappedItems)
    }

    // --- ComputeUnmappedItemsStep ---------------------------------------------------------------

    @Test
    fun `ComputeUnmappedItems reports only real stored ids that no rule covers`() {
        seedItems(
            itemsA,
            "minecraft:oak_planks",        // covered by a rule
            "minecraft:cyan_wool",         // covered by a rule
            "minecraft:air",               // technical id — resolves to nothing, but is not a gap
            "minecraft:potted_cactus",     // block state — likewise
            "minecraft:zorkmid",           // a genuine gap
            "minecraft:frobnicator",       // a genuine gap
        )
        // A different version's gap must not leak into this version's report.
        seedItems(itemsB, "minecraft:other_versions_gap")

        val result = runBlocking { ComputeUnmappedItemsStep.process(itemsA) }
        val gaps = assertIs<Result.Success<List<String>>>(result).value

        // Sorted and namespace-stripped, as ItemGlyph.unmapped returns them.
        assertEquals(listOf("frobnicator", "zorkmid"), gaps)
    }

    @Test
    fun `ComputeUnmappedItems reports nothing for a version with no stored items`() {
        val result = runBlocking { ComputeUnmappedItemsStep.process(itemsEmpty) }
        assertEquals(emptyList(), assertIs<Result.Success<List<String>>>(result).value)
    }

    // --- LoadNewestUnmappedItemsStep -------------------------------------------------------------

    /**
     * `version` is a VARCHAR, so `ORDER BY version` puts 1.9.x above 1.21.x. The newest row has to
     * be picked by parsed version, and this pins it with exactly that pair.
     */
    @Test
    fun `LoadNewestUnmappedItems picks the newest version numerically, not lexically`() {
        seed(v1, IngestionStatus.COMPLETED, unmappedItems = listOf("gap_in_1_21_4")) // 1.21.4
        seed(MinecraftVersion.Release(1, 9, 4), IngestionStatus.COMPLETED, unmappedItems = listOf("gap_in_1_9_4"))

        val report = assertIs<Result.Success<UnmappedItemsReport?>>(
            runBlocking { LoadNewestUnmappedItemsStep.process(Unit) }
        ).value

        assertEquals(v1, assertNotNull(report).version)
        assertEquals(listOf("gap_in_1_21_4"), report.itemIds)
    }

    @Test
    fun `LoadNewestUnmappedItems ignores versions that never completed`() {
        seed(v1, IngestionStatus.COMPLETED, unmappedItems = listOf("gap"))
        seed(MinecraftVersion.Release(1, 22, 0), IngestionStatus.IN_PROGRESS, unmappedItems = listOf("newer_but_unfinished"))

        val report = assertIs<Result.Success<UnmappedItemsReport?>>(
            runBlocking { LoadNewestUnmappedItemsStep.process(Unit) }
        ).value

        assertEquals(v1, assertNotNull(report).version)
    }

    @Test
    fun `LoadNewestUnmappedItems returns an empty report rather than failing when there are no gaps`() {
        seed(v1, IngestionStatus.COMPLETED)

        val report = assertIs<Result.Success<UnmappedItemsReport?>>(
            runBlocking { LoadNewestUnmappedItemsStep.process(Unit) }
        ).value

        assertEquals(emptyList(), assertNotNull(report).itemIds)
    }

    @Test
    fun `LoadNewestUnmappedItems returns null when nothing has been ingested`() {
        val report = assertIs<Result.Success<UnmappedItemsReport?>>(
            runBlocking { LoadNewestUnmappedItemsStep.process(Unit) }
        ).value

        assertNull(report)
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
    fun `Filter drops completed versions whose stored SHA matches and keeps SHA mismatches`() {
        seed(v1, IngestionStatus.COMPLETED, serverJarSha = "sha-unchanged")
        seed(v2, IngestionStatus.COMPLETED, serverJarSha = "sha-old")

        val input = listOf(
            jar(v1, "sha-unchanged"), // completed + same SHA → skip
            jar(v2, "sha-new"),       // completed + changed SHA → re-ingest
        )

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<ResolvedServerJar>>>(result).value

        assertEquals(setOf(v2), kept.map { it.version }.toSet())
    }

    @Test
    fun `Filter keeps missing, failed and in_progress versions and a completed NULL-SHA row is skipped not re-ingested`() {
        seed(v1, IngestionStatus.COMPLETED, serverJarSha = null) // SHA unknown → skip (do NOT re-ingest/duplicate); migration V2_36_0 fills these in prod
        seed(v2, IngestionStatus.FAILED, serverJarSha = "sha-x")
        // v3 intentionally has no ledger row (the truncate-to-recreate path)

        val input = listOf(jar(v1, "sha-a"), jar(v2, "sha-x"), jar(v3, "sha-c"))

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<ResolvedServerJar>>>(result).value

        // v1 is NOT re-ingested (no duplication); v2 (failed) and v3 (missing) are.
        assertEquals(setOf(v2, v3), kept.map { it.version }.toSet())
    }

    @Test
    fun `Filter keeps everything when the ledger is empty`() {
        val input = listOf(jar(v1, "sha-a"), jar(v2, "sha-b"))

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<ResolvedServerJar>>>(result).value

        assertEquals(setOf(v1, v2), kept.map { it.version }.toSet())
    }

    @Test
    fun `Filter re-ingests a completed matching-SHA version ingested under an older extraction version`() {
        // SHA still matches, but it was ingested under stale extraction code → re-ingest once.
        seed(v1, IngestionStatus.COMPLETED, serverJarSha = "sha-same", extractionVersion = ExtractionVersion.CURRENT - 1)
        seed(v2, IngestionStatus.COMPLETED, serverJarSha = "sha-same", extractionVersion = ExtractionVersion.CURRENT)

        val input = listOf(jar(v1, "sha-same"), jar(v2, "sha-same"))

        val result = runBlocking { FilterAlreadyStoredVersionsStep.process(input) }
        val kept = assertIs<Result.Success<List<ResolvedServerJar>>>(result).value

        // v1 is stale → re-ingested; v2 is current with a matching SHA → skipped.
        assertEquals(setOf(v1), kept.map { it.version }.toSet())
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun jar(version: MinecraftVersion.Release, sha: String) =
        ResolvedServerJar(version, URI.create("https://example.test/$version/server.jar"), sha)

    private fun completed(
        version: MinecraftVersion.Release,
        sha: String,
        unmappedItems: List<String> = emptyList(),
    ) = CompletedIngestion(jar(version, sha), unmappedItems)

    private fun seedItems(version: MinecraftVersion.Release, vararg itemIds: String) = runBlocking {
        // minecraft_items.version is a foreign key into minecraft_version; the ledger table's own
        // FK was dropped, which is why the other helpers here do not need this.
        val parent = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT DO NOTHING"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) }
        ).process(Unit)
        assertIs<Result.Success<*>>(parent)

        itemIds.forEach { itemId ->
            val result = DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO minecraft_items (version, item_id, item_name) VALUES (?, ?, ?)"
                ),
                parameterSetter = { stmt, _ ->
                    stmt.setString(1, version.toString())
                    stmt.setString(2, itemId)
                    stmt.setString(3, itemId.substringAfter(':'))
                }
            ).process(Unit)
            assertIs<Result.Success<*>>(result)
        }
    }

    private data class LedgerRow(
        val status: String,
        val attemptCount: Int,
        val lastError: String?,
        val serverJarSha: String?,
        val serverJarUrl: String?,
        val hasStarted: Boolean,
        val hasCompleted: Boolean,
        val unmappedItems: List<String>,
    )

    private fun seed(
        version: MinecraftVersion.Release,
        status: String,
        attemptCount: Int = 0,
        lastError: String? = null,
        serverJarSha: String? = null,
        extractionVersion: Int = ExtractionVersion.CURRENT,
        unmappedItems: List<String> = emptyList(),
    ) = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_version_ingestion (version, status, attempt_count, last_error, server_jar_sha, extraction_version, unmapped_items) VALUES (?, ?, ?, ?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version.toString())
                stmt.setString(2, status)
                stmt.setInt(3, attemptCount)
                stmt.setString(4, lastError)
                stmt.setString(5, serverJarSha)
                stmt.setInt(6, extractionVersion)
                stmt.setArray(7, stmt.connection.createArrayOf("text", unmappedItems.toTypedArray()))
            }
        ).process(Unit)
        assertIs<Result.Success<*>>(result)
    }

    private fun readRow(version: MinecraftVersion.Release): LedgerRow? = runBlocking {
        DatabaseSteps.query<Unit, LedgerRow?>(
            sql = SafeSQL.select(
                "SELECT status, attempt_count, last_error, server_jar_sha, server_jar_url, started_at, completed_at, unmapped_items FROM minecraft_version_ingestion WHERE version = ?"
            ),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs ->
                if (rs.next()) {
                    LedgerRow(
                        status = rs.getString("status"),
                        attemptCount = rs.getInt("attempt_count"),
                        lastError = rs.getString("last_error"),
                        serverJarSha = rs.getString("server_jar_sha"),
                        serverJarUrl = rs.getString("server_jar_url"),
                        hasStarted = rs.getTimestamp("started_at") != null,
                        hasCompleted = rs.getTimestamp("completed_at") != null,
                        unmappedItems = (rs.getArray("unmapped_items").array as Array<*>).filterIsInstance<String>(),
                    )
                } else null
            }
        ).process(Unit).getOrThrow()
    }
}
