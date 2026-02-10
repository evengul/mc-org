package app.mcorg.domain.model.resources

import kotlinx.serialization.Serializable

/**
 * Represents a team's selected path to obtain an item, persisted in the database.
 *
 * Example encoded path: "minecraft:blue_bed>crafting_shaped~minecraft:blue_wool>shearing|minecraft:oak_planks>crafting"
 * Format: itemId>sourceType~requiredItem1>sourceType|requiredItem2>sourceType
 */
data class ResourceGatheringPlan(
    val id: Int,
    val resourceGatheringId: Int,
    val selectedPath: ProductionPath,
    val confirmed: Boolean
)

/**
 * A concrete, user-selected production path (no branches, linear decisions)
 */
@Serializable
data class ProductionPath(
    val itemId: String,                     // What we're making
    val source: String?,                // How we get it (null for leaf items)
    val requirements: List<ProductionPath> = emptyList()  // What we need (each is a decision point)
) {

    /**
     * Encode to URL-safe string: "item>source>req1>source|req2>source"
     * Uses > for path navigation, | for sibling requirements, ~ for path depth separator
     */
    fun encode(): String {
        if (source == null) {
            // Leaf item, no source
            return itemId
        }

        val encodedRequirements = requirements.joinToString("|") { it.encode() }
        return if (encodedRequirements.isEmpty()) {
            "$itemId>$source"
        } else {
            "$itemId>$source~$encodedRequirements"
        }
    }

    companion object {
        /**
         * Decode from URL parameter
         * Format: "minecraft:blue_bed>crafting_shaped~minecraft:blue_wool>shearing|minecraft:oak_planks>crafting"
         */
        fun decode(encoded: String): ProductionPath? {
            if (encoded.isBlank()) return null

            return try {
                decodeInternal(encoded)
            } catch (e: Exception) {
                null // Invalid encoding
            }
        }

        private fun decodeInternal(encoded: String): ProductionPath {
            // Check if this is a leaf node (no source)
            if (!encoded.contains(">")) {
                return ProductionPath(itemId = encoded, source = null)
            }

            // Split on first ~ to separate item>source from requirements
            val parts = encoded.split("~", limit = 2)
            val itemAndSource = parts[0].split(">", limit = 2)

            val itemId = itemAndSource[0]
            val sourceType = itemAndSource.getOrNull(1)

            val requirements = if (parts.size > 1) {
                // Parse requirements (separated by |)
                parts[1].split("|").map { decodeInternal(it) }
            } else {
                emptyList()
            }

            return ProductionPath(itemId, sourceType, requirements)
        }
    }

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

