package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.domain.services.ItemSourceGraphBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-564: a wither rose cost three seconds, because breaking a *potted* one counted as finding
 * one.
 *
 * 1.21.4 has two sources for `minecraft:wither_rose`, both block loot: the placed rose and the
 * potted rose. The potted one escaped the self-block-loot gate (its stem is not the item's name),
 * and the plain one was priced as a bare swing although the block only exists where a Wither has
 * killed something. These pin both halves: the pot never wins, and the rose costs the Wither.
 */
class PottedPlantTest {

    private fun item(name: String) = Item("minecraft:$name", name)

    private fun blockLoot(block: String, drops: String) = ResourceSource(
        type = SourceType.LootTypes.BLOCK,
        filename = "blocks/$block.json",
        producedItems = listOf(item(drops) to ResourceQuantity.ItemQuantity(1)),
    )

    private fun model(vararg sources: ResourceSource) =
        UnitCostModel(ItemSourceGraphBuilder.buildFromResourceSources(sources.toList()))

    @Test
    fun `a wither rose comes from the rose a Wither left, not from a pot`() {
        val model = model(
            blockLoot("wither_rose", "wither_rose"),
            blockLoot("potted_wither_rose", "wither_rose"),
        )

        assertEquals("minecraft:block:blocks/wither_rose.json", model.best(item("wither_rose"))?.getKey())
    }

    @Test
    fun `a wither rose costs minutes of Wither, not one swing`() {
        val model = model(
            blockLoot("wither_rose", "wither_rose"),
            blockLoot("potted_wither_rose", "wither_rose"),
        )
        val rose = model.cost.getValue("minecraft:wither_rose")

        assertTrue(rose > 5.0, "a rose needs a summoned Wither behind it, was $rose min")
    }

    @Test
    fun `a flower that grows in the world is still picked by breaking it`() {
        // The guard on the other side: poppies generate as vegetation, so breaking one is how
        // anyone gets one, at the bare swing — and the pot is no cheaper a way to the same flower.
        val model = model(
            blockLoot("poppy", "poppy"),
            blockLoot("potted_poppy", "poppy"),
        )

        assertEquals("minecraft:block:blocks/poppy.json", model.best(item("poppy"))?.getKey())
        assertEquals(EffortTable.DEFAULT.of(SourceType.LootTypes.BLOCK), model.cost.getValue("minecraft:poppy"), 1e-12)
    }
}
