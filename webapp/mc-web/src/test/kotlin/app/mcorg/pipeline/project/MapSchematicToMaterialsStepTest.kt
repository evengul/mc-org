package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapSchematicToMaterialsStepTest {

    private val catalog = listOf(
        "minecraft:birch_sign",
        "minecraft:dead_horn_coral_fan",
        "minecraft:redstone_torch",
        "minecraft:piston",
        "minecraft:cobblestone_wall",
        "minecraft:oak_planks",
    ).map { Item(it, it.substringAfterLast(':')) }

    private fun litematica(items: Map<String, Int>) =
        Litematica("Build", "", "", Triple(1, 1, 1), items)

    private fun map(items: Map<String, Int>): Map<String, Int> = runBlocking {
        val result = MapSchematicToMaterialsStep(catalog).process(litematica(items))
        assertIs<Result.Success<List<Pair<Item, Int>>>>(result)
        result.value.associate { it.first.id to it.second }
    }

    @Test
    fun `wall sign resolves to the base sign item`() {
        val byId = map(mapOf("minecraft:birch_wall_sign" to 3))
        assertEquals(3, byId["minecraft:birch_sign"])
        assertNull(byId["minecraft:birch_wall_sign"], "the wall block id must not survive")
    }

    @Test
    fun `coral wall fan and wall torch resolve to their base items`() {
        val byId = map(
            mapOf(
                "minecraft:dead_horn_coral_wall_fan" to 2,
                "minecraft:redstone_wall_torch" to 5,
            )
        )
        assertEquals(2, byId["minecraft:dead_horn_coral_fan"])
        assertEquals(5, byId["minecraft:redstone_torch"])
    }

    @Test
    fun `technical blocks with no material are dropped`() {
        val byId = map(mapOf("minecraft:piston_head" to 4, "minecraft:oak_planks" to 10))
        assertNull(byId["minecraft:piston_head"])
        assertNull(byId["minecraft:piston"], "an extended head must not be counted as a piston")
        assertEquals(10, byId["minecraft:oak_planks"])
    }

    @Test
    fun `wall variant amounts merge with the base item already present`() {
        val byId = map(mapOf("minecraft:birch_sign" to 4, "minecraft:birch_wall_sign" to 3))
        assertEquals(7, byId["minecraft:birch_sign"])
    }

    @Test
    fun `a real block ending in wall is unaffected`() {
        // cobblestone_wall has "_wall" as a suffix, not the "_wall_" infix, so it is a
        // real item and resolves to itself.
        val byId = map(mapOf("minecraft:cobblestone_wall" to 12))
        assertEquals(12, byId["minecraft:cobblestone_wall"])
    }

    @Test
    fun `blocks absent from the version are dropped`() {
        val byId = map(mapOf("minecraft:some_future_block" to 9, "minecraft:oak_planks" to 1))
        assertNull(byId["minecraft:some_future_block"])
        assertEquals(1, byId["minecraft:oak_planks"])
    }

    @Test
    fun `a schematic of only unmappable blocks fails validation`() = runBlocking {
        val result = MapSchematicToMaterialsStep(catalog).process(litematica(mapOf("minecraft:piston_head" to 1)))
        assertTrue(result is Result.Failure)
    }
}
