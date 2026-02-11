package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.domain.services.ItemSourceGraphQueries
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HandleSaveResourcePathTest {
    @Test
    fun createBeaconPath() {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(resourceSources)
        val queries = ItemSourceGraphQueries(graph)

        val beaconChain = queries.findProductionChain("minecraft:beacon")

        assertNotNull(beaconChain)
        assertEquals(2, beaconChain.sources.size)

        val blockBranch = beaconChain.sources.find { it.source.getKey() == "minecraft:block:beacon.json" }
        val craftingBranch = beaconChain.sources.find { it.source.getKey() == "minecraft:crafting_shaped:beacon.json" }

        assertNotNull(blockBranch)
        assertEquals(0, blockBranch.requiredItems.size)

        assertNotNull(craftingBranch)
        assertEquals(3, craftingBranch.requiredItems.size)

        val glassTree = craftingBranch.requiredItems.find { it.targetItem.itemId == "minecraft:glass" }
        val netherStarTree = craftingBranch.requiredItems.find { it.targetItem.itemId == "minecraft:nether_star" }
        val obsidianTree = craftingBranch.requiredItems.find { it.targetItem.itemId == "minecraft:obsidian" }

        assertNotNull(glassTree)
        assertEquals(0, glassTree.sources.size)
        assertEquals(2, glassTree.tagMembers.size)

        assertNotNull(netherStarTree)
        assertEquals(1, netherStarTree.sources.size)
        assertEquals(0, netherStarTree.tagMembers.size)

        val netherStarSource = netherStarTree.sources.first()
        assertEquals(0, netherStarSource.requiredItems.size)

        assertNotNull(obsidianTree)
        assertEquals(2, obsidianTree.sources.size)
        assertEquals(0, obsidianTree.tagMembers.size)


        val obsidianBarterSource = obsidianTree.sources.find { it.source.getKey() == "minecraft:barter:barter.json" }
        assertNotNull(obsidianBarterSource)
        assertEquals(0, obsidianBarterSource.requiredItems.size)

        val obsidianBlockSource = obsidianTree.sources.find { it.source.getKey() == "minecraft:block:obsidian.json" }
        assertNotNull(obsidianBlockSource)
        assertEquals(0, obsidianBlockSource.requiredItems.size)
    }

    @Test
    fun buildPlanFromGraphSelections() {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(resourceSources)
        val queries = ItemSourceGraphQueries(graph)

        val beaconChain = queries.findProductionChain("minecraft:beacon")
        assertNotNull(beaconChain)

        // Step 1: Select crafting as the source for beacon
        var path = ProductionPath(item = Item("minecraft:beacon", "Beacon"))
            .selectSourceForItem("minecraft:beacon", "minecraft:crafting_shaped:beacon.json", beaconChain)

        assertEquals("minecraft:beacon", path.item.id)
        assertNotNull(path.source)
        assertEquals("minecraft:crafting_shaped:beacon.json", path.source.sourceKey)
        // Crafting beacon requires glass, nether_star, obsidian - populated from tree
        assertEquals(3, path.source.requirements.size)

        // Step 2: Select smelting for glass (a requirement of the crafting source)
        path = path.selectSourceForItem("minecraft:glass", "minecraft:smelting:glass.json", beaconChain)

        // Glass should now be a requirement nested inside beacon's source
        val glassReq = path.source!!.requirements.find { it.item.id == "minecraft:glass" }
        assertNotNull(glassReq)
        assertEquals("minecraft:smelting:glass.json", glassReq.source?.sourceKey)
        // Smelting glass requires sand - populated from tree
        assertNotEquals(0, glassReq.source?.requirements?.size ?: 0)

        // Step 3: Select entity drop for nether_star
        path = path.selectSourceForItem("minecraft:nether_star", "minecraft:entity:wither.json", beaconChain)

        val netherStarReq = path.source!!.requirements.find { it.item.id == "minecraft:nether_star" }
        assertNotNull(netherStarReq)
        assertEquals("minecraft:entity:wither.json", netherStarReq.source?.sourceKey)
        // Wither drop has no requirements
        assertEquals(0, netherStarReq.source!!.requirements.size)

        // Step 4: Select block for obsidian
        path = path.selectSourceForItem("minecraft:obsidian", "minecraft:block:obsidian.json", beaconChain)

        val obsidianReq = path.source!!.requirements.find { it.item.id == "minecraft:obsidian" }
        assertNotNull(obsidianReq)
        assertEquals("minecraft:block:obsidian.json", obsidianReq.source?.sourceKey)
        // Block drop has no requirements
        assertEquals(0, obsidianReq.source!!.requirements.size)

        // Verify overall structure
        assertEquals(3, path.source.requirements.size)
        // beacon, glass, nether_star, obsidian + sand (from smelting glass)
        assertEquals(5, path.getAllItemIds().size)
        assertEquals(4, path.countDecisions()) // beacon + glass + nether_star + obsidian
        assertTrue(path.isComplete())
    }

    private val resourceSources: List<ResourceSource> = listOf(
        ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
            filename = "beacon.json",
            requiredItems = listOf(
                MinecraftTag("minecraft:glass", "Glass", listOf(Item("minecraft:glass", "Glass"), Item("minecraft:white_glass", "White Glass"))) to ResourceQuantity.ItemQuantity(5),
                Item("minecraft:nether_star", "Nether Star") to ResourceQuantity.ItemQuantity(1),
                Item("minecraft:obsidian", "Obsidian") to ResourceQuantity.ItemQuantity(3)
            ),
            producedItems = listOf(
                Item("minecraft:beacon", "Beacon") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "beacon.json",
            producedItems = listOf(
                Item("minecraft:beacon", "Beacon") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.SMELTING,
            filename = "glass.json",
            requiredItems = listOf(
                MinecraftTag("minecraft:sand", "Sand", listOf(Item("minecraft:sand", "Sand"), Item("minecraft:red_sand", "Red Sand"))) to ResourceQuantity.ItemQuantity(1),
            ),
            producedItems = listOf(
                Item("minecraft:glass", "Glass") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "sand.json",
            producedItems = listOf(
                Item("minecraft:sand", "Sand") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "red_sand.json",
            producedItems = listOf(
                Item("minecraft:sand", "Red Sand") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "glass.json",
            producedItems = listOf(
                Item("minecraft:glass", "Glass") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "white_glass.json",
            producedItems = listOf(
                Item("minecraft:white_glass", "White Glass") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BARTER,
            filename = "barter.json",
            producedItems = listOf(
                Item("minecraft:obsidian", "Obsidian") to ResourceQuantity.ItemQuantity(1)
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.BLOCK,
            filename = "obsidian.json",
            producedItems = listOf(
                Item("minecraft:obsidian", "Obsidian") to ResourceQuantity.ItemQuantity(1),
            )
        ),
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.ENTITY,
            filename = "wither.json",
            producedItems = listOf(
                Item("minecraft:nether_star", "Nether Star") to ResourceQuantity.ItemQuantity(1),
            )
        )
    )

}