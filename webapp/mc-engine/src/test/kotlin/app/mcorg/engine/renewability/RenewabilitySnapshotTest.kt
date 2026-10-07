package app.mcorg.engine.renewability

import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.engine.model.ItemSourceGraph
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every reviewed version's verdicts, pinned (MCO-565).
 *
 * Each `renewability/<version>/` holds the ingested sources as a fixture and the verdicts Even
 * reviewed item by item. This rebuilds the graph from the fixture and fails when the engine now
 * says something else about any item — a rule change shows up here as the list of items it moved,
 * which is what a reviewer has to look at anyway.
 *
 * A change that is meant to move verdicts is reviewed with `renewability-diagnostics review`,
 * then accepted with `renewability-diagnostics version=<v> snapshot`, which rewrites both files.
 *
 * A version that borrowed villager trades names its donor in a `# trades borrowed from <v>`
 * header line, written by `snapshot`, and the donor must be snapshotted too. Production picks the
 * newest ingested version with trades, which may not be one that is snapshotted, so the header is
 * what keeps a snapshot reproducible.
 */
class RenewabilitySnapshotTest {

    private data class Snapshot(
        val version: String,
        val fixture: RenewabilityFixture,
        val verdicts: Map<String, Boolean>,
        val tradeDonor: String?,
    ) {
        val graph: ItemSourceGraph by lazy { ItemSourceGraphBuilder.buildFromResourceSources(fixture.sources) }
    }

    private val snapshots: List<Snapshot> by lazy {
        val root = javaClass.getResource("/renewability")?.toURI()?.let(::File)
            ?: fail("No renewability snapshots on the test classpath")
        root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.map { dir ->
            val fixture = GZIPInputStream(File(dir, "sources.tsv.gz").inputStream()).bufferedReader().use { it.readText() }
            val lines = File(dir, "verdicts.tsv").readLines()
            val verdicts = lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line -> line.split('\t').let { it[0] to (it[1] == "1") } }
            val donor = lines.firstOrNull { it.startsWith(DONOR_HEADER) }?.removePrefix(DONOR_HEADER)?.trim()
            Snapshot(dir.name, RenewabilityFixture.read(fixture), verdicts, donor)
        }
    }

    @Test
    fun `the reviewed versions are all here`() {
        assertTrue(snapshots.map { it.version }.containsAll(listOf("26.3.0", "1.21.11")))
    }

    @Test
    fun `the engine reproduces every reviewed verdict`() {
        val byVersion = snapshots.associateBy { it.version }
        val failures = snapshots.mapNotNull { snapshot ->
            val tradeDonor = snapshot.tradeDonor?.let { v ->
                val donor = byVersion[v] ?: fail("${snapshot.version} borrowed trades from $v, which has no snapshot")
                Renewability.TradeDonor(v, donor.graph)
            }
            val now = Renewability.of(snapshot.graph, snapshot.fixture.registry, snapshot.fixture.tags, tradeDonor)
                .explain().associate { it.itemId to it.renewable }
            val moved = (now.keys + snapshot.verdicts.keys).sorted().filter { now[it] != snapshot.verdicts[it] }
            if (moved.isEmpty()) null
            else "${snapshot.version}: ${moved.size} verdicts moved — " +
                moved.take(40).joinToString(" ") { "$it(${snapshot.verdicts[it].mark()}→${now[it].mark()})" }
        }
        assertEquals(emptyList(), failures, "Review with renewability-diagnostics before accepting a new snapshot")
    }

    @Test
    fun `the counts Even reviewed`() {
        val counts = snapshots.associate { s -> s.version to (s.verdicts.size to s.verdicts.count { it.value }) }
        assertEquals(1519 to 1165, counts["26.3.0"])
        assertEquals(1366 to 1082, counts["1.21.11"])
    }

    @Test
    fun `the acceptance items read as the issue says`() {
        for (version in listOf("26.3.0", "1.21.11")) {
            val verdicts = snapshots.single { it.version == version }.verdicts
            for (id in listOf("tuff", "deepslate", "calcite", "end_stone", "netherrack", "ancient_debris", "diamond", "sand", "black_concrete", "raw_iron", "raw_gold")) {
                assertEquals(false, verdicts[id], "$version: $id")
            }
            for (id in listOf("cobblestone", "stone", "basalt", "oak_log", "oak_leaves", "crimson_stem", "ice", "amethyst_shard", "gravel", "clay", "iron_ingot", "gold_ingot")) {
                assertEquals(true, verdicts[id], "$version: $id")
            }
        }
    }

    @Test
    fun `a version without trades of its own names the snapshot it borrowed them from`() {
        for (snapshot in snapshots.filterNot { Renewability.hasVillagerTrades(it.graph) }) {
            assertTrue(snapshot.tradeDonor != null, "${snapshot.version} has no trades and no `$DONOR_HEADER` line")
        }
    }

    private companion object {
        const val DONOR_HEADER = "# trades borrowed from "
    }

    private fun Boolean?.mark() = when (this) { true -> "R"; false -> "N"; null -> "-" }
}
