package app.mcorg.engine.model

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import kotlinx.serialization.Serializable
import java.util.Locale.getDefault

/**
 * Node representing a Minecraft item in the production graph.
 * Items can be both inputs (required) and outputs (produced) of various sources.
 */
@Serializable
data class ItemNode(val item: MinecraftId) {
    val itemId: String get() = item.id
    override fun toString(): String = "Item(${item.id})"
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

    fun getMethodLabel(): String {
        return when (sourceType.id) {
            "minecraft:block" -> "Break Block"
            "minecraft:block_interact" -> "Interact"
            "minecraft:entity" -> "Entity Drop"
            "minecraft:entity_interact" -> "Interact"
            "minecraft:barter" -> "Bartering"
            "minecraft:chest" -> "Chest Loot"
            "minecraft:gift" -> "Gift Drop"
            "minecraft:archaeology" -> "Archaeology"
            "minecraft:equipment" -> "Equipment"
            else -> sourceType.name
        }
    }

    /**
     * The block a block loot table is for (`blocks/soul_campfire.json` → `minecraft:soul_campfire`),
     * or null for any other source. Breaking that block is this source's real input, which the
     * graph's edges do not show; the cost model and [ItemSourceGraph.getItemsDependingOn] both
     * read it here so they cannot disagree about which block it is.
     */
    fun lootedBlockId(): String? =
        if (sourceType == ResourceSource.SourceType.LootTypes.BLOCK) {
            "minecraft:" + filename.substringAfterLast('/').substringBeforeLast('.')
        } else {
            null
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
    private val itemNodes: Map<MinecraftId, ItemNode>,
    private val sourceNodes: Map<String, SourceNode>,
    private val itemToSourceEdges: Map<ItemNode, Set<SourceNode>>,
    private val sourceToItemEdges: Map<SourceNode, Set<ItemNode>>,
    private val sourceToRequiredItems: Map<SourceNode, Set<ItemNode>>,
    private val sourceInputQuantities: Map<SourceNode, Map<ItemNode, Int>> = emptyMap(),
    private val sourceOutputQuantities: Map<SourceNode, Map<ItemNode, Int>> = emptyMap(),
    private val sourceOutputExpectedYields: Map<SourceNode, Map<ItemNode, Double>> = emptyMap()
) {

    /**
     * Producer reverse-index: item -> the sources that produce it. Built lazily
     * on first lookup (O(edges) once), making [getSourcesForItem] O(1) — it sits
     * on the planner's hottest path. Delegated properties are not serialized,
     * so the wire format is unchanged.
     */
    private val producersByItem: Map<ItemNode, Set<SourceNode>> by lazy {
        val index = HashMap<ItemNode, MutableSet<SourceNode>>(itemNodes.size)
        for ((source, items) in sourceToItemEdges) {
            for (item in items) {
                index.getOrPut(item) { linkedSetOf() }.add(source)
            }
        }
        index
    }

    /**
     * Get all sources that can produce a specific item.
     * @param item The MinecraftId (e.g., Item("minecraft:diamond", "Diamond"))
     * @return Set of all sources that produce this item, or empty set if none found
     */
    fun getSourcesForItem(item: MinecraftId): Set<SourceNode> {
        val itemNode = itemNodes[item] ?: return emptySet()
        return producersByItem[itemNode] ?: emptySet()
    }

    /**
     * Dependents reverse-index: item id -> the ids of items whose acquisition can use it one link
     * up. Built lazily like [producersByItem], and not serialized either.
     *
     * Three kinds of link, and they are the three ways one item's price reaches another in
     * `UnitCostModel`:
     *
     * - **an ingredient**, to everything its source produces;
     * - **a tag member**, to the tag: a tag costs its cheapest member;
     * - **a block**, to what its block loot table drops. The loot source requires nothing in the
     *   graph, but breaking a block you had to build costs the block (`manufacturedBlockCost`),
     *   so the block is an input the edges do not show. Every block loot source gets the link,
     *   not only the built-only ones that rule prices that way — a superset costs nothing here.
     */
    private val dependentsById: Map<String, Set<String>> by lazy {
        val index = HashMap<String, MutableSet<String>>(itemNodes.size)
        fun link(from: String, to: String) {
            index.getOrPut(from) { HashSet() }.add(to)
        }
        for ((source, produced) in sourceToItemEdges) {
            for (required in sourceToRequiredItems[source].orEmpty()) {
                for (item in produced) link(required.itemId, item.itemId)
            }
            source.lootedBlockId()?.let { blockId ->
                for (item in produced) link(blockId, item.itemId)
            }
        }
        for (node in itemNodes.values) {
            val tag = node.item as? MinecraftTag ?: continue
            for (member in tag.content) link(member.id, tag.id)
        }
        index
    }

    /**
     * Every item whose acquisition can pass through any of [itemIds], those items included.
     *
     * The upward closure of the production graph: an item is in it when some chain that obtains
     * it, through any source, tag member or broken block, uses one of [itemIds] somewhere below.
     * Equivalently, the items whose price can change when the price of one of [itemIds] does —
     * supplying an item, making it cheaper, or removing it as an option reaches no other item.
     *
     * Ids are strings so a tag (`#minecraft:planks`) and an item are told apart by their prefix.
     * An id the graph does not know is returned on its own.
     */
    fun getItemsDependingOn(itemIds: Set<String>): Set<String> {
        val reached = HashSet(itemIds)
        val queue = ArrayDeque(itemIds)
        while (queue.isNotEmpty()) {
            for (next in dependentsById[queue.removeFirst()].orEmpty()) {
                if (reached.add(next)) queue.addLast(next)
            }
        }
        return reached
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
     * Get the quantity of a specific item required by a source.
     * @return The count, or 1 if unknown
     */
    fun getRequiredQuantity(source: SourceNode, item: ItemNode): Int {
        return sourceInputQuantities[source]?.get(item) ?: 1
    }

    /**
     * Get the quantity of a specific item produced by a source.
     * @return The count, or 1 if unknown
     */
    fun getProducedQuantity(source: SourceNode, item: ItemNode): Int {
        return sourceOutputQuantities[source]?.get(item) ?: 1
    }

    /**
     * Average amount of the item one attempt at this source produces, from
     * loot-table rolls/weights/count data. Null when unknown or not applicable
     * (recipes use [getProducedQuantity]).
     */
    fun getExpectedYield(source: SourceNode, item: ItemNode): Double? {
        return sourceOutputExpectedYields[source]?.get(item)
    }

    /**
     * Get all required quantities for a source as a map of itemId -> count.
     */
    fun getRequiredQuantities(source: SourceNode): Map<String, Int> {
        return sourceInputQuantities[source]?.map { (item, qty) -> item.itemId to qty }?.toMap() ?: emptyMap()
    }

    /**
     * Get an item node by its MinecraftId.
     * @param item The MinecraftId
     * @return The ItemNode if it exists, null otherwise
     */
    fun getItemNode(item: MinecraftId): ItemNode? {
        return itemNodes[item]
    }

    /**
     * Find all item nodes matching a string ID.
     * May return multiple nodes if both an Item and MinecraftTag share the same ID.
     */
    fun getItemNodesByStringId(itemId: String): Set<ItemNode> {
        return itemNodes.values.filter { it.itemId == itemId }.toSet()
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
        private val itemNodes = mutableMapOf<MinecraftId, ItemNode>()
        private val sourceNodes = mutableMapOf<String, SourceNode>()
        private val itemToSourceEdges = mutableMapOf<ItemNode, MutableSet<SourceNode>>()
        private val sourceToItemEdges = mutableMapOf<SourceNode, MutableSet<ItemNode>>()
        private val sourceToRequiredItems = mutableMapOf<SourceNode, MutableSet<ItemNode>>()
        private val sourceInputQuantities = mutableMapOf<SourceNode, MutableMap<ItemNode, Int>>()
        private val sourceOutputQuantities = mutableMapOf<SourceNode, MutableMap<ItemNode, Int>>()
        private val sourceOutputExpectedYields = mutableMapOf<SourceNode, MutableMap<ItemNode, Double>>()

        /**
         * Add or get an item node.
         * If the item already exists, returns the existing node.
         * When adding a MinecraftTag, also creates nodes for each member item.
         * @param item The MinecraftId (Item or MinecraftTag)
         * @return The ItemNode (new or existing)
         */
        fun addItemNode(item: MinecraftId): ItemNode {
            val node = itemNodes.getOrPut(item) { ItemNode(item) }
            if (item is MinecraftTag) {
                for (member in item.content) {
                    itemNodes.getOrPut(member) { ItemNode(member) }
                }
            }
            return node
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
         * @param quantity The quantity required (null means unknown, defaults to 1)
         */
        fun addItemToSourceEdge(item: ItemNode, source: SourceNode, quantity: Int? = null) {
            itemToSourceEdges.getOrPut(item) { mutableSetOf() }.add(source)
            sourceToRequiredItems.getOrPut(source) { mutableSetOf() }.add(item)
            if (quantity != null && quantity > 0) {
                sourceInputQuantities.getOrPut(source) { mutableMapOf() }
                    .merge(item, quantity) { existing, new -> existing + new }
            }
        }

        /**
         * Add an edge from a source to an item (source produces item).
         * @param source The source node
         * @param item The item node
         * @param quantity The quantity produced (null means unknown, defaults to 1)
         * @param expectedYield Average amount per attempt from loot-table data (null when unknown)
         */
        fun addSourceToItemEdge(source: SourceNode, item: ItemNode, quantity: Int? = null, expectedYield: Double? = null) {
            sourceToItemEdges.getOrPut(source) { mutableSetOf() }.add(item)
            if (quantity != null && quantity > 0) {
                sourceOutputQuantities.getOrPut(source) { mutableMapOf() }
                    .merge(item, quantity) { existing, new -> existing + new }
            }
            if (expectedYield != null && expectedYield > 0) {
                sourceOutputExpectedYields.getOrPut(source) { mutableMapOf() }
                    .merge(item, expectedYield) { existing, new -> existing + new }
            }
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
            val immutableSourceInputQuantities = sourceInputQuantities.mapValues { it.value.toMap() }
            val immutableSourceOutputQuantities = sourceOutputQuantities.mapValues { it.value.toMap() }
            val immutableSourceOutputExpectedYields = sourceOutputExpectedYields.mapValues { it.value.toMap() }

            return ItemSourceGraph(
                immutableItemNodes,
                immutableSourceNodes,
                immutableItemToSourceEdges,
                immutableSourceToItemEdges,
                immutableSourceToRequiredItems,
                immutableSourceInputQuantities,
                immutableSourceOutputQuantities,
                immutableSourceOutputExpectedYields
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

