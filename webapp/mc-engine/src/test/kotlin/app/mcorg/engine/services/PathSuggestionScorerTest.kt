package app.mcorg.engine.services

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.PathSuggestionScorer
import app.mcorg.domain.services.ProductionBranch
import app.mcorg.domain.services.ProductionTree
import app.mcorg.domain.services.SuggestionContext
import app.mcorg.engine.model.ItemNode
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathSuggestionScorerTest {

    private val defaultContext = SuggestionContext(requiredAmount = 10)

    private fun leafBranch(sourceType: ResourceSource.SourceType, filename: String = "test.json"): ProductionBranch {
        return ProductionBranch(
            source = SourceNode(sourceType, filename),
            requiredItems = emptyList()
        )
    }

    private fun recipeBranch(
        requiredItems: List<ProductionTree> = emptyList(),
        requiredQuantities: Map<String, Int> = emptyMap(),
        producedQuantity: Int = 1
    ): ProductionBranch {
        return ProductionBranch(
            source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "recipe.json"),
            requiredItems = requiredItems,
            requiredQuantities = requiredQuantities,
            producedQuantity = producedQuantity
        )
    }

    @Test
    fun `higher base score source type scores higher`() {
        val craftingBranch = leafBranch(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED) // score=95
        val chestBranch = leafBranch(ResourceSource.SourceType.LootTypes.CHEST) // score=60

        val craftingScore = PathSuggestionScorer.score(craftingBranch, defaultContext)
        val chestScore = PathSuggestionScorer.score(chestBranch, defaultContext)

        assertTrue(craftingScore > chestScore, "Crafting ($craftingScore) should score higher than chest ($chestScore)")
    }

    @Test
    fun `more requirements reduce score`() {
        val noReqs = recipeBranch()
        val withReqs = recipeBranch(
            requiredItems = listOf(
                ProductionTree(ItemNode(Item("minecraft:diamond", "Diamond")), emptyList()),
                ProductionTree(ItemNode(Item("minecraft:stick", "Stick")), emptyList())
            )
        )

        val noReqsScore = PathSuggestionScorer.score(noReqs, defaultContext)
        val withReqsScore = PathSuggestionScorer.score(withReqs, defaultContext)

        assertTrue(noReqsScore > withReqsScore, "No requirements ($noReqsScore) should score higher than with requirements ($withReqsScore)")
    }

    @Test
    fun `higher efficiency produces higher score`() {
        // 4 planks from 1 log = ratio 4.0
        val efficientBranch = recipeBranch(
            requiredItems = listOf(
                ProductionTree(ItemNode(Item("minecraft:oak_log", "Oak Log")), emptyList())
            ),
            requiredQuantities = mapOf("minecraft:oak_log" to 1),
            producedQuantity = 4
        )

        // 1 stick from 2 planks = ratio 0.5
        val inefficientBranch = recipeBranch(
            requiredItems = listOf(
                ProductionTree(ItemNode(Item("minecraft:oak_planks", "Oak Planks")), emptyList())
            ),
            requiredQuantities = mapOf("minecraft:oak_planks" to 2),
            producedQuantity = 1
        )

        val efficientScore = PathSuggestionScorer.score(efficientBranch, defaultContext)
        val inefficientScore = PathSuggestionScorer.score(inefficientBranch, defaultContext)

        assertTrue(efficientScore > inefficientScore, "Efficient ($efficientScore) should score higher than inefficient ($inefficientScore)")
    }

    @Test
    fun `world productions boost branches that use already-farmed items`() {
        val ironIngotTree = ProductionTree(ItemNode(Item("minecraft:iron_ingot", "Iron Ingot")), emptyList())

        val branchUsingIron = recipeBranch(requiredItems = listOf(ironIngotTree))

        val withProductions = SuggestionContext(
            requiredAmount = 10,
            worldProductions = setOf("minecraft:iron_ingot")
        )
        val withoutProductions = SuggestionContext(
            requiredAmount = 10,
            worldProductions = emptySet()
        )

        val boostedScore = PathSuggestionScorer.score(branchUsingIron, withProductions)
        val normalScore = PathSuggestionScorer.score(branchUsingIron, withoutProductions)

        assertTrue(boostedScore > normalScore, "With productions ($boostedScore) should score higher than without ($normalScore)")
    }

    @Test
    fun `recipe threshold bonus only applies above threshold`() {
        val craftingBranch = leafBranch(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED)
        val blockBranch = leafBranch(ResourceSource.SourceType.LootTypes.BLOCK)

        val belowThreshold = SuggestionContext(requiredAmount = 50, recipeThreshold = 100)
        val aboveThreshold = SuggestionContext(requiredAmount = 200, recipeThreshold = 100)

        val craftingBelow = PathSuggestionScorer.score(craftingBranch, belowThreshold)
        val craftingAbove = PathSuggestionScorer.score(craftingBranch, aboveThreshold)
        val blockBelow = PathSuggestionScorer.score(blockBranch, belowThreshold)
        val blockAbove = PathSuggestionScorer.score(blockBranch, aboveThreshold)

        // Below threshold, no recipe bonus is applied
        assertTrue(craftingBelow - blockBelow < craftingAbove - blockAbove,
            "Recipe advantage should be larger above threshold")
    }

    @Test
    fun `loot source with no requirements scores base score only`() {
        val lootBranch = leafBranch(ResourceSource.SourceType.LootTypes.BLOCK)
        val score = PathSuggestionScorer.score(lootBranch, defaultContext)

        // Block loot has base score 100, no penalties or bonuses
        assertEquals(score, 100, "Leaf loot branch should score base score (100), got $score")
    }

    @Test
    fun `block loot is heavily penalized when recipe alternative exists`() {
        val blockBranch = leafBranch(ResourceSource.SourceType.LootTypes.BLOCK)

        val withoutRecipeAlt = PathSuggestionScorer.score(blockBranch, defaultContext, hasRecipeAlternative = false)
        val withRecipeAlt = PathSuggestionScorer.score(blockBranch, defaultContext, hasRecipeAlternative = true)

        assertTrue(withoutRecipeAlt > withRecipeAlt,
            "Block loot without recipe alt ($withoutRecipeAlt) should score much higher than with ($withRecipeAlt)")
    }

    @Test
    fun `circular block loot penalty does not affect non-block loot`() {
        val entityBranch = leafBranch(ResourceSource.SourceType.LootTypes.ENTITY)
        val chestBranch = leafBranch(ResourceSource.SourceType.LootTypes.CHEST)

        val entityWithout = PathSuggestionScorer.score(entityBranch, defaultContext, hasRecipeAlternative = false)
        val entityWith = PathSuggestionScorer.score(entityBranch, defaultContext, hasRecipeAlternative = true)
        val chestWithout = PathSuggestionScorer.score(chestBranch, defaultContext, hasRecipeAlternative = false)
        val chestWith = PathSuggestionScorer.score(chestBranch, defaultContext, hasRecipeAlternative = true)

        assertTrue(entityWithout == entityWith, "Entity loot should not be penalized, got $entityWithout vs $entityWith")
        assertTrue(chestWithout == chestWith, "Chest loot should not be penalized, got $chestWithout vs $chestWith")
    }

    @Test
    fun `crafting recipe beats block loot when recipe alternative exists`() {
        val craftingBranch = recipeBranch(
            requiredItems = listOf(
                ProductionTree(ItemNode(Item("minecraft:glass", "Glass")), emptyList()),
                ProductionTree(ItemNode(Item("minecraft:obsidian", "Obsidian")), emptyList()),
                ProductionTree(ItemNode(Item("minecraft:nether_star", "Nether Star")), emptyList())
            )
        )
        val blockBranch = leafBranch(ResourceSource.SourceType.LootTypes.BLOCK)

        val craftingScore = PathSuggestionScorer.score(craftingBranch, defaultContext, hasRecipeAlternative = true)
        val blockScore = PathSuggestionScorer.score(blockBranch, defaultContext, hasRecipeAlternative = true)

        assertTrue(craftingScore > blockScore,
            "Crafting ($craftingScore) should beat block loot ($blockScore) when recipe alternative exists")
    }
}
