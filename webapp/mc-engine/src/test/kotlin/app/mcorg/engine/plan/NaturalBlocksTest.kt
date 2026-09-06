package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-527: breaking terrain is mining, and the model has to be able to tell.
 *
 * The gate that decides this used to ask *"does this block have a recipe"*, which is a question
 * about crafting rather than about the world. It is right for a beacon and wrong for terracotta,
 * and being wrong there removed mining a badlands as an option — so "should I mine or trade for
 * terracotta" was a comparison the planner could not hold.
 *
 * These pin the distinction rather than the contents of the snapshot: which blocks are listed is
 * derived from the jar and will move between versions, but *what the list is for* must not.
 */
class NaturalBlocksTest {

    private fun item(id: String) = Item("minecraft:$id", id)

    private fun blockLoot(name: String) =
        SourceNode.fromKey("${ResourceSource.SourceType.LootTypes.BLOCK.id}:blocks/$name.json")

    // ── the snapshot ────────────────────────────────────────────────────────

    @Test
    fun `terrain is what the ground is made of`() {
        for (block in listOf("stone", "deepslate", "sand", "gravel", "dirt", "terracotta")) {
            assertTrue(
                NaturalBlocks.isNatural("minecraft:$block"),
                "$block is terrain and must read as naturally generated",
            )
        }
    }

    @Test
    fun `things a player builds are not natural`() {
        // If any of these ever reads as natural, the self-block-loot gate stops protecting
        // against "break the thing you crafted" and the planner will happily suggest it.
        for (block in listOf("beacon", "bookshelf", "iron_block", "diamond_block", "cauldron")) {
            assertFalse(
                NaturalBlocks.isNatural("minecraft:$block"),
                "$block is only ever placed by a player",
            )
        }
    }

    @Test
    fun `structure decoration is not natural, however much of it a village has`() {
        // The discriminator is "does a biome ask for this feature", not "is it in
        // configured_feature". `pile_hay` and `pile_pumpkin` are village dressing: they live in
        // the feature directory but no biome lists them.
        //
        // Getting this wrong is not cosmetic. With hay bales reading as natural, wheat cost
        // 0.01 min — break a hay block, unpack it into nine wheat — which is MCO-317's
        // "route a common item through an unpack chain" arriving in a new place.
        assertFalse(NaturalBlocks.isNatural("minecraft:hay_block"), "hay is village dressing")
        assertFalse(NaturalBlocks.isNatural("minecraft:jack_o_lantern"), "so is a carved pumpkin")

        // ...while a block a wild feature really does place stays natural. A crimson forest is
        // built out of nether wart blocks.
        assertTrue(NaturalBlocks.isNatural("minecraft:nether_wart_block"))
    }

    // ── the gate ────────────────────────────────────────────────────────────

    @Test
    fun `mining terracotta is not re-collecting a placed block`() {
        // The case this was filed for. Terracotta has a matching stem *and* a recipe, which is
        // exactly the combination the old rule caught, and a badlands is made of the stuff.
        assertFalse(isSelfBlockLoot(item("terracotta"), blockLoot("terracotta")))
    }

    @Test
    fun `breaking a beacon still is`() {
        assertTrue(isSelfBlockLoot(item("beacon"), blockLoot("beacon")))
    }

    @Test
    fun `naturalness is asked before the name and the table`() {
        // Ordering matters: `stone` matches its own stem, so a name comparison alone would call
        // it re-collection. It is only saved by being asked about the world first.
        assertFalse(isSelfBlockLoot(item("stone"), blockLoot("stone")))
        assertFalse(isSelfBlockLoot(item("sand"), blockLoot("sand")))
    }
}
