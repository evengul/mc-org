package app.mcorg.engine.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Invariants of the committed `structure-density.txt` snapshot.
 *
 * This pins the shape of the file and the facts [UnitCostModel] relies on. It deliberately
 * does **not** download a jar: the snapshot exists precisely because the numbers have not
 * moved across 1.20 -> 26.2, and a test that fetched 50 MB on every run to confirm that
 * would cost more than it catches.
 *
 * The drift check against Mojang lives in `scripts/dump-structure-density.py --check`, and
 * belongs with ingesting a new Minecraft version — the same division as `dump-item-ids.sh`
 * and `ItemGlyphTest`. What this test catches is the likelier failure: a hand-edited or
 * truncated snapshot, or a regeneration that silently dropped a section.
 */
class StructureDensityTest {

    @Test
    fun `the snapshot parses and names the version it came from`() {
        assertTrue(StructureDensity.version.isNotBlank(), "no version= line")
        assertEquals(20, StructureDensity.placements.size, "1.20 -> 26.2 all ship 20 structure sets")
    }

    @Test
    fun `villages are present as the density baseline`() {
        // Every other structure is expressed as a multiple of this, so its absence would not
        // fail loudly — it would quietly rescale the entire structure half of the effort table.
        assertEquals(1156.0, StructureDensity.baselineChunks, "villages are 34x34 chunks")
        assertEquals(1.0, StructureDensity.densityRatio("villages"))
    }

    @Test
    fun `density is derived for every spread placement and absent for the stronghold`() {
        val (spread, other) = StructureDensity.placements.values.partition { it.kind == "random_spread" }

        assertEquals(19, spread.size)
        assertTrue(spread.all { it.chunksPerOccurrence != null }, "a spread always has a spacing")

        // Strongholds are placed on concentric rings, so spacing^2 does not apply to them.
        // densityRatio falls back to 1.0 rather than inventing a spacing they do not have.
        assertEquals(listOf("concentric_rings"), other.map { it.kind })
        assertEquals(null, StructureDensity.placements.getValue("strongholds").chunksPerOccurrence)
        assertEquals(1.0, StructureDensity.densityRatio("strongholds"))
    }

    @Test
    fun `the rarities that decide the interesting cases hold their measured order`() {
        // Not round numbers to be pinned for their own sake — these four are what price the
        // obsidian, charcoal and book cases in UnitCostModelAdversarialTest.
        assertEquals(6400.0, StructureDensity.placements.getValue("woodland_mansions").chunksPerOccurrence)
        assertEquals(400.0, StructureDensity.placements.getValue("end_cities").chunksPerOccurrence)
        assertEquals(1156.0, StructureDensity.placements.getValue("trial_chambers").chunksPerOccurrence)
        assertEquals(100.0, StructureDensity.placements.getValue("buried_treasures").chunksPerOccurrence)

        // Density and accessibility are different quantities: buried treasure is the densest
        // structure in the game and needs a map from a shipwreck before it can be found at all.
        assertTrue(
            StructureDensity.densityRatio("buried_treasures") < StructureDensity.densityRatio("villages"),
            "which is why access is curated separately rather than read off the density",
        )
    }

    @Test
    fun `membership separates the three cases MCO-501 turns on`() {
        assertEquals(
            setOf("end_cities"),
            StructureDensity.setsContaining("minecraft:ender_chest"),
            "an ender chest does generate — in End city ships — so the fix cannot be a " +
                "'does this generate naturally' flag",
        )
        assertTrue(
            "villages" in StructureDensity.setsContaining("minecraft:campfire"),
            "a campfire is in taiga and snowy villages, so breaking one is an ordinary errand",
        )
        assertTrue(
            StructureDensity.setsContaining("minecraft:soul_campfire").isEmpty(),
            "no template places a soul campfire, so the only way to have one is to build it",
        )
    }

    @Test
    fun `ordinary building blocks are in the membership, which is why craftability gates it`() {
        // The gate in EffortTable exists because these are here. Villages are built out of
        // them, and none is structure-gated in any useful sense.
        for (terrain in listOf("cobblestone", "sand", "dirt", "oak_log")) {
            assertTrue(
                StructureDensity.setsContaining("minecraft:$terrain").isNotEmpty(),
                "$terrain is placed by a structure template",
            )
        }
    }

    @Test
    fun `every structure set named in membership has a placement`() {
        // A typo or a stale family mapping in the generator would otherwise silently price a
        // block at densityRatio's 1.0 fallback instead of failing.
        val known = StructureDensity.placements.keys
        val referenced = listOf(
            "ender_chest", "campfire", "bookshelf", "obsidian", "cobblestone", "sand",
        ).flatMap { StructureDensity.setsContaining("minecraft:$it") }.toSet()

        assertTrue(referenced.isNotEmpty(), "the sample should reference some structures")
        for (set in referenced) {
            assertNotNull(StructureDensity.placements[set], "membership names unknown set '$set'")
            assertTrue(set in known)
        }
    }
}
