package app.mcorg.domain.model.resources

import kotlinx.serialization.Serializable
import java.util.Locale.getDefault

/**
 * Node representing a Minecraft item in the production graph.
 * Items can be both inputs (required) and outputs (produced) of various sources.
 */
@Serializable
data class ItemNode(val itemId: String) {
    override fun toString(): String = "Item($itemId)"
}

/**
 * Node representing a specific source for obtaining items.
 * A source can be a recipe (crafting, smelting, etc.) or a loot source (mining, chest, etc.).
 * Multiple sources can produce the same item (e.g., iron can be mined or smelted).
 *
 * Each ResourceSource (identified by type + filename) becomes a unique SourceNode.
 */
@Serializable
data class SourceNode(
    val sourceType: ResourceSource.SourceType,
    val filename: String
) {
    override fun toString(): String = "Source(${sourceType.id}:$filename)"

    fun getKey(): String {
        return "${sourceType.id}:$filename"
    }

    fun getName(): String {
        return if (sourceType.id.contains("entity")) {
            "Entity: ${getPrettyFilename()}"
        } else if (sourceType.id.contains("block")) {
            "Break Block: ${getPrettyFilename()}"
        } else {
            sourceType.name
        }
    }

    private fun getPrettyFilename(): String {
        return filename.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    }

    companion object {
        fun fromKey(key: String): SourceNode {
            val parts = key.split(":")
            require(parts.size >= 3) { "Invalid SourceNode key: $key" }
            val groupId = parts[0]
            val sourceTypeId = parts[1]
            val filename = parts.subList(2, parts.size).joinToString(":")
            val sourceType = ResourceSource.SourceType.of("$groupId:$sourceTypeId")
                ?: ResourceSource.SourceType.UNKNOWN
            return SourceNode(sourceType, filename)
        }
    }
}

/**
 * Represents an edge in the item source graph.
 * Edges connect items to sources (inputs) and sources to items (outputs).
 */
sealed class GraphEdge {
    /**
     * Edge from an item to a source, indicating the item is required for the source.
     * Example: "2 planks" -> "stick crafting recipe"
     */
    data class ItemToSource(val item: ItemNode, val source: SourceNode) : GraphEdge() {
        override fun toString(): String = "$item -> $source"
    }

    /**
     * Edge from a source to an item, indicating the source produces the item.
     * Example: "stick crafting recipe" -> "4 sticks"
     */
    data class SourceToItem(val source: SourceNode, val item: ItemNode) : GraphEdge() {
        override fun toString(): String = "$source -> $item"
    }
}

/**
 * Bipartite graph representing all item production chains in Minecraft.
 *
 * The graph has two types of nodes:
 * - ItemNodes: Represent Minecraft items (diamonds, planks, etc.)
 * - SourceNodes: Represent ways to obtain items (recipes, mining, loot, etc.)
 *
 * Edges connect:
 * - Items to Sources (item is required input for the source)
 * - Sources to Items (source produces the item as output)
 *
 * This structure elegantly handles:
 * - Multiple ways to obtain the same item (iron from mining, smelting, or chests)
 * - Complex production chains (diamond pickaxe needs diamonds and sticks, which need more items)
 * - Cycles (crafting table needs planks, but planks can be crafted with crafting table)
 *
 * The graph is immutable once built for thread-safe queries.
 */
@Serializable
class ItemSourceGraph private constructor(
    private val itemNodes: Map<String, ItemNode>,
    private val sourceNodes: Map<String, SourceNode>,
    private val itemToSourceEdges: Map<ItemNode, Set<SourceNode>>,
    private val sourceToItemEdges: Map<SourceNode, Set<ItemNode>>,
    private val sourceToRequiredItems: Map<SourceNode, Set<ItemNode>>
) {

    /**
     * Get all sources that can produce a specific item.
     * @param itemId The item identifier (e.g., "minecraft:diamond")
     * @return Set of all sources that produce this item, or empty set if none found
     */
    fun getSourcesForItem(itemId: String): Set<SourceNode> {
        val itemNode = itemNodes[itemId] ?: return emptySet()
        return sourceToItemEdges.entries
            .filter { it.value.contains(itemNode) }
            .map { it.key }
            .toSet()
    }

    /**
     * Get all items required as input for a specific source.
     * @param source The source node
     * @return Set of all items required, or empty set if the source has no requirements
     */
    fun getRequiredItems(source: SourceNode): Set<ItemNode> {
        return sourceToRequiredItems[source] ?: emptySet()
    }

    /**
     * Get all items produced by a specific source.
     * @param source The source node
     * @return Set of all items produced, or empty set if none
     */
    fun getProducedItems(source: SourceNode): Set<ItemNode> {
        return sourceToItemEdges[source] ?: emptySet()
    }

    /**
     * Get an item node by its ID.
     * @param itemId The item identifier
     * @return The ItemNode if it exists, null otherwise
     */
    fun getItemNode(itemId: String): ItemNode? {
        return itemNodes[itemId]
    }

    /**
     * Get a source node by its source type ID and filename.
     * @param sourceTypeId The source type identifier (e.g., "minecraft:crafting_shaped")
     * @param filename The filename of the ResourceSource
     * @return The SourceNode if it exists, null otherwise
     */
    fun getSourceNode(sourceTypeId: String, filename: String): SourceNode? {
        return sourceNodes["$sourceTypeId:$filename"]
    }

    /**
     * Get all item nodes in the graph.
     * @return Set of all item nodes
     */
    fun getAllItems(): Set<ItemNode> {
        return itemNodes.values.toSet()
    }

    /**
     * Get all source nodes in the graph.
     * @return Set of all source nodes
     */
    fun getAllSources(): Set<SourceNode> {
        return sourceNodes.values.toSet()
    }

    fun getSourceCount(): Int {
        return sourceNodes.size
    }

    fun getItemCount(): Int {
        return itemNodes.size
    }

    /**
     * Get statistics about the graph.
     * @return A map containing graph statistics (item count, source count, edge count)
     */
    fun getStatistics(): Map<String, Int> {
        val itemToSourceEdgeCount = itemToSourceEdges.values.sumOf { it.size }
        val sourceToItemEdgeCount = sourceToItemEdges.values.sumOf { it.size }

        return mapOf(
            "itemCount" to itemNodes.size,
            "sourceCount" to sourceNodes.size,
            "itemToSourceEdges" to itemToSourceEdgeCount,
            "sourceToItemEdges" to sourceToItemEdgeCount,
            "totalEdges" to (itemToSourceEdgeCount + sourceToItemEdgeCount)
        )
    }

    /**
     * Builder for constructing an ItemSourceGraph.
     * The builder is mutable during construction, but produces an immutable graph.
     */
    class Builder {
        private val itemNodes = mutableMapOf<String, ItemNode>()
        private val sourceNodes = mutableMapOf<String, SourceNode>()
        private val itemToSourceEdges = mutableMapOf<ItemNode, MutableSet<SourceNode>>()
        private val sourceToItemEdges = mutableMapOf<SourceNode, MutableSet<ItemNode>>()
        private val sourceToRequiredItems = mutableMapOf<SourceNode, MutableSet<ItemNode>>()

        /**
         * Add or get an item node.
         * If the item already exists, returns the existing node.
         * @param itemId The item identifier
         * @return The ItemNode (new or existing)
         */
        fun addItemNode(itemId: String): ItemNode {
            return itemNodes.getOrPut(itemId) { ItemNode(itemId) }
        }

        /**
         * Add or get a source node.
         * If a source with the same type ID and filename already exists, returns the existing node.
         * @param sourceType The source type
         * @param filename The filename of the ResourceSource
         * @return The SourceNode (new or existing)
         */
        fun addSourceNode(sourceType: ResourceSource.SourceType, filename: String): SourceNode {
            val key = "${sourceType.id}:$filename"
            return sourceNodes.getOrPut(key) { SourceNode(sourceType, filename) }
        }

        /**
         * Add an edge from an item to a source (item is required for source).
         * @param item The item node
         * @param source The source node
         */
        fun addItemToSourceEdge(item: ItemNode, source: SourceNode) {
            itemToSourceEdges.getOrPut(item) { mutableSetOf() }.add(source)
            sourceToRequiredItems.getOrPut(source) { mutableSetOf() }.add(item)
        }

        /**
         * Add an edge from a source to an item (source produces item).
         * @param source The source node
         * @param item The item node
         */
        fun addSourceToItemEdge(source: SourceNode, item: ItemNode) {
            sourceToItemEdges.getOrPut(source) { mutableSetOf() }.add(item)
        }

        /**
         * Build the immutable graph from the current builder state.
         * @return An immutable ItemSourceGraph
         */
        fun build(): ItemSourceGraph {
            // Create immutable copies of all collections
            val immutableItemNodes = itemNodes.toMap()
            val immutableSourceNodes = sourceNodes.toMap()
            val immutableItemToSourceEdges = itemToSourceEdges.mapValues { it.value.toSet() }
            val immutableSourceToItemEdges = sourceToItemEdges.mapValues { it.value.toSet() }
            val immutableSourceToRequiredItems = sourceToRequiredItems.mapValues { it.value.toSet() }

            return ItemSourceGraph(
                immutableItemNodes,
                immutableSourceNodes,
                immutableItemToSourceEdges,
                immutableSourceToItemEdges,
                immutableSourceToRequiredItems
            )
        }
    }

    companion object {
        /**
         * Create a new builder for constructing an ItemSourceGraph.
         * @return A new Builder instance
         */
        fun builder(): Builder = Builder()
    }
}

