package app.mcorg.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the glyph rules against a snapshot of a real ingested item registry.
 *
 * This catches *rule regressions* — reordering, an over-broad pattern, a typo — on every PR. It
 * does not and cannot detect a new Minecraft version shipping new items: it runs against a
 * committed snapshot, not the live registry. That job belongs to the ingestion-time check in
 * MCO-475. Keep both; they are different jobs.
 */
class ItemGlyphTest {

    private val itemIds: List<String> = readResource("/minecraft/item-ids.txt")
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    private val snapshotVersion: String = readResource("/minecraft/item-ids.version").trim()

    private fun readResource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `snapshot is the registry we think it is`() {
        assertTrue(itemIds.size > 1500, "snapshot looks truncated: ${itemIds.size} ids")
        assertTrue(snapshotVersion.isNotEmpty())
    }

    @Test
    fun `every real item id resolves to a glyph`() {
        val gaps = ItemGlyph.unmapped(itemIds)
        assertTrue(
            gaps.isEmpty(),
            "Minecraft $snapshotVersion has ${gaps.size} item(s) with no glyph. Add a rule in " +
                "ItemGlyph.RULES, or add the id to NOT_AN_ITEM if it is not a real item:\n" +
                gaps.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `every glyph a rule can produce has an svg on disk`() {
        val missing = ItemGlyph.ALL.map { it.name }.filter { ItemGlyphSprites.body(it) == null }
        assertTrue(missing.isEmpty(), "glyphs with no SVG: $missing")
    }

    @Test
    fun `every svg on disk is reachable from some rule`() {
        val onDisk = ItemGlyphSprites.filesOnDisk()
        assertNotNull(onDisk, "sprite directory should be listable when running from classes")
        val known = ItemGlyph.ALL.map { it.name }.toSet()
        val orphans = onDisk.filterNot { it in known }
        assertTrue(orphans.isEmpty(), "SVGs no rule can ever select: $orphans")
    }

    /**
     * The detection MCO-475's ingestion check is built on: an id no rule covers has to come back as
     * a gap, namespace stripped and sorted, and has to do so *alongside* ids that do resolve — the
     * ingestion check runs `unmapped` over a whole 1,500-id registry, so a version that adds two new
     * items must yield those two and nothing else.
     */
    @Test
    fun `ids no rule covers are reported as gaps, and only those`() {
        val nextVersionRegistry = itemIds + listOf("minecraft:zorkmid", "frobnicator", "minecraft:air")

        assertEquals(listOf("frobnicator", "zorkmid"), ItemGlyph.unmapped(nextVersionRegistry))
        assertNull(ItemGlyph.resolve("zorkmid"))
        assertTrue(ItemGlyph.isRenderable("minecraft:zorkmid"), "an unknown id is still a real item")
    }

    @Test
    fun `technical ids are excluded rather than reported as gaps`() {
        listOf("minecraft:air", "minecraft:water", "potted_cactus", "minecraft:piston_head")
            .forEach {
                assertNull(ItemGlyph.resolve(it), "$it should resolve to no glyph")
                assertTrue(ItemGlyph.unmapped(listOf(it)).isEmpty(), "$it should not count as a gap")
            }
    }

    /**
     * The exact bug a broad material rule causes. A `copper` rule above the tool rules turns these
     * into copper blocks, and it is invisible in review because a wrong icon still looks like an
     * icon.
     */
    @Test
    fun `copper tools and gear beat the copper material rule`() {
        assertEquals("axe", ItemGlyph.resolve("copper_axe")?.name)
        assertEquals("armor", ItemGlyph.resolve("copper_boots")?.name)
        assertEquals("redstone", ItemGlyph.resolve("copper_bulb")?.name)
        assertEquals("chest", ItemGlyph.resolve("copper_chest")?.name)
        // …while the oxidation family itself still lands on `copper`.
        assertEquals("copper", ItemGlyph.resolve("oxidized_cut_copper")?.name)
        assertEquals("copper", ItemGlyph.resolve("waxed_weathered_copper")?.name)
    }

    @Test
    fun `crop beats food where they overlap`() {
        // `carrot` is both a crop and a food; first-match-wins has to settle it.
        assertEquals("crop", ItemGlyph.resolve("carrot")?.name)
        assertEquals("food", ItemGlyph.resolve("golden_carrot")?.name)
    }

    @Test
    fun `shape beats material on shaped blocks`() {
        assertEquals("stairs", ItemGlyph.resolve("oak_stairs")?.name)
        assertEquals("slab", ItemGlyph.resolve("deepslate_tile_slab")?.name)
        assertEquals("door", ItemGlyph.resolve("iron_door")?.name)
    }

    @Test
    fun `tint is read from the id prefix`() {
        assertEquals("cyan", ItemGlyph.tint("cyan_wool"))
        assertEquals("dark_oak", ItemGlyph.tint("dark_oak_planks"))
        assertEquals("netherite", ItemGlyph.tint("netherite_pickaxe"))
        assertEquals("oxidized", ItemGlyph.tint("oxidized_copper"))
    }

    @Test
    fun `longest tint prefix wins`() {
        // `light_blue` must not be shortened to `light`, nor `dark_oak` to `oak`.
        assertEquals("light_blue", ItemGlyph.tint("light_blue_concrete"))
        assertEquals("light_gray", ItemGlyph.tint("light_gray_wool"))
        assertEquals("dark_oak", ItemGlyph.tint("dark_oak_boat"))
    }

    @Test
    fun `untinted glyphs and bare ids yield no tint`() {
        assertNull(ItemGlyph.tint("redstone"), "redstone glyph takes no axis")
        assertNull(ItemGlyph.tint("oak_stairs"), "MATERIAL axis is deliberately untinted")
        assertNull(ItemGlyph.tint("bundle"), "a plain bundle carries no colour prefix")
    }

    @Test
    fun `namespaced and bare ids resolve the same`() {
        assertEquals(ItemGlyph.resolve("oak_planks"), ItemGlyph.resolve("minecraft:oak_planks"))
        assertEquals("cyan", ItemGlyph.tint("minecraft:cyan_bed"))
    }

    @Test
    fun `the set is the size we designed`() {
        assertEquals(73, ItemGlyph.ALL.size)
        val renderable = itemIds.filter { ItemGlyph.isRenderable(it) }
        assertTrue(renderable.size > 1500, "renderable items: ${renderable.size}")
        renderable.forEach { assertNotNull(ItemGlyph.resolve(it), "no glyph for $it") }
    }
}
