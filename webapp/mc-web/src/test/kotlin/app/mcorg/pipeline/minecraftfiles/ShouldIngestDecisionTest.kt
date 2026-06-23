package app.mcorg.pipeline.minecraftfiles

import app.mcorg.data.minecraft.ExtractionVersion
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
    fun `re-ingests a completed matching-SHA version when the extraction code is newer`() {
        // SHA still matches, but the version was ingested under an older extraction version.
        val stale = IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-current", extractionVersion = ExtractionVersion.CURRENT - 1)
        assertTrue(FilterAlreadyStoredVersionsStep.shouldIngest(jar, stale))
    }

    @Test
    fun `does not re-ingest for extraction version once it is current`() {
        val current = IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-current", extractionVersion = ExtractionVersion.CURRENT)
        assertFalse(FilterAlreadyStoredVersionsStep.shouldIngest(jar, current))
    }

    @Test
    fun `decision depends on the jar's own SHA`() {
        val entry = IngestionLedgerEntry(IngestionStatus.COMPLETED, "sha-current")
        assertEquals(false, FilterAlreadyStoredVersionsStep.shouldIngest(jar, entry))
        assertEquals(true, FilterAlreadyStoredVersionsStep.shouldIngest(jar.copy(sha1 = "sha-different"), entry))
    }

    private val v = jar.version
    private val other = MinecraftVersion.Release(1, 21, 8)

    @Test
    fun `FORCE_REINGEST unset or false forces nothing`() {
        assertFalse(FilterAlreadyStoredVersionsStep.forcedPredicate(null)(v))
        assertFalse(FilterAlreadyStoredVersionsStep.forcedPredicate("")(v))
        assertFalse(FilterAlreadyStoredVersionsStep.forcedPredicate("false")(v))
    }

    @Test
    fun `FORCE_REINGEST true or all forces every version`() {
        assertTrue(FilterAlreadyStoredVersionsStep.forcedPredicate("true")(v))
        assertTrue(FilterAlreadyStoredVersionsStep.forcedPredicate("ALL")(other))
    }

    @Test
    fun `FORCE_REINGEST with a version list forces only those`() {
        val predicate = FilterAlreadyStoredVersionsStep.forcedPredicate(" 1.21.4 , 1.21.8 ")
        assertTrue(predicate(v))
        assertTrue(predicate(other))
        assertFalse(predicate(MinecraftVersion.Release(1, 20, 1)))
    }
}
