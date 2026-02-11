package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.services.ProductionTree

data class ResourceGatheringPlan(
    val id: Int,
    val resourceGatheringId: Int,
    val selectedPath: ProductionPath,
    val confirmed: Boolean
)

/**
 * A concrete, user-selected production path (no branches, linear decisions)
 */
data class ProductionPath(
    val item: MinecraftId,
    val source: SelectedSource? = null
) {

    /**
     * Get all unique item IDs in this path (for summary display)
     */
    fun getAllItemIds(): Set<String> {
        val items = mutableSetOf(item.id)
        source?.requirements?.forEach { items.addAll(it.getAllItemIds()) }
        return items
    }

    /**
     * Count total decision points (nodes with sources)
     */
    fun countDecisions(): Int {
        return (if (source != null) 1 else 0) + (source?.requirements?.sumOf { it.countDecisions() } ?: 0)
    }

    /**
     * Check if path is complete (all leaf nodes reached)
     */
    fun isComplete(): Boolean {
        if (source == null) return true // Leaf node
        return source.requirements.isEmpty() || source.requirements.all { it.isComplete() }
    }

    /**
     * Select a source for a specific item in the path tree.
     * Uses the ProductionTree to populate requirements of the selected source.
     * Returns a new path with the selection applied.
     */
    fun selectSourceForItem(targetItemId: String, sourceType: String, tree: ProductionTree): ProductionPath {
        if (item.id == targetItemId) {
            val matchingBranch = tree.sources.find { it.source.getKey() == sourceType }
                ?: tree.tagMembers.firstNotNullOfOrNull { member ->
                    member.sources.find { it.source.getKey() == sourceType }
                }
            val requirements = matchingBranch?.requiredItems?.map {
                ProductionPath(item = it.targetItem.item)
            } ?: emptyList()
            return copy(source = SelectedSource(sourceKey = sourceType, requirements = requirements))
        }

        val currentRequirements = source?.requirements ?: emptyList()

        val hasMatch = currentRequirements.any { it.containsItem(targetItemId) }
        if (hasMatch) {
            val subtree = tree.findSubtreeForItem(targetItemId)
            val updatedRequirements = currentRequirements.map {
                if (it.containsItem(targetItemId) && subtree != null) it.selectSourceForItem(targetItemId, sourceType, subtree)
                else it
            }
            return copy(source = source?.copy(requirements = updatedRequirements))
        }

        // Target doesn't exist yet - find subtree and add as new requirement
        val subtree = tree.findSubtreeForItem(targetItemId)
        if (subtree != null) {
            val matchingBranch = subtree.sources.find { it.source.getKey() == sourceType }
            val childReqs = matchingBranch?.requiredItems?.map {
                ProductionPath(item = it.targetItem.item)
            } ?: emptyList()
            val newReq = ProductionPath(item = subtree.targetItem.item, source = SelectedSource(sourceKey = sourceType, requirements = childReqs))
            return copy(source = source?.copy(requirements = currentRequirements + newReq))
        }

        return this
    }

    private fun containsItem(targetItemId: String): Boolean {
        if (item.id == targetItemId) return true
        return source?.requirements?.any { it.containsItem(targetItemId) } ?: false
    }
}

data class SelectedSource(
    val sourceKey: String,
    val requirements: List<ProductionPath> = emptyList()
)
