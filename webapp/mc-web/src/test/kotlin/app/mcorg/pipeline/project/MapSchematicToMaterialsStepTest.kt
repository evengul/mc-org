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
        "minecraft:carrot",
        "minecraft:dirt",
        "minecraft:redstone",
        "minecraft:string",
        "minecraft:torch",
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
    fun `crop blocks redirect to their produce item`() {
        val byId = map(mapOf("minecraft:carrots" to 6))
        assertEquals(6, byId["minecraft:carrot"])
        assertNull(byId["minecraft:carrots"], "the crop block id must not survive")
    }

    @Test
    fun `placed tool forms resolve to their material and merge`() {
        val byId = map(mapOf("minecraft:dirt_path" to 8, "minecraft:farmland" to 4))
        assertEquals(12, byId["minecraft:dirt"], "both resolve to dirt and sum")
    }

    @Test
    fun `placed effect forms resolve to their material`() {
        val byId = map(mapOf("minecraft:redstone_wire" to 3, "minecraft:tripwire" to 2, "minecraft:wall_torch" to 7))
        assertEquals(3, byId["minecraft:redstone"])
        assertEquals(2, byId["minecraft:string"])
        assertEquals(7, byId["minecraft:torch"], "bare wall_torch has no _wall_ infix and needs the redirect")
    }

    @Test
    fun `potted plants, candle cakes and infested blocks are dropped`() {
        val byId = map(
            mapOf(
                "minecraft:potted_oak_sapling" to 2,
                "minecraft:black_candle_cake" to 1,
                "minecraft:infested_stone" to 5,
                "minecraft:oak_planks" to 9,
            )
        )
        assertNull(byId["minecraft:potted_oak_sapling"])
        assertNull(byId["minecraft:black_candle_cake"])
        assertNull(byId["minecraft:infested_stone"])
        assertEquals(9, byId["minecraft:oak_planks"], "real materials still pass through")
    }

    @Test
    fun `a schematic of only unmappable blocks fails validation`() = runBlocking {
        val result = MapSchematicToMaterialsStep(catalog).process(litematica(mapOf("minecraft:piston_head" to 1)))
        assertTrue(result is Result.Failure)
    }
}
