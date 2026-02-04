package app.mcorg.domain.services

import app.mcorg.domain.model.resources.ItemNode
import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.SourceNode
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Query service for ItemSourceGraph providing advanced graph traversal and analysis operations.
 *
 * This service provides methods to:
 * - Find production chains (how to craft/obtain items)
 * - Detect cycles in the production graph
 * - Find leaf items (only obtainable via loot/mining)
 * - Find top-level items (not used in any recipes)
 * - Analyze graph connectivity
 */
class ItemSourceGraphQueries(private val graph: ItemSourceGraph) {

    private val logger = LoggerFactory.getLogger(ItemSourceGraphQueries::class.java)

    /**
     * Find all sources that can produce a specific item.
     * Convenience wrapper around graph.getSourcesForItem()
     *
     * @param itemId The item identifier
     * @return Set of all sources that produce this item
     */
    fun findAllSourcesForItem(itemId: String): Set<SourceNode> {
        return graph.getSourcesForItem(itemId)
    }

    /**
     * Find all items required for a specific source.
     *
     * @param source The source node
     * @return Set of all items required as inputs
     */
    fun findRequiredItemsForSource(source: SourceNode): Set<ItemNode> {
        return graph.getRequiredItems(source)
    }

    /**
     * Find the complete production chain for a target item.
     * Returns a tree structure showing all possible ways to obtain the item
     * and recursively all items needed for each method.
     *
     * @param targetItem The item to find production chains for
     * @param maxDepth Maximum recursion depth (prevents infinite loops)
     * @return ProductionTree with all production paths, or null if item not found
     */
    fun findProductionChain(targetItem: String, maxDepth: Int = 10): ProductionTree? {
        val itemNode = graph.getItemNode(targetItem) ?: return null
        val visited = mutableSetOf<ItemNode>()

        return buildProductionTree(itemNode, visited, maxDepth)
    }

    /**
     * Recursively build a production tree for an item.
     * Tracks visited items to detect cycles and prevent infinite recursion.
     */
    private fun buildProductionTree(
        item: ItemNode,
        visited: MutableSet<ItemNode>,
        depth: Int
    ): ProductionTree {
        // Base case: max depth reached or item already visited (cycle)
        if (depth <= 0 || item in visited) {
            return ProductionTree(item, emptyList())
        }

        visited.add(item)

        // Find all sources that produce this item
        val sources = graph.getSourcesForItem(item.itemId)

        // For each source, recursively find production chains for required items
        val branches = sources.map { source ->
            val requiredItems = graph.getRequiredItems(source)
            val requiredTrees = requiredItems.map { requiredItem ->
                buildProductionTree(requiredItem, visited, depth - 1)
            }
            ProductionBranch(source, requiredTrees)
        }

        return ProductionTree(item, branches)
    }

    /**
     * Find leaf items - items that have no recipe and can only be obtained through
     * loot, mining, or other non-crafting sources.
     *
     * An item is a leaf if it has no sources, or all its sources require no inputs.
     *
     * @return Set of items that are "base resources"
     */
    fun findLeafItems(): Set<ItemNode> {
        val leafItems = mutableSetOf<ItemNode>()

        for (item in graph.getAllItems()) {
            val sources = graph.getSourcesForItem(item.itemId)

            // Item is a leaf if it has no sources
            if (sources.isEmpty()) {
                leafItems.add(item)
                continue
            }

            // Item is a leaf if ALL its sources have no requirements
            val allSourcesNoRequirements = sources.all { source ->
                graph.getRequiredItems(source).isEmpty()
            }

            if (allSourcesNoRequirements) {
                leafItems.add(item)
            }
        }

        return leafItems
    }

    /**
     * Find top-level items - items that are not used as input for any source.
     * These are typically final products (tools, armor, etc.) that aren't crafted into anything else.
     *
     * @return Set of items that are never used as recipe inputs
     */
    fun findTopLevelItems(): Set<ItemNode> {
        val usedItems = mutableSetOf<ItemNode>()

        // Collect all items that are used as inputs
        for (source in graph.getAllSources()) {
            usedItems.addAll(graph.getRequiredItems(source))
        }

        // Top-level items are those not in the used set
        return graph.getAllItems().filter { it !in usedItems }.toSet()
    }

    /**
     * Detect cycles in the production graph.
     * A cycle exists when an item is part of its own production chain.
     *
     * Example: Crafting table needs planks, but you need a crafting table to make planks efficiently.
     *
     * @return List of cycles, where each cycle is a list of item IDs forming the cycle
     */
    fun detectCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<ItemNode>()
        val recursionStack = mutableSetOf<ItemNode>()
        val currentPath = mutableListOf<ItemNode>()

        for (item in graph.getAllItems()) {
            if (item !in visited) {
                detectCyclesRecursive(item, visited, recursionStack, currentPath, cycles)
            }
        }

        return cycles
    }

    /**
     * Recursive helper for cycle detection using DFS with recursion stack.
     */
    private fun detectCyclesRecursive(
        item: ItemNode,
        visited: MutableSet<ItemNode>,
        recursionStack: MutableSet<ItemNode>,
        currentPath: MutableList<ItemNode>,
        cycles: MutableList<List<String>>
    ) {
        visited.add(item)
        recursionStack.add(item)
        currentPath.add(item)

        // Get all sources for this item
        val sources = graph.getSourcesForItem(item.itemId)

        // For each source, check its required items
        for (source in sources) {
            val requiredItems = graph.getRequiredItems(source)

            for (requiredItem in requiredItems) {
                if (requiredItem !in visited) {
                    // Continue DFS
                    detectCyclesRecursive(requiredItem, visited, recursionStack, currentPath, cycles)
                } else if (requiredItem in recursionStack) {
                    // Found a cycle!
                    val cycleStartIndex = currentPath.indexOf(requiredItem)
                    val cycle = currentPath.subList(cycleStartIndex, currentPath.size).map { it.itemId }
                    cycles.add(cycle + requiredItem.itemId) // Close the cycle
                }
            }
        }

        currentPath.removeAt(currentPath.size - 1)
        recursionStack.remove(item)
    }

    /**
     * Find the shortest production path from a source item to a target item.
     * Uses BFS to find the shortest chain of crafting steps.
     *
     * @param fromItemId Starting item ID
     * @param toItemId Target item ID
     * @return List of item IDs representing the shortest path, or null if no path exists
     */
    fun findShortestPath(fromItemId: String, toItemId: String): List<String>? {
        val fromItem = graph.getItemNode(fromItemId) ?: return null
        val toItem = graph.getItemNode(toItemId) ?: return null

        if (fromItem == toItem) {
            return listOf(fromItemId)
        }

        // BFS to find shortest path
        val queue = ArrayDeque<ItemNode>()
        val visited = mutableSetOf<ItemNode>()
        val parent = mutableMapOf<ItemNode, ItemNode>()

        queue.add(fromItem)
        visited.add(fromItem)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            // Get all sources that use this item as input
            val sourcesUsingItem = graph.getAllSources().filter { source ->
                graph.getRequiredItems(source).contains(current)
            }

            // For each source, check what it produces
            for (source in sourcesUsingItem) {
                val producedItems = graph.getProducedItems(source)

                for (produced in producedItems) {
                    if (produced !in visited) {
                        visited.add(produced)
                        parent[produced] = current

                        if (produced == toItem) {
                            // Found the target! Reconstruct path
                            return reconstructPath(fromItem, toItem, parent)
                        }

                        queue.add(produced)
                    }
                }
            }
        }

        // No path found
        return null
    }

    /**
     * Reconstruct path from parent map (result of BFS).
     */
    private fun reconstructPath(
        from: ItemNode,
        to: ItemNode,
        parent: Map<ItemNode, ItemNode>
    ): List<String> {
        val path = mutableListOf<String>()
        var current: ItemNode? = to

        while (current != null) {
            path.add(0, current.itemId)
            current = parent[current]
        }

        return path
    }

    /**
     * Analyze the graph and return various statistics.
     * Useful for debugging and understanding the graph structure.
     *
     * @return Map containing analysis results
     */
    fun analyzeGraph(): Map<String, Any> {
        val stats = graph.getStatistics().toMutableMap<String, Any>()

        // Add query-based statistics
        stats["leafItemCount"] = findLeafItems().size
        stats["topLevelItemCount"] = findTopLevelItems().size
        stats["cycleCount"] = detectCycles().size

        // Find items with most sources
        val itemSourceCounts = graph.getAllItems().associate { item ->
            item.itemId to graph.getSourcesForItem(item.itemId).size
        }
        val maxSources = itemSourceCounts.maxByOrNull { it.value }
        if (maxSources != null) {
            stats["itemWithMostSources"] = maxSources.key
            stats["maxSourcesPerItem"] = maxSources.value
        }

        // Find sources with most requirements
        val sourceRequirementCounts = graph.getAllSources().associate { source ->
            source.filename to graph.getRequiredItems(source).size
        }
        val maxRequirements = sourceRequirementCounts.maxByOrNull { it.value }
        if (maxRequirements != null) {
            stats["sourceWithMostRequirements"] = maxRequirements.key
            stats["maxRequirementsPerSource"] = maxRequirements.value
        }

        return stats
    }
}

/**
 * Represents a tree of production paths for an item.
 * Shows all possible ways to obtain the item and what's needed for each method.
 */
@Serializable
data class ProductionTree(
    val targetItem: ItemNode,
    val sources: List<ProductionBranch>
) {
    fun getNBestBranches(n: Int): List<ProductionBranch> {
        return sources.sortedByDescending { it.getScore() }.take(n)
    }

    fun pruneRecursively(
        maxBranchesPerLevel: Int = 2,
        scorer: (ProductionBranch) -> Int = { it.getScore() }
    ) : ProductionTree {
        val rankedBranches = this.sources
            .map { branch -> branch to scorer(branch) }
            .sortedByDescending { it.second }
            .take(maxBranchesPerLevel)
            .map { it.first }

        // Recursively prune child branches
        val prunedBranches = rankedBranches.map { branch ->
            ProductionBranch(
                source = branch.source,
                requiredItems = branch.requiredItems.map { childTree ->
                    childTree.pruneRecursively(maxBranchesPerLevel, scorer)
                }
            )
        }

        return ProductionTree(targetItem, prunedBranches)
    }

    fun deduplicated(): ProductionTree {
        val seenItems = mutableMapOf<String, ProductionTree>()

        fun deduplicate(subtree: ProductionTree): ProductionTree {
            val itemId = subtree.targetItem.itemId

            // If we've already processed this item, return cached result
            if (itemId in seenItems) {
                return ProductionTree(subtree.targetItem, emptyList()) // Placeholder
            }

            seenItems[itemId] = subtree

            val deduplicatedBranches = subtree.sources.map { branch ->
                ProductionBranch(
                    branch.source,
                    branch.requiredItems.map { deduplicate(it) }
                )
            }

            return ProductionTree(subtree.targetItem, deduplicatedBranches)
        }

        return deduplicate(this)
    }

    fun getDepth(): Int {
        if (sources.isEmpty()) {
            return 0
        }
        return 1 + sources.maxOf { branch ->
            branch.requiredItems.maxOfOrNull { it.getDepth() } ?: 0
        }
    }
}

/**
 * Represents one branch of a production tree - a specific source and what it requires.
 */
@Serializable
data class ProductionBranch(
    val source: SourceNode,
    val requiredItems: List<ProductionTree>  // Recursive: each required item has its own production tree
) {
    fun getScore(): Int {
        return source.sourceType.score - requiredItems.size * 10 - (requiredItems.takeIf { it.isNotEmpty() }?.let { items -> items.maxOf { item -> item.getDepth() } } ?: 0)
    }

    fun getDepth(): Int {
        if (requiredItems.isEmpty()) {
            return 1
        }
        return 1 + requiredItems.maxOf { it.getDepth() }
    }
}

