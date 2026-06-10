package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.TestUtils
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractVillagerTradesStepTest : ServerFileTest(
    MinecraftVersionRange.LowerBounded(
        from = MinecraftVersion.fromString("26.1")
    )
) {
    @ParameterizedTest
    @MethodSource("getVersions")
    fun extractsTradesFor26_1AndLater(version: MinecraftVersion.Release) {
        val trades = TestUtils.executeAndAssertSuccess(
            ExtractVillagerTradesStep,
            contextFor(version)
        )

        // 26.1.1 ships 387 trade JSON files across 15 professions.
        assertTrue(
            trades.size >= 380,
            "Expected at least 380 trades for $version, got ${trades.size}"
        )

        // Every trade must have exactly one produced item and at least one required item.
        trades.forEach { trade ->
            assertEquals(1, trade.producedItems.size, "Trade ${trade.filename} should produce one item")
            assertTrue(
                trade.requiredItems.isNotEmpty(),
                "Trade ${trade.filename} should have at least one required item"
            )
            assertTrue(
                trade.type.isTrade(),
                "Trade ${trade.filename} should have a TradeTypes source type, got ${trade.type}"
            )
        }

        // Spot-check: farmer level 1 wheat → emerald trade.
        val wheatEmerald = trades.firstOrNull { it.filename == "farmer/1/wheat_emerald.json" }
        assertNotNull(wheatEmerald, "Expected farmer/1/wheat_emerald.json trade to be present")
        assertEquals(ResourceSource.SourceType.TradeTypes.FARMER, wheatEmerald.type)
        assertEquals(1, wheatEmerald.requiredItems.size)
        val (wantedItem, wantedQty) = wheatEmerald.requiredItems.single()
        assertEquals("minecraft:wheat", (wantedItem as Item).id)
        assertEquals(ResourceQuantity.ItemQuantity(20), wantedQty)
        val (givenItem, givenQty) = wheatEmerald.producedItems.single()
        assertEquals("minecraft:emerald", (givenItem as Item).id)
        assertEquals(ResourceQuantity.ItemQuantity(1), givenQty)

        // Spot-check: librarian enchanted-book trade uses RuntimeCalculation for the wants price
        // and has an additional_wants book requirement.
        val enchantedBook = trades.firstOrNull {
            it.filename.endsWith("librarian/1/emerald_and_book_enchanted_book.json")
        }
        assertNotNull(
            enchantedBook,
            "Expected librarian/1/emerald_and_book_enchanted_book.json trade to be present"
        )
        assertEquals(ResourceSource.SourceType.TradeTypes.LIBRARIAN, enchantedBook.type)
        assertEquals(
            2, enchantedBook.requiredItems.size,
            "Enchanted-book trade should have both wants (emerald) and additional_wants (book)"
        )
        val emeraldEntry = enchantedBook.requiredItems.first { (item, _) ->
            (item as? Item)?.id == "minecraft:emerald"
        }
        assertEquals(ResourceQuantity.RuntimeCalculation, emeraldEntry.second)
        val bookEntry = enchantedBook.requiredItems.first { (item, _) ->
            (item as? Item)?.id == "minecraft:book"
        }
        assertEquals(ResourceQuantity.ItemQuantity(1), bookEntry.second)
        val (enchantedOutput, _) = enchantedBook.producedItems.single()
        assertEquals("minecraft:enchanted_book", (enchantedOutput as Item).id)

        // Spot-check: wandering trader trades resolve to the WANDERING_TRADER source type.
        val wanderingTrade = trades.firstOrNull { it.filename.startsWith("wandering_trader/") }
        assertNotNull(wanderingTrade, "Expected at least one wandering trader trade")
        assertEquals(ResourceSource.SourceType.TradeTypes.WANDERING_TRADER, wanderingTrade.type)
    }

    @Test
    fun `returns empty list for pre-26_1 versions where villager_trade directory does not exist`() {
        // 1.21.11 does not ship data/minecraft/villager_trade/ — the step should treat that as
        // "no trades" rather than failing.
        val version = MinecraftVersion.Release(1, 21, 11)
        val trades = TestUtils.executeAndAssertSuccess(
            ExtractVillagerTradesStep,
            contextFor(version)
        )
        assertTrue(trades.isEmpty(), "Expected no trades for pre-26.1 version $version, got ${trades.size}")
    }
}
