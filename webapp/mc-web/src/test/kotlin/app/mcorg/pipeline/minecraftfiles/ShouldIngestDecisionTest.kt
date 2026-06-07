package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure unit tests for the SHA freshness decision (no DB). The integrated, DB-backed version is
 * covered in [IngestionLedgerStepsTest].
 */
class ShouldIngestDecisionTest {

    private val jar = ResolvedServerJar(MinecraftVersion.Release(1, 21, 4), URI.create("https://example.test/server.jar"), "sha-current")

    @Test
    fun `ingests when there is no ledger row`() {
        assertTrue(FilterAlreadyStoredVersionsStep.shouldIngest(jar, null))
    }

    @Test
    fun `ingests when the previous run did not complete`() {
        assertTrue(FilterAlreadyStoredVersionsStep.shouldIngest(jar, IngestionLedgerEntry(IngestionStatus.FAILED, "sha-current")))
        assertTrue(FilterAlreadyStoredVersionsStep.shouldIngest(jar, IngestionLedgerEntry(IngestionStatus.IN_PROGRESS, "sha-current")))
    }

    @Test
    fun `does NOT re-ingest when completed but stored SHA is unknown (backfilled NULL is adopted in place)`() {
        assertFalse(FilterAlreadyStoredVersionsStep.shouldIngest(jar, IngestionLedgerEntry(IngestionStatus.COMPLETED, null)))
    }

    @Test
    fun `ingests when completed but the SHA has changed`() {
        assertTrue(FilterAlreadyStoredVersionsStep.shouldIngest(jar, IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-old")))
    }

    @Test
    fun `skips only when completed and the stored SHA matches`() {
        assertFalse(FilterAlreadyStoredVersionsStep.shouldIngest(jar, IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-current")))
    }

    @Test
    fun `decision depends on the jar's own SHA`() {
        val entry = IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-current")
        assertEquals(false, FilterAlreadyStoredVersionsStep.shouldIngest(jar, entry))
        assertEquals(true, FilterAlreadyStoredVersionsStep.shouldIngest(jar.copy(sha1 = "sha-different"), entry))
    }
}
