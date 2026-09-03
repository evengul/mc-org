package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.PlacedForm
import app.mcorg.domain.model.minecraft.PlacedForms
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Breaking a placed form is re-collection, not acquisition — and the block is not always named
 * after the item you placed.
 *
 * `isSelfBlockLoot` compared the loot-table stem to the item id, which caught
 * `blocks/beacon.json` for a beacon and missed `blocks/redstone_wire.json` for redstone. The
 * cost model then routed redstone through breaking placed wire, because 0.05 minutes beats
 * mining deepslate redstone ore and nothing said the route was circular.
 *
 * The fix asks [PlacedForms] instead, which is the same table the import door reads — and the
 * relation matters, which is why these tests care about the cases that must *not* be caught.
 */
class PlacedFormCircularityTest {

    private fun block(name: String) =
        SourceNode.fromKey("${SourceType.LootTypes.BLOCK.id}:blocks/$name.json")

    private fun item(id: String) = Item("minecraft:$id", id)

    @Test
    fun `breaking placed redstone dust is not a way to obtain redstone`() {
        assertTrue(SelectionScorer.isSelfBlockLoot(item("redstone"), block("redstone_wire")))
    }

    @Test
    fun `the name-matched case still works`() {
        assertTrue(SelectionScorer.isSelfBlockLoot(item("beacon"), block("beacon")))
    }

    @Test
    fun `harvesting a crop is production, not re-collection`() {
        // A seed goes in and more than a seed comes out. Treating this as circular is how a
        // planner ends up sourcing wheat from a shipwreck.
        assertFalse(SelectionScorer.isSelfBlockLoot(item("carrot"), block("carrots")))
        assertFalse(SelectionScorer.isSelfBlockLoot(item("sweet_berries"), block("sweet_berry_bush")))
    }

    @Test
    fun `a block needing a tool between item and placement is not re-collection`() {
        // Dirt cannot become farmland without a hoe, so breaking farmland really is a way to
        // end up holding dirt — even though farmland is plainly "a placed form of" it.
        assertFalse(SelectionScorer.isSelfBlockLoot(item("dirt"), block("farmland")))
        assertFalse(SelectionScorer.isSelfBlockLoot(item("cauldron"), block("water_cauldron")))
        assertFalse(SelectionScorer.isSelfBlockLoot(item("sand"), block("suspicious_sand")))
    }

    @Test
    fun `a block that merely contains the item is not a placed form of it`() {
        // An ender chest is *made of* obsidian and a bookshelf *of* books, which is a different
        // relation entirely — the ingredient closure, not the placed form. Keeping them out of
        // this table is deliberate: books from a naturally generated bookshelf are a normal way
        // to get books, and obsidian's real answer is a lava-and-water source the graph lacks.
        assertFalse(SelectionScorer.isSelfBlockLoot(item("obsidian"), block("ender_chest")))
        assertFalse(SelectionScorer.isSelfBlockLoot(item("book"), block("bookshelf")))
    }

    @Test
    fun `every reversible entry really is reversible both ways`() {
        // Guard on the table itself: a REVERSIBLE entry claims placing the item makes the block
        // AND breaking it returns the same amount. Anything added here that only holds one way
        // belongs in HARVEST_ONLY, and getting that wrong bans a legitimate gather.
        val reversible = PlacedForms.ALL.filter { it.relation == PlacedForm.Relation.REVERSIBLE }
        assertTrue(reversible.isNotEmpty(), "the table would otherwise be silently inert")
        assertTrue(
            reversible.all { it.blockId != it.itemId },
            "a same-name entry is already handled by the stem comparison and needs no row"
        )
    }
}
