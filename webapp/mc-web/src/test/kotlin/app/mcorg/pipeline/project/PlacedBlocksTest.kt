package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The placed-cell rules both import doors read (MCO-396, MCO-308).
 *
 * `MapSchematicToMaterialsStepTest` exercises the same tables through the schematic step and
 * `ImportIdeaPipelineTest` through the idea step; this covers the rules themselves, including
 * the cases only one door used to reach.
 */
class PlacedBlocksTest {

    private val catalog = listOf(
        "minecraft:water",
        "minecraft:flowing_water",
        "minecraft:lava",
        "minecraft:powder_snow",
        "minecraft:nether_portal",
        "minecraft:air",
        "minecraft:water_bucket",
        "minecraft:lava_bucket",
        "minecraft:powder_snow_bucket",
        "minecraft:oak_planks",
        "minecraft:redstone",
        "minecraft:redstone_wire",
        "minecraft:birch_sign",
        "minecraft:birch_wall_sign",
        "minecraft:dirt",
        "minecraft:farmland",
        "minecraft:carrot",
        "minecraft:carrots",
        "minecraft:cauldron",
        "minecraft:budding_amethyst",
        "minecraft:potted_cactus",
    ).map { Item(it, it.substringAfterLast(':')) }

    private val byId = catalog.associateBy { it.id }

    private fun resolve(vararg rows: Pair<String, Int>) = resolvePlacedCells(rows.toMap(), byId)

    private fun PlacedMaterials.ids() = requirements.associate { it.first.id to it.second }

    @Test
    fun `placed redstone dust is redstone, not an unobtainable block`() {
        // The MCO-308 report, verbatim: idea #3 offered 592 `Redstone Wire (Block)` and the
        // warning strip called them creative-only. They are 592 redstone.
        val result = resolve("minecraft:redstone_wire" to 592)

        assertEquals(mapOf("minecraft:redstone" to 592), result.ids())
        assertTrue(result.unknown.isEmpty())
    }

    @Test
    fun `a wall sign is a sign`() {
        val result = resolve("minecraft:birch_wall_sign" to 1)

        assertEquals(mapOf("minecraft:birch_sign" to 1), result.ids())
    }

    @Test
    fun `two placed forms of the same material sum rather than overwrite`() {
        // The reason resolution sums: both ids are real, and both are dirt.
        val result = resolve("minecraft:farmland" to 40, "minecraft:dirt" to 8)

        assertEquals(mapOf("minecraft:dirt" to 48), result.ids())
    }

    @Test
    fun `a redirect the version lacks falls through to the block itself`() {
        // Better a BLOCKED row naming something real than a promise of an item this version
        // has never had.
        val narrow = catalog.filterNot { it.id == "minecraft:carrot" }.associateBy { it.id }

        val result = resolvePlacedCells(mapOf("minecraft:carrots" to 9), narrow)

        assertEquals(mapOf("minecraft:carrots" to 9), result.requirements.associate { it.first.id to it.second })
    }

    @Test
    fun `potted plants and other non-materials are dropped`() {
        val result = resolve(
            "minecraft:potted_cactus" to 3,
            "minecraft:nether_portal" to 24,
            "minecraft:air" to 9_389_854,
            "minecraft:oak_planks" to 5,
        )

        assertEquals(mapOf("minecraft:oak_planks" to 5), result.ids())
        assertTrue(result.unknown.isEmpty())
    }

    @Test
    fun `a genuinely unobtainable block resolves to itself and is left to the warning strip`() {
        val result = resolve("minecraft:budding_amethyst" to 4)

        assertEquals(mapOf("minecraft:budding_amethyst" to 4), result.ids())
    }

    @Test
    fun `an id no rule and no catalog claims is reported, not silently dropped`() {
        val result = resolve("minecraft:copper_golem_statue" to 2, "minecraft:oak_planks" to 1)

        assertEquals(listOf("minecraft:copper_golem_statue"), result.unknown)
        assertEquals(mapOf("minecraft:oak_planks" to 1), result.ids())
    }

    @Test
    fun `a fluid becomes one bucket and keeps its cell count aside`() {
        val result = resolve("minecraft:water" to 4000, "minecraft:oak_planks" to 12)

        assertEquals(mapOf("minecraft:oak_planks" to 12, "minecraft:water_bucket" to 1), result.ids())
        assertEquals(mapOf("minecraft:water_bucket" to 4000), result.placedCounts)
    }

    @Test
    fun `source and flowing cells count as the same fluid`() {
        val result = resolve("minecraft:water" to 900, "minecraft:flowing_water" to 13)

        assertEquals(1, result.requirements.single().second)
        assertEquals(mapOf("minecraft:water_bucket" to 913), result.placedCounts)
    }

    @Test
    fun `a fluid whose bucket the version lacks is dropped rather than invented`() {
        val narrow = catalog.filterNot { it.id == "minecraft:powder_snow_bucket" }.associateBy { it.id }

        val result = resolvePlacedCells(mapOf("minecraft:powder_snow" to 2, "minecraft:oak_planks" to 5), narrow)

        assertEquals(listOf("minecraft:oak_planks"), result.requirements.map { it.first.id })
        assertNull(result.placedCounts["minecraft:powder_snow_bucket"])
        assertTrue(result.unknown.isEmpty())
    }

    @Test
    fun `a filled cauldron is the cauldron you place`() {
        val result = resolve("minecraft:water_cauldron" to 1)

        assertEquals(mapOf("minecraft:cauldron" to 1), result.ids())
        assertTrue(result.placedCounts.isEmpty())
    }

    @Test
    fun `an ordinary list passes through untouched`() {
        val result = resolve("minecraft:oak_planks" to 64)

        assertEquals(mapOf("minecraft:oak_planks" to 64), result.ids())
        assertTrue(result.placedCounts.isEmpty())
    }
}
