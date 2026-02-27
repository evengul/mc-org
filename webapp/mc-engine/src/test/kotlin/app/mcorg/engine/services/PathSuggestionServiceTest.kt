package app.mcorg.engine.services

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.domain.services.ItemSourceGraphQueries
import app.mcorg.domain.services.PathSuggestionService
import app.mcorg.domain.services.ProductionBranch
import app.mcorg.domain.services.ProductionTree
import app.mcorg.domain.services.SuggestionContext
import app.mcorg.engine.model.ItemNode
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.*

class PathSuggestionServiceTest {

    private val defaultContext = SuggestionContext(requiredAmount = 10)

    @Test
    fun `suggests path for simple recipe chain`() {
        // diamond_pickaxe requires diamonds + sticks via crafting
        val diamondTree = ProductionTree(
            ItemNode(Item("minecraft:diamond", "Diamond")),
            listOf(
                ProductionBranch(
                    source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "diamond_ore.json"),
                    requiredItems = emptyList()
                )
            )
        )
        val stickTree = ProductionTree(
            ItemNode(Item("minecraft:stick", "Stick")),
            listOf(
                ProductionBranch(
                    source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json"),
                    requiredItems = listOf(
                        ProductionTree(ItemNode(Item("minecraft:oak_planks", "Oak Planks")), emptyList())
                    )
                )
            )
        )
        val pickaxeTree = ProductionTree(
            ItemNode(Item("minecraft:diamond_pickaxe", "Diamond Pickaxe")),
            listOf(
                ProductionBranch(
                    source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "diamond_pickaxe.json"),
                    requiredItems = listOf(diamondTree, stickTree),
                    requiredQuantities = mapOf("minecraft:diamond" to 3, "minecraft:stick" to 2),
                    producedQuantity = 1
                )
            )
        )

        val path = PathSuggestionService.suggestPath(pickaxeTree, defaultContext)

        assertEquals("minecraft:diamond_pickaxe", path.item.id)
        assertNotNull(path.source)
        assertEquals("minecraft:crafting_shaped:diamond_pickaxe.json", path.source.sourceKey)
        assertEquals(2, path.source.requirements.size)

        // Diamond should have block loot selected
        val diamondPath = path.source.requirements.find { it.item.id == "minecraft:diamond" }
        assertNotNull(diamondPath)
        assertNotNull(diamondPath.source)
        assertEquals("minecraft:block:diamond_ore.json", diamondPath.source.sourceKey)

        // Stick should have crafting selected, with oak_planks as leaf
        val stickPath = path.source.requirements.find { it.item.id == "minecraft:stick" }
        assertNotNull(stickPath)
        assertNotNull(stickPath.source)
        assertEquals("minecraft:crafting_shaped:stick.json", stickPath.source.sourceKey)
        assertEquals(1, stickPath.source.requirements.size)
        assertNull(stickPath.source.requirements.first().source) // leaf
    }

    @Test
    fun `leaf item produces path with no source`() {
        val leafTree = ProductionTree(
            ItemNode(Item("minecraft:oak_log", "Oak Log")),
            emptyList()
        )

        val path = PathSuggestionService.suggestPath(leafTree, defaultContext)

        assertEquals("minecraft:oak_log", path.item.id)
        assertNull(path.source)
    }

    @Test
    fun `tag item is left unresolved`() {
        val tagTree = ProductionTree(
            ItemNode(
                MinecraftTag(
                    "minecraft:planks", "Planks", listOf(
                        Item("minecraft:oak_planks", "Oak Planks"),
                        Item("minecraft:birch_planks", "Birch Planks")
                    )
                )
            ),
            sources = emptyList(),
            tagMembers = listOf(
                ProductionTree(ItemNode(Item("minecraft:oak_planks", "Oak Planks")), emptyList()),
                ProductionTree(ItemNode(Item("minecraft:birch_planks", "Birch Planks")), emptyList())
            )
        )

        val path = PathSuggestionService.suggestPath(tagTree, defaultContext)

        assertEquals("minecraft:planks", path.item.id)
        assertNull(path.source, "Tag items should be left unresolved")
    }

    @Test
    fun `prefers recipe over loot for large quantities`() {
        val smeltingBranch = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.RecipeTypes.SMELTING, "iron_ingot.json"),
            requiredItems = listOf(
                ProductionTree(ItemNode(Item("minecraft:raw_iron", "Raw Iron")), emptyList())
            ),
            requiredQuantities = mapOf("minecraft:raw_iron" to 1),
            producedQuantity = 1
        )
        val chestBranch = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.LootTypes.CHEST, "stronghold.json"),
            requiredItems = emptyList()
        )

        val ironTree = ProductionTree(
            ItemNode(Item("minecraft:iron_ingot", "Iron Ingot")),
            listOf(smeltingBranch, chestBranch)
        )

        // With high required amount, recipe should be preferred
        val largeContext = SuggestionContext(requiredAmount = 500, recipeThreshold = 100)
        val path = PathSuggestionService.suggestPath(ironTree, largeContext)

        assertNotNull(path.source)
        assertEquals("minecraft:smelting:iron_ingot.json", path.source.sourceKey)
    }

    @Test
    fun `prefers source using already-produced items`() {
        val ironIngotTree = ProductionTree(ItemNode(Item("minecraft:iron_ingot", "Iron Ingot")), emptyList())
        val goldIngotTree = ProductionTree(ItemNode(Item("minecraft:gold_ingot", "Gold Ingot")), emptyList())

        val ironRecipe = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "iron_tool.json"),
            requiredItems = listOf(ironIngotTree),
            requiredQuantities = mapOf("minecraft:iron_ingot" to 3),
            producedQuantity = 1
        )
        val goldRecipe = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "gold_tool.json"),
            requiredItems = listOf(goldIngotTree),
            requiredQuantities = mapOf("minecraft:gold_ingot" to 3),
            producedQuantity = 1
        )

        val toolTree = ProductionTree(
            ItemNode(Item("minecraft:tool", "Tool")),
            listOf(ironRecipe, goldRecipe)
        )

        // Iron is already being produced in the world
        val context = SuggestionContext(
            requiredAmount = 10,
            worldProductions = setOf("minecraft:iron_ingot")
        )

        val path = PathSuggestionService.suggestPath(toolTree, context)

        assertNotNull(path.source)
        assertEquals("minecraft:crafting_shaped:iron_tool.json", path.source.sourceKey)
    }

    @Test
    fun `suggested path is complete for simple chains`() {
        val logTree = ProductionTree(
            ItemNode(Item("minecraft:oak_log", "Oak Log")),
            listOf(
                ProductionBranch(
                    source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "oak_log.json"),
                    requiredItems = emptyList()
                )
            )
        )
        val planksTree = ProductionTree(
            ItemNode(Item("minecraft:oak_planks", "Oak Planks")),
            listOf(
                ProductionBranch(
                    source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS, "oak_planks.json"),
                    requiredItems = listOf(logTree),
                    requiredQuantities = mapOf("minecraft:oak_log" to 1),
                    producedQuantity = 4
                )
            )
        )

        val path = PathSuggestionService.suggestPath(planksTree, defaultContext)

        assertTrue(path.isComplete(), "Suggested path should be complete")
        assertEquals(2, path.getAllItemIds().size) // oak_planks, oak_log (unique IDs)
    }

    @Test
    fun `prefers crafting over block loot for craftable items like beacon`() {
        // Beacon: can be "obtained" via block loot (break placed beacon) or crafted
        val glassTree = ProductionTree(ItemNode(Item("minecraft:glass", "Glass")), emptyList())
        val obsidianTree = ProductionTree(ItemNode(Item("minecraft:obsidian", "Obsidian")), emptyList())
        val netherStarTree = ProductionTree(ItemNode(Item("minecraft:nether_star", "Nether Star")), emptyList())

        val craftingBranch = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "beacon.json"),
            requiredItems = listOf(glassTree, obsidianTree, netherStarTree),
            requiredQuantities = mapOf("minecraft:glass" to 5, "minecraft:obsidian" to 3, "minecraft:nether_star" to 1),
            producedQuantity = 1
        )
        val blockLootBranch = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "beacon.json"),
            requiredItems = emptyList()
        )

        val beaconTree = ProductionTree(
            ItemNode(Item("minecraft:beacon", "Beacon")),
            listOf(craftingBranch, blockLootBranch)
        )

        val path = PathSuggestionService.suggestPath(beaconTree, defaultContext)

        assertNotNull(path.source)
        assertEquals("minecraft:crafting_shaped:beacon.json", path.source.sourceKey,
            "Should prefer crafting over block loot for a craftable item like beacon")
        assertEquals(3, path.source.requirements.size)
    }

    @Test
    fun `block loot is not penalized for naturally occurring blocks`() {
        // Diamond ore: only obtainable via block loot, no recipe
        val blockLootBranch = ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "diamond_ore.json"),
            requiredItems = emptyList()
        )

        val diamondOreTree = ProductionTree(
            ItemNode(Item("minecraft:diamond_ore", "Diamond Ore")),
            listOf(blockLootBranch)
        )

        val path = PathSuggestionService.suggestPath(diamondOreTree, defaultContext)

        assertNotNull(path.source)
        assertEquals("minecraft:block:diamond_ore.json", path.source.sourceKey,
            "Block loot should still be selected when there's no recipe alternative")
    }

    @Test
    fun `works with real graph data`() {
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks") to ResourceQuantity.ItemQuantity(2)),
                producedItems = listOf(Item("minecraft:stick", "Stick") to ResourceQuantity.ItemQuantity(4))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "oak_planks.json",
                requiredItems = listOf(Item("minecraft:oak_log", "Oak Log") to ResourceQuantity.ItemQuantity(1)),
                producedItems = listOf(Item("minecraft:oak_planks", "Oak Planks") to ResourceQuantity.ItemQuantity(4))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "oak_log.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:oak_log", "Oak Log") to ResourceQuantity.ItemQuantity(1))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
        val queries = ItemSourceGraphQueries(graph)

        val tree = queries.findProductionChain("minecraft:stick")?.deduplicated()
        assertNotNull(tree)

        val path = PathSuggestionService.suggestPath(tree, defaultContext)

        assertEquals("minecraft:stick", path.item.id)
        assertNotNull(path.source)
        assertTrue(path.isComplete())

        // Verify quantities propagated correctly
        val stickBranch = tree.sources.first()
        assertEquals(4, stickBranch.producedQuantity)
        assertEquals(mapOf("minecraft:oak_planks" to 2), stickBranch.requiredQuantities)
    }

    @Test
    fun `leaf node = tag is left unresolved`() {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sourcesRequiredForBeacon)
        val queries = ItemSourceGraphQueries(graph)

        val tree = queries.findProductionChain("minecraft:beacon")?.deduplicated()
        assertNotNull(tree)

        val path = PathSuggestionService.suggestPath(tree, defaultContext)

        assertEquals("minecraft:beacon", path.item.id)
        assertNotNull(path.source)
        assertTrue(path.isComplete())

        val glassPath = path.source.requirements.find { it.item.id == "minecraft:glass" }

        assertNotNull(glassPath)
        assertNotNull(glassPath.source)

        assertEquals(1, glassPath.source.requirements.size)
        val glassRequirement = glassPath.source.requirements.first()
        assertIs<MinecraftTag>(glassRequirement.item)
        assertContains(glassRequirement.item.content.map { it.id }, "minecraft:sand")
        assertContains(glassRequirement.item.content.map { it.id }, "minecraft:red_sand")
        assertNull(glassRequirement.source, "Tag with only leaf sources should be left unresolved - any member item works")
    }

    private val sourcesRequiredForBeacon: List<ResourceSource> = listOf(
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
                Item("minecraft:red_sand", "Red Sand") to ResourceQuantity.ItemQuantity(1),
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
