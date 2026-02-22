package app.mcorg.domain.services

import app.mcorg.domain.model.resources.ResourceSource

/**
 * Context for auto-suggestion scoring.
 *
 * @param requiredAmount how many of the root item are needed (from resource_gathering.required)
 * @param worldProductions all productions in the world (items already being farmed)
 * @param recipeThreshold above this quantity, recipes are strongly preferred over loot sources
 */
data class SuggestionContext(
    val requiredAmount: Int,
    val worldProductions: Set<String> = emptySet(),
    val recipeThreshold: Int = 100
)

/**
 * Scores ProductionBranch alternatives for auto-suggestion.
 *
 * Scoring factors (all additive):
 * 1. Source type base score (from SourceType.score)
 * 2. Resource efficiency bonus (output / input ratio)
 * 3. World productions bonus (ingredients already being farmed)
 * 4. Recipe-vs-loot threshold (recipes preferred for large quantities)
 * 5. Depth/complexity penalty
 */
object PathSuggestionScorer {

    private const val EFFICIENCY_WEIGHT = 20
    private const val PRODUCTION_BONUS = 30
    private const val RECIPE_THRESHOLD_BONUS = 50
    private const val CIRCULAR_BLOCK_LOOT_PENALTY = 200
    private const val DEPTH_PENALTY = 5
    private const val REQUIREMENT_PENALTY = 10

    /**
     * Score a production branch in the context of auto-suggestion.
     *
     * @param branch the branch to score
     * @param context suggestion context with required amount and world productions
     * @param hasRecipeAlternative true if the same item has at least one recipe source as a sibling.
     *   When true, block loot is heavily penalized because it likely represents "break what you crafted"
     *   rather than mining a naturally occurring block.
     * @return integer score (higher is better)
     */
    fun score(branch: ProductionBranch, context: SuggestionContext, hasRecipeAlternative: Boolean = false): Int {
        var total = 0

        // 1. Base source type score
        total += branch.source.sourceType.score

        // 2. Resource efficiency bonus
        total += efficiencyBonus(branch)

        // 3. World productions bonus - boost if required ingredients are already farmed
        total += worldProductionsBonus(branch, context.worldProductions)

        // 4. Recipe-vs-loot threshold
        total += recipeThresholdBonus(branch, context.requiredAmount, context.recipeThreshold)

        // 5. Circular block loot penalty: if this item can be crafted, its block loot
        //    is almost certainly "break what you placed" (e.g. beacon, redstone lamp),
        //    not a naturally occurring block (e.g. diamond ore, oak log).
        total -= circularBlockLootPenalty(branch, hasRecipeAlternative)

        // 6. Depth/complexity penalty
        total -= branch.requiredItems.size * REQUIREMENT_PENALTY
        total -= (branch.requiredItems.maxOfOrNull { it.getDepth() } ?: 0) * DEPTH_PENALTY

        return total
    }

    private fun efficiencyBonus(branch: ProductionBranch): Int {
        val totalInput = branch.requiredQuantities.values.sum()
        if (totalInput == 0) return 0
        val ratio = branch.producedQuantity.toDouble() / totalInput
        // Bonus scales: ratio 1.0 = 0 bonus, ratio 4.0 (e.g. logs->planks) = 60 bonus
        return ((ratio - 1.0) * EFFICIENCY_WEIGHT).toInt().coerceAtLeast(0)
    }

    private fun worldProductionsBonus(branch: ProductionBranch, worldProductions: Set<String>): Int {
        if (worldProductions.isEmpty()) return 0
        val matchCount = branch.requiredItems.count { it.targetItem.itemId in worldProductions }
        return matchCount * PRODUCTION_BONUS
    }

    private fun recipeThresholdBonus(
        branch: ProductionBranch,
        requiredAmount: Int,
        recipeThreshold: Int
    ): Int {
        if (requiredAmount < recipeThreshold) return 0
        return if (branch.source.sourceType.isRecipe()) RECIPE_THRESHOLD_BONUS else 0
    }

    private fun circularBlockLootPenalty(branch: ProductionBranch, hasRecipeAlternative: Boolean): Int {
        if (!hasRecipeAlternative) return 0
        return if (branch.source.sourceType == ResourceSource.SourceType.LootTypes.BLOCK) CIRCULAR_BLOCK_LOOT_PENALTY else 0
    }
}
