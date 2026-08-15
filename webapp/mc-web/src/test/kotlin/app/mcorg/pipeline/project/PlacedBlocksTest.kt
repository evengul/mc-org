package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-396 — the idea door's half of the placed-cell rules.
 *
 * The schematic door exercises the same tables through `MapSchematicToMaterialsStepTest`.
 * This covers the subset an idea can hit, where requirements are catalog items rather than
 * block states.
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
    ).map { Item(it, it.substringAfterLast(':')) }

    private fun item(id: String) = catalog.single { it.id == id }

    private fun normalize(vararg rows: Pair<String, Int>) =
        normalizePlacedBlocks(rows.associate { (id, amount) -> item(id) to amount }, catalog)

    @Test
    fun `a fluid becomes one bucket and keeps its cell count aside`() {
        val result = normalize("minecraft:water" to 4000, "minecraft:oak_planks" to 12)

        assertEquals(
            mapOf("minecraft:water_bucket" to 1, "minecraft:oak_planks" to 12),
            result.requirements.associate { it.first.id to it.second },
        )
        assertEquals(mapOf("minecraft:water_bucket" to 4000), result.placedCounts)
    }

    @Test
    fun `source and flowing cells count as the same fluid`() {
        val result = normalize("minecraft:water" to 900, "minecraft:flowing_water" to 13)

        assertEquals(1, result.requirements.single().second)
        assertEquals(mapOf("minecraft:water_bucket" to 913), result.placedCounts)
    }

    @Test
    fun `portals and air are dropped without a word`() {
        val result = normalize(
            "minecraft:nether_portal" to 24,
            "minecraft:air" to 9_389_854,
            "minecraft:oak_planks" to 5,
        )

        assertEquals(listOf("minecraft:oak_planks"), result.requirements.map { it.first.id })
        assertTrue(result.placedCounts.isEmpty())
    }

    @Test
    fun `a fluid whose bucket the version lacks is dropped rather than invented`() {
        val narrow = catalog.filterNot { it.id == "minecraft:powder_snow_bucket" }

        val result = normalizePlacedBlocks(
            mapOf(item("minecraft:powder_snow") to 2, item("minecraft:oak_planks") to 5),
            narrow,
        )

        assertEquals(listOf("minecraft:oak_planks"), result.requirements.map { it.first.id })
        assertNull(result.placedCounts["minecraft:powder_snow_bucket"])
    }

    @Test
    fun `an ordinary list passes through untouched`() {
        val result = normalize("minecraft:oak_planks" to 64)

        assertEquals(listOf("minecraft:oak_planks" to 64), result.requirements.map { it.first.id to it.second })
        assertTrue(result.placedCounts.isEmpty())
    }
}
