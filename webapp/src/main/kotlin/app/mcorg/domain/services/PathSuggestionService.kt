package app.mcorg.domain.services

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.SelectedSource

/**
 * Deterministic auto-suggestion service that walks a ProductionTree
 * and produces a complete ProductionPath by greedily picking the
 * highest-scored branch at each level.
 */
object PathSuggestionService {

    /**
     * Suggest a complete production path for the given tree.
     *
     * Algorithm:
     * 1. Score all available sources for the root item
     * 2. Pick the highest-scored source
     * 3. For each required item in that source:
     *    - Tag with no sources: leave unresolved (user picks variant)
     *    - Leaf (no sources): leave as-is
     *    - Has sources: recurse
     * 4. Return the assembled ProductionPath
     *
     * @param tree the production tree (should be deduplicated first)
     * @param context scoring context (required amount, world productions, threshold)
     * @return a ProductionPath with sources selected where possible
     */
    fun suggestPath(tree: ProductionTree, context: SuggestionContext): ProductionPath {
        return suggestForNode(tree, context)
    }

    private fun suggestForNode(tree: ProductionTree, context: SuggestionContext): ProductionPath {
        // Tag node with no production sources: leave as leaf
        if (tree.targetItem.item is MinecraftTag && tree.sources.isEmpty()) {
            return ProductionPath(item = tree.targetItem.item)
        }

        // Tag node where all sources are leaves (no requirements):
        // The player can collect any member item, no need to specify which
        if (tree.targetItem.item is MinecraftTag && tree.sources.all { it.requiredItems.isEmpty() }) {
            return ProductionPath(item = tree.targetItem.item)
        }

        // Leaf node: no sources available
        if (tree.sources.isEmpty()) {
            return ProductionPath(item = tree.targetItem.item)
        }

        // Detect if any sibling branch is a recipe — if so, block loot is likely circular
        val hasRecipeAlternative = tree.sources.any { it.source.sourceType.isRecipe() }

        // Score all branches and pick the best
        val bestBranch = tree.sources.maxByOrNull { branch ->
            PathSuggestionScorer.score(branch, context, hasRecipeAlternative)
        } ?: return ProductionPath(item = tree.targetItem.item)

        // Recurse into required items
        val childPaths = bestBranch.requiredItems.map { childTree ->
            suggestForNode(childTree, context)
        }

        return ProductionPath(
            item = tree.targetItem.item,
            source = SelectedSource(
                sourceKey = bestBranch.source.getKey(),
                requirements = childPaths
            )
        )
    }
}
