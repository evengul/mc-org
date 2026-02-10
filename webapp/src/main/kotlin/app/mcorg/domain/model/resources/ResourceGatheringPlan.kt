package app.mcorg.domain.model.resources

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
    val itemId: String,                     // What we're making
    val source: String?,                // How we get it (null for leaf items)
    val requirements: List<ProductionPath> = emptyList()  // What we need (each is a decision point)
) {

    /**
     * Get all unique item IDs in this path (for summary display)
     */
    fun getAllItemIds(): Set<String> {
        val items = mutableSetOf(itemId)
        requirements.forEach { items.addAll(it.getAllItemIds()) }
        return items
    }

    /**
     * Count total decision points (nodes with sources)
     */
    fun countDecisions(): Int {
        return (if (source != null) 1 else 0) + requirements.sumOf { it.countDecisions() }
    }

    /**
     * Check if path is complete (all leaf nodes reached)
     */
    fun isComplete(): Boolean {
        if (source == null) return true // Leaf node
        return requirements.isEmpty() || requirements.all { it.isComplete() }
    }

    /**
     * Select a source for a specific item in the path tree.
     * Returns a new path with the selection applied.
     */
    fun selectSourceForItem(targetItemId: String, sourceType: String): ProductionPath {
        // If this is the target item, update its source
        if (itemId == targetItemId) {
            return copy(source = sourceType)
        }

        // Check if target exists in any requirement - if so, recurse
        val hasMatch = requirements.any { it.containsItem(targetItemId) }
        if (hasMatch) {
            return copy(requirements = requirements.map { it.selectSourceForItem(targetItemId, sourceType) })
        }

        // Target doesn't exist yet - add as a new requirement
        return copy(requirements = requirements + ProductionPath(itemId = targetItemId, source = sourceType))
    }

    private fun containsItem(targetItemId: String): Boolean {
        if (itemId == targetItemId) return true
        return requirements.any { it.containsItem(targetItemId) }
    }
}
